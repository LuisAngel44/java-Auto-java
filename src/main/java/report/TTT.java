package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTT {

    // --- CONFIGURACIÓN DE JIRA ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // IDs DE CAMPOS PERSONALIZADOS
    static final String FIELD_DESC_INCIDENTE = "customfield_10180"; 
    static final String FIELD_SOLUCION = "customfield_10089";
    static final String FIELD_TICKET_ID = "customfield_10320";
    static final String FIELD_ORGANIZATION = "customfield_10002";
    static final String FIELD_CATEGORIA = "customfield_10394";
    static final String FIELD_CAUSA_RAIZ = "customfield_10135";
    static final String FIELD_TIPO_INCIDENCIA = "customfield_10469";
    static final String FIELD_TIEMPO_SOLUCION = "customfield_10177";
    static final String FIELD_FECHA_GEN = "customfield_10321";
    static final String FIELD_FECHA_SOL = "customfield_10322"; 
    static final String FIELD_TIEMPO_NODISP = "customfield_10178";

    // ÍNDICES DE COLUMNAS EXCEL
    static final int COL_RESUMEN = 0, COL_DESC = 1, COL_COLEGIO = 2, COL_DISPOSITIVO = 3;
    static final int COL_CONTACTO_NOM = 4, COL_CONTACTO_CEL = 5, COL_DEP = 6, COL_PROV = 7;
    static final int COL_DIST = 8, COL_DIR = 9, COL_FECHA_GEN = 10, COL_FECHA_SOL = 11;
    static final int COL_NOMBRE_IE = 12, COL_COD_MODULAR = 13, COL_COD_LOCAL = 14;
    static final int COL_MEDIO_TRANS = 16, COL_TIPO_INC = 17, COL_TIEMPO_NODISP = 18;
    static final int COL_TIEMPO_SOLUCION = 19, COL_ITEM = 20, COL_SOLUCION = 21;       
    static final int COL_RUTAS_IMG = 22, COL_AREA = 23, COL_CAUSA_RAIZ = 24;     
    static final int COL_CAT_SERVICIO = 25, COL_OUTPUT_ID = 26;      

    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty() || jiraUrl.isEmpty()) {
            System.err.println("❌ ERROR: Credenciales faltantes en .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        System.out.println(">>> 🚀 INICIANDO CARGA (API v2 - Fix Imágenes Wiki Markup) <<<");

        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int exitosos = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 
                String resumen = getValRobust(row, COL_RESUMEN, formatter); 
                if (resumen.isEmpty()) break; 

                System.out.println("\n🔄 Procesando: " + resumen);

                // 1️⃣ CREAR TICKET BÁSICO
                String issueKey = crearTicketBasico(resumen, row, formatter);

                if (issueKey != null) {
                    System.out.println("   ✅ 1. Ticket Creado: " + issueKey);

                    // 2️⃣ SUBIR IMÁGENES PRIMERO (Capturamos los NOMBRES para el Wiki Markup)
                    String rutas = getValRobust(row, COL_RUTAS_IMG, formatter);
                    List<String> nombresImagenes = new ArrayList<>();
                    if (!rutas.isEmpty()) {
                        nombresImagenes = subirImagenesYObtenerNombres(issueKey, rutas);
                    }

                    // 3️⃣ ACTUALIZACIÓN GENERAL (Usando API V2 para que acepte las imágenes)
                    ObjectNode putPayload = mapper.createObjectNode();
                    prepararCamposActualizacionV2(putPayload.putObject("fields"), row, issueKey, nombresImagenes, formatter);
                    enviarPutV2(issueKey, putPayload, "Actualización General (Campos e Imágenes)");

                    // 4️⃣ TRANSICIÓN Y POST-ACTUALIZACIÓN DE FECHAS
                    String fechaSol = getValRobust(row, COL_FECHA_SOL, formatter);
                    String textoSolucion = getValRobust(row, COL_SOLUCION, formatter);
                    
                    if (!fechaSol.isEmpty() && (!textoSolucion.isEmpty() || !nombresImagenes.isEmpty())) {
                        System.out.println("   🔄 Cambiando a 'Resuelta'...");
                        cambiarEstadoTicket(issueKey, "Resuelta");

                        // SEGUNDO PUT (Forzar Fecha y Tiempo Solución después de resolver)
                        ObjectNode finalPayload = mapper.createObjectNode();
                        ObjectNode fFields = finalPayload.putObject("fields");
                        fFields.put(FIELD_FECHA_SOL, formatearFechaRobusta(fechaSol));
                        
                        String tSol = getValRobust(row, COL_TIEMPO_SOLUCION, formatter);
                        if (!tSol.isEmpty()) fFields.put(FIELD_TIEMPO_SOLUCION, tSol);
                        
                        enviarPutV2(issueKey, finalPayload, "Post-Resolución (Fechas forzadas)");
                    }

                    row.createCell(COL_OUTPUT_ID).setCellValue(issueKey); 
                    exitosos++;
                }
                Thread.sleep(600); 
            }

            try (FileOutputStream fileOut = new FileOutputStream("resultado_carga_final.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 FINALIZADO. Procesados: " + exitosos);
            }
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
    }

    private static String crearTicketBasico(String resumen, Row row, DataFormatter fmt) {
        try {
            String itemNum = extraerSoloNumeros(getValRobust(row, COL_ITEM, fmt));
            String[] config = obtenerConfiguracionItem(itemNum); 
            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", SERVICE_DESK_ID);
            payload.put("requestTypeId", config[0]); 
            
            ObjectNode values = payload.putObject("requestFieldValues");
            values.put("summary", resumen.length() > 250 ? resumen.substring(0, 245) + "..." : resumen);
            putIfNotEmpty(values, "customfield_10090", getValRobust(row, COL_CONTACTO_NOM, fmt));
            putIfNotEmpty(values, "customfield_10091", getValRobust(row, COL_CONTACTO_CEL, fmt));
            values.putObject("customfield_10176").put("value", "Incidente");
            
            if (!config[2].isEmpty()) values.putArray(FIELD_ORGANIZATION).add(config[2]); 
            
            String colId = getValRobust(row, COL_COLEGIO, fmt);
            String dispId = getValRobust(row, COL_DISPOSITIVO, fmt);
            if (!colId.isEmpty()) agregarActivo(values, "customfield_10170", WORKSPACE_ID, colId);
            if (!dispId.isEmpty() && !config[1].isEmpty()) agregarActivo(values, config[1], WORKSPACE_ID, dispId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("X-ExperimentalApi", "opt-in")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return mapper.readTree(response.body()).get("issueKey").asText();
            } else {
                System.err.println("   ❌ Error creando ticket base: " + response.body());
                return null;
            }
        } catch (Exception e) { 
            return null; 
        }
    }

    // AHORA PREPARA LOS TEXTOS EN FORMATO WIKI EN LUGAR DE ADF
    private static void prepararCamposActualizacionV2(ObjectNode fields, Row row, String issueKey, List<String> nombresImg, DataFormatter fmt) {
        String desc = getValRobust(row, COL_DESC, fmt);
        String sol = getValRobust(row, COL_SOLUCION, fmt);
        
        if (!desc.isEmpty()) fields.put(FIELD_DESC_INCIDENTE, desc); 
        
        // Construimos el campo Solución con el Wiki Markup
        if (!sol.isEmpty() || !nombresImg.isEmpty()) {
            StringBuilder solucionWiki = new StringBuilder();
            for (String img : nombresImg) {
                solucionWiki.append("!").append(img).append("|width=1000!\n\n");
            }
            solucionWiki.append(sol);
            fields.put(FIELD_SOLUCION, solucionWiki.toString());
        }

        fields.put(FIELD_TICKET_ID, issueKey);
        String tipoInc = getValRobust(row, COL_TIPO_INC, fmt);
        if (!tipoInc.isEmpty()) fields.put(FIELD_TIPO_INCIDENCIA, tipoInc.toUpperCase());

        String cat = getValRobust(row, COL_CAT_SERVICIO, fmt);
        String causa = getValRobust(row, COL_CAUSA_RAIZ, fmt);
        if (!cat.isEmpty()) fields.putObject(FIELD_CATEGORIA).put("value", cat.trim());
        if (!causa.isEmpty()) fields.putObject(FIELD_CAUSA_RAIZ).put("value", causa.trim());

        String fGen = getValRobust(row, COL_FECHA_GEN, fmt);
        if (!fGen.isEmpty()) {
            String fGenFormateada = formatearFechaRobusta(fGen);
            if (fGenFormateada != null) fields.put(FIELD_FECHA_GEN, fGenFormateada);
        }
        String tiempoNoDisp = getValRobust(row, COL_TIEMPO_NODISP, fmt);
        if (!tiempoNoDisp.isEmpty()) {
            fields.put(FIELD_TIEMPO_NODISP, tiempoNoDisp);
        }
        putIfNotEmpty(fields, "customfield_10355", getValRobust(row, COL_DEP, fmt));
        putIfNotEmpty(fields, "customfield_10356", getValRobust(row, COL_PROV, fmt));
        putIfNotEmpty(fields, "customfield_10357", getValRobust(row, COL_DIST, fmt));
        putIfNotEmpty(fields, "customfield_10358", getValRobust(row, COL_DIR, fmt));
        putIfNotEmpty(fields, "customfield_10359", getValRobust(row, COL_NOMBRE_IE, fmt));
        putIfNotEmpty(fields, "customfield_10169", getValRobust(row, COL_COD_MODULAR, fmt));
        putIfNotEmpty(fields, "customfield_10168", getValRobust(row, COL_COD_LOCAL, fmt));
        putIfNotEmpty(fields, "customfield_10361", getValRobust(row, COL_MEDIO_TRANS, fmt));
        
        fields.putObject("customfield_10286").put("value", "Creado por alertas");
        fields.putObject("customfield_10471").put("value", "Cliente");
        fields.putObject("customfield_10504").put("value", getValRobust(row, COL_AREA, fmt).equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
    }

    // DEVUELVE LOS NOMBRES DE ARCHIVO PARA EL WIKI MARKUP (Igual que en TTUP)
    private static List<String> subirImagenesYObtenerNombres(String key, String rutas) {
        List<String> nombres = new ArrayList<>();
        for (String r : rutas.replace(";", ",").split(",")) {
            File f = new File(r.trim());
            if (!f.exists()) continue;
            try {
                String b = "---" + UUID.randomUUID();
                HttpRequest req = HttpRequest.newBuilder().uri(URI.create(jiraUrl + "/rest/api/3/issue/" + key + "/attachments"))
                        .header("Authorization", "Basic " + encodedAuth).header("X-Atlassian-Token", "no-check")
                        .header("Content-Type", "multipart/form-data; boundary=" + b)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(createMultipartBody(f, b))).build();
                
                HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    nombres.add(f.getName());
                    System.out.println("   📸 Imagen subida exitosamente: " + f.getName());
                } else {
                    System.err.println("   ❌ Error subiendo imagen: " + response.body());
                }
            } catch (Exception e) { 
                System.err.println("   ❌ Excepción subiendo imagen: " + e.getMessage());
            }
        }
        return nombres;
    }

    // USAMOS LA API V2 PARA LAS ACTUALIZACIONES
    private static boolean enviarPutV2(String key, ObjectNode p, String log) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(jiraUrl + "/rest/api/2/issue/" + key)) // API V2 AQUÍ
                    .header("Authorization", "Basic " + encodedAuth).header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(p))).build();
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 204) {
                System.out.println("   ✅ 2. " + log + " OK");
                return true;
            } else {
                System.err.println("   ⚠️ Fallo en " + log + ": " + response.body());
                return false;
            }
        } catch (Exception e) { return false; }
    }

    private static void cambiarEstadoTicket(String issueKey, String estadoDestino) {
        try {
            HttpRequest getReq = HttpRequest.newBuilder().uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth).GET().build();
            JsonNode transitions = mapper.readTree(client.send(getReq, HttpResponse.BodyHandlers.ofString()).body()).path("transitions");
            String tId = null;
            for (JsonNode t : transitions) {
                if (t.path("to").path("name").asText().equalsIgnoreCase(estadoDestino)) {
                    tId = t.path("id").asText();
                    break;
                }
            }
            if (tId != null) {
                ObjectNode p = mapper.createObjectNode();
                p.putObject("transition").put("id", tId);
                HttpRequest postReq = HttpRequest.newBuilder().uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                        .header("Authorization", "Basic " + encodedAuth).header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(p))).build();
                client.send(postReq, HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception e) { }
    }

    private static byte[] createMultipartBody(File f, String b) throws Exception {
        byte[] h = ("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + f.getName() + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] d = Files.readAllBytes(f.toPath());
        byte[] t = ("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[h.length + d.length + t.length];
        System.arraycopy(h, 0, res, 0, h.length);
        System.arraycopy(d, 0, res, h.length, d.length);
        System.arraycopy(t, 0, res, h.length + d.length, t.length);
        return res;
    }

    private static void agregarActivo(ObjectNode v, String fId, String wsId, String objId) {
        ObjectNode a = v.putArray(fId).addObject();
        a.put("workspaceId", wsId);
        a.put("id", wsId + ":" + objId);
        a.put("objectId", objId);
    }

    private static String extraerSoloNumeros(String i) {
        if (i == null) return "";
        Matcher m = Pattern.compile("\\d+").matcher(i);
        return m.find() ? m.group() : "";
    }

    private static String[] obtenerConfiguracionItem(String n) {
        switch (n) {
            case "1": return new String[]{"126", "customfield_10250", "2"};
            case "2": return new String[]{"127", "customfield_10251", "1"};
            case "3": return new String[]{"128", "customfield_10252", "3"};
            case "4": return new String[]{"129", "customfield_10253", "4"};
            default:  return new String[]{"129", "customfield_10253", ""};
        }
    }

    private static String getValRobust(Row row, int index, DataFormatter fmt) {
        Cell c = row.getCell(index);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(c.getDateCellValue());
        }
        return fmt.formatCellValue(c).trim();
    }

    private static String formatearFechaRobusta(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.contains("1900")) return null;
        try {
            if (raw.contains("T") && raw.contains("-0500")) return raw; 
            String limpio = raw.trim().replaceAll("\\s+", " "); 
            String fechaPart = limpio, horaPart = "00:00:00";
            if (limpio.contains(" ")) {
                String[] partes = limpio.split(" ");
                fechaPart = partes[0];
                if (partes.length > 1) horaPart = partes[1];
            }
            String[] dmy = fechaPart.split("[/\\-]");
            if (dmy.length == 3) {
                String dia = dmy[0].length() == 4 ? dmy[2] : dmy[0];
                String mes = dmy[1];
                String anio = dmy[0].length() == 4 ? dmy[0] : dmy[2];
                dia = (dia.length() == 1) ? "0" + dia : dia;
                mes = (mes.length() == 1) ? "0" + mes : mes;
                anio = (anio.length() == 2) ? "20" + anio : anio;
                fechaPart = anio + "-" + mes + "-" + dia;
            }
            String[] hms = horaPart.split(":");
            String hh = (hms.length > 0 && hms[0].length() > 0) ? hms[0] : "00";
            String mm = (hms.length > 1 && hms[1].length() > 0) ? hms[1] : "00";
            String ss = (hms.length > 2 && hms[2].length() > 0) ? hms[2] : "00";
            hh = (hh.length() == 1) ? "0" + hh : hh;
            mm = (mm.length() == 1) ? "0" + mm : mm;
            ss = (ss.length() == 1) ? "0" + ss : ss;
            return fechaPart + "T" + hh + ":" + mm + ":" + ss + ".000-0500";
        } catch (Exception e) { return null; }
    }

    private static void putIfNotEmpty(ObjectNode f, String k, String v) {
        if (v != null && !v.isEmpty()) f.put(k, v);
    }
}