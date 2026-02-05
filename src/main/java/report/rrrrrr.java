package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class rrrrrr{

    // --- CONFIGURACIÓN ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // 🚨 IDs CONFIRMADOS
    static final String FIELD_DESC_INCIDENTE = "customfield_10180"; 
    static final String FIELD_SOLUCION = "customfield_10089";
    static final String FIELD_TICKET_ID = "customfield_10320";
    
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ ERROR: Credenciales faltantes en .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🚀 INICIANDO CARGA v15 (CORRECCIÓN FINAL: ID 10180 + TEXTO) <<<");
            
            int exitosos = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 

                String resumen = getCellValue(row, 0); 
                if (resumen.isEmpty()) break;

                // --- LECTURA ---
                String descAlarma = getCellValue(row, 1);    
                String idColegio = getCellValue(row, 2);     
                String idDispositivo = getCellValue(row, 3); 
                String contactoNom = getCellValue(row, 4);
                String contactoCel = getCellValue(row, 5);
                String dep = getCellValue(row, 6);
                String prov = getCellValue(row, 7);
                String dist = getCellValue(row, 8);
                String direccion = getCellValue(row, 9);
                String fechaGen = getCellValue(row, 10);
                String fechaSol = getCellValue(row, 11);
                String nombreIE = getCellValue(row, 12);
                String codModular = getCellValue(row, 13);
                String codLocal = getCellValue(row, 14);
                String medioTrans = getCellValue(row, 16);
                String tipoIncidencia = getCellValue(row, 17);
                String tiempoNoDisp = getCellValue(row, 18);
                String itemNum = getCellValue(row, 19); 
                String textoSolucion = getCellValue(row, 20); 
                String rutasImagenes = getCellValue(row, 21); 
                String areaExcel = getCellValue(row, 22);
                String causaRaiz = getCellValue(row, 23);     
                String catServicio = getCellValue(row, 24);   

                System.out.println("\n------------------------------------------------");
                System.out.println("🔄 Procesando: " + (resumen.length() > 40 ? resumen.substring(0, 40) + "..." : resumen));

                // 1️⃣ CREAR TICKET
                String issueKey = crearTicketBasico(resumen, contactoNom, contactoCel, idColegio, idDispositivo, itemNum, areaExcel, catServicio);

                if (issueKey != null) {
                    System.out.println("   ✅ 1. Creado: " + issueKey);

                    // 2️⃣ DESCRIPCIÓN DEL INCIDENTE (ID 10180 - FORMATO TEXTO) 🚨
                    if (!descAlarma.isEmpty()) {
                        String descLimpia = descAlarma.replace("\\n", "\n").replace(" \n ", "\n").trim();
                        // ⚠️ CAMBIO CLAVE: Usamos actualizarCampoTexto (String), NO ADF
                        boolean descOk = actualizarCampoTexto(issueKey, FIELD_DESC_INCIDENTE, descLimpia, "Desc. Incidente");
                        
                        // Plan de respaldo: Si falla el campo, ponerlo como comentario
                        if (!descOk) {
                            agregarComentario(issueKey, "📋 DETALLE:\n" + descLimpia);
                        }
                    }

                    // 3️⃣ NÚMERO DE TICKET
                    actualizarCampoTexto(issueKey, FIELD_TICKET_ID, issueKey, "Número Ticket");

                    // 4️⃣ DROPDOWNS
                    if (!catServicio.isEmpty()) actualizarDropdown(issueKey, "customfield_10394", catServicio, "Categoría");
                    if (!causaRaiz.isEmpty()) actualizarDropdown(issueKey, "customfield_10135", causaRaiz, "Causa Raíz");
                    
                    // 5️⃣ SOLUCIÓN (Texto plano)
                    if (!textoSolucion.isEmpty()) {
                        actualizarCampoTexto(issueKey, FIELD_SOLUCION, textoSolucion, "Solución");
                    }
                    
                    if (!tipoIncidencia.isEmpty()) actualizarCampoTexto(issueKey, "customfield_10469", tipoIncidencia.toUpperCase(), "Tipo Incidencia");

                    // 6️⃣ TIEMPOS Y FECHAS
                    actualizarTiempos(issueKey, fechaGen, fechaSol, tiempoNoDisp);

                    // 7️⃣ DATOS UBICACIÓN
                    actualizarUbicacion(issueKey, dep, prov, dist, direccion, nombreIE, codModular, codLocal, medioTrans);

                    // 8️⃣ IMÁGENES
                    if (!rutasImagenes.isEmpty()) subirImagenes(issueKey, rutasImagenes);

                    row.createCell(25).setCellValue(issueKey); 
                    exitosos++;
                    System.out.println("   ✨ TICKET COMPLETADO");
                }
                
                Thread.sleep(1000); 
            }

            try (FileOutputStream fileOut = new FileOutputStream("resultado_carga_final.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 FIN. Exitosos: " + exitosos);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // =========================================================================
    // CREACIÓN BÁSICA
    // =========================================================================
    private static String crearTicketBasico(String resumen, String nombre, String cel, String colId, String dispId, String itemNum, String area, String cat) {
        try {
            String[] config = obtenerConfiguracionItem(itemNum); 
            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", SERVICE_DESK_ID);
            payload.put("requestTypeId", config[0]); 
            
            ObjectNode values = payload.putObject("requestFieldValues");
            values.put("summary", resumen.length() > 250 ? resumen.substring(0, 245) + "..." : resumen);
            values.put("customfield_10090", nombre); 
            values.put("customfield_10091", cel);    
            values.putObject("customfield_10176").put("value", "Incidente");
            if (!itemNum.isEmpty()) values.putArray("customfield_10002").add(itemNum);
            values.putObject("customfield_10504").put("value", area.equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
            values.putObject("customfield_10394").put("value", cat.isEmpty() ? "Servicio de acceso a Internet" : cat);

            if (!colId.isEmpty()) agregarActivo(values, "customfield_10170", WORKSPACE_ID, colId);
            if (!dispId.isEmpty()) agregarActivo(values, config[1], WORKSPACE_ID, dispId);

            return enviarPost(payload);
        } catch (Exception e) { return null; }
    }

    // =========================================================================
    // ACTUALIZACIÓN DE TEXTO SIMPLE (LA CLAVE DE v15)
    // =========================================================================
    private static boolean actualizarCampoTexto(String issueKey, String fieldId, String valor, String nombre) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("fields").put(fieldId, valor); // Envía String directo

            if (enviarPut(issueKey, payload)) {
                System.out.println("      📝 " + nombre + ": OK");
                return true;
            } else {
                System.err.println("      ⚠️ Error en " + nombre);
                return false;
            }
        } catch (Exception e) { return false; }
    }

    // =========================================================================
    // UTILIDADES VARIAS
    // =========================================================================

    private static void actualizarDropdown(String issueKey, String fieldId, String valor, String nombre) {
        if (valor == null || valor.isEmpty()) return;
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("fields").putObject(fieldId).put("value", valor.trim());
            if (enviarPut(issueKey, payload)) System.out.println("      🔹 " + nombre + ": OK");
            else System.err.println("      ⚠️ Error en " + nombre + " (Valor: " + valor + ")");
        } catch (Exception e) {}
    }

    private static void actualizarTiempos(String issueKey, String fGen, String fSol, String tNoDisp) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            boolean hayDatos = false;

            if (fGen != null && !fGen.contains("1900") && fGen.length() > 5) {
                fields.put("customfield_10321", formatearFecha(fGen));
                hayDatos = true;
            }
            if (fSol != null && !fSol.contains("1900") && fSol.length() > 5) {
                fields.put("customfield_10322", formatearFecha(fSol));
                hayDatos = true;
            }
            if (tNoDisp != null && !tNoDisp.isEmpty()) {
                fields.put("customfield_10178", tNoDisp);
                hayDatos = true;
            }

            if (hayDatos) enviarPut(issueKey, payload);
        } catch (Exception e) {}
    }

    private static void actualizarUbicacion(String issueKey, String dep, String prov, String dist, String dir, 
                                            String nomIE, String codMod, String codLoc, String medio) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");
            putIfNotEmpty(fields, "customfield_10355", dep);
            putIfNotEmpty(fields, "customfield_10356", prov);
            putIfNotEmpty(fields, "customfield_10357", dist);
            putIfNotEmpty(fields, "customfield_10358", dir);
            putIfNotEmpty(fields, "customfield_10359", nomIE);
            putIfNotEmpty(fields, "customfield_10169", codMod);
            putIfNotEmpty(fields, "customfield_10168", codLoc);
            putIfNotEmpty(fields, "customfield_10361", medio);
            fields.putObject("customfield_10286").put("value", "Creado por alertas");
            fields.putObject("customfield_10471").put("value", "Cliente");
            enviarPut(issueKey, payload);
        } catch (Exception e) {}
    }

    private static void agregarComentario(String issueKey, String cuerpo) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("body", cuerpo);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/comment"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {}
    }

    // --- HTTP ---
    private static String enviarPost(ObjectNode payload) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                .header("Authorization", "Basic " + encodedAuth)
                .header("Content-Type", "application/json")
                .header("X-ExperimentalApi", "opt-in")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 201) return mapper.readTree(response.body()).get("issueKey").asText();
        System.err.println("   ❌ Error Creación: " + response.body());
        return null;
    }

    private static boolean enviarPut(String issueKey, ObjectNode payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 204) {
                // Imprimimos el error SOLO si falla para depurar
                System.err.println("      ⚠️ Error API (" + response.statusCode() + "): " + response.body());
            }
            return response.statusCode() == 204;
        } catch (Exception e) { return false; }
    }

    // --- IMÁGENES ---
    private static void subirImagenes(String issueKey, String rutas) {
        String[] paths = rutas.split(",");
        for (String rutaRaw : paths) {
            String ruta = rutaRaw.trim();
            if (ruta.isEmpty()) continue;
            File file = new File(ruta);
            if (!file.exists()) { System.err.println("      ⚠️ Imagen no encontrada: " + ruta); continue; }
            try {
                String boundary = "---boundary" + UUID.randomUUID().toString();
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                List<byte[]> byteArrays = new ArrayList<>();
                byteArrays.add(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(("Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                byteArrays.add(fileBytes);
                byteArrays.add(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
                int totalLength = byteArrays.stream().mapToInt(b -> b.length).sum();
                byte[] multipartBody = new byte[totalLength];
                int currentPos = 0;
                for (byte[] b : byteArrays) { System.arraycopy(b, 0, multipartBody, currentPos, b.length); currentPos += b.length; }
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/attachments"))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("X-Atlassian-Token", "no-check")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                        .build();
                if (client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200) 
                    System.out.println("      📸 Imagen OK: " + file.getName());
            } catch (Exception e) {}
        }
    }

    // --- HELPERS ---
    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1": return new String[]{"126", "customfield_10250"};
            case "2": return new String[]{"127", "customfield_10251"};
            case "3": return new String[]{"128", "customfield_10252"};
            case "4": return new String[]{"129", "customfield_10253"};
            default:  return new String[]{"129", "customfield_10253"};
        }
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        ArrayNode assetArray = values.putArray(fieldId);
        ObjectNode assetObj = assetArray.addObject();
        assetObj.put("workspaceId", wsId);
        assetObj.put("id", wsId + ":" + objId);
        assetObj.put("objectId", objId);
    }

    private static String formatearFecha(String fechaExcel) {
        if (fechaExcel.contains("T") && fechaExcel.contains("-0500")) return fechaExcel;
        try { return fechaExcel.replace(" ", "T") + ".000-0500"; } catch (Exception e) { return fechaExcel; }
    }

    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
    
    private static void putIfNotEmpty(ObjectNode fields, String key, String value) {
        if (value != null && !value.isEmpty()) fields.put(key, value);
    }
}