package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
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

public class ZZUPZZZ {

    // --- CONFIGURACIÓN DE JIRA ---
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";

    // IDs DE CAMPOS PERSONALIZADOS (Mapa MINEDU)
    static final String FIELD_DESC_INCIDENTE = "customfield_10180";
    static final String FIELD_SOLUCION = "customfield_10089";
    static final String FIELD_ORGANIZATION = "customfield_10002";
    static final String FIELD_CATEGORIA = "customfield_10394";
    static final String FIELD_CAUSA_RAIZ = "customfield_10135";
    static final String FIELD_FECHA_GEN = "customfield_10321";
    static final String FIELD_FECHA_SOL = "customfield_10322";
    static final String FIELD_TIEMPO_SOLUCION = "customfield_10177";
    static final String FIELD_IMPUTABILIDAD = "customfield_10471"; // <-- CAMPO AÑADIDO

    // ÍNDICES DE COLUMNAS EXCEL (Sincronizado con TTT)
    static final int COL_RESUMEN = 0, COL_DESC = 1, COL_COLEGIO = 2, COL_DISPOSITIVO = 3;
    static final int COL_CONTACTO_NOM = 4, COL_CONTACTO_CEL = 5, COL_DEP = 6, COL_PROV = 7;
    static final int COL_DIST = 8, COL_DIR = 9, COL_FECHA_GEN_IDX = 10, COL_FECHA_SOL_IDX = 11;
    static final int COL_NOMBRE_IE = 12, COL_COD_MODULAR = 13, COL_COD_LOCAL = 14;
    static final int COL_MEDIO_TRANS = 16, COL_TIPO_INC = 17, COL_TIEMPO_NODISP = 18;
    static final int COL_TIEMPO_SOLUCION = 19;
    static final int COL_ITEM = 20, COL_SOLUCION = 21, COL_RUTAS_IMG = 22, COL_AREA = 23;
    static final int COL_CAUSA_RAIZ = 24, COL_CAT_SERVICIO = 25;

    // COLUMNAS CLAVE PARA LA ACTUALIZACIÓN
    static final int COL_TICKET_KEY = 26;
    static final int COL_IMPUTABILIDAD = 27; // <-- ÍNDICE AÑADIDO

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
            System.err.println("❌ ERROR: Faltan credenciales en el archivo .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        System.out.println(">>> 🚀 INICIANDO ACTUALIZACIÓN MASIVA (Versión Definitiva con Imputabilidad) <<<");

        try (FileInputStream file = new FileInputStream(new File("DDDFINAL11.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int exitosos = 0;
            int errores = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String issueKey = getValRobust(row, COL_TICKET_KEY, formatter);
                if (issueKey.isEmpty()) continue;

                System.out.println("\n------------------------------------------------");
                System.out.println("🔄 Ticket: " + issueKey + " | " + getValRobust(row, COL_RESUMEN, formatter));

                try {
                    String rutasImagenes = getValRobust(row, COL_RUTAS_IMG, formatter);
                    List<String> nombresImagenesSubidas = new ArrayList<>();

                    // 1) PRIMERO: actualizar texto y demás campos SIN imágenes
                    ObjectNode putPayload = mapper.createObjectNode();
                    ObjectNode putFields = putPayload.putObject("fields");
                    prepararCamposActualizacion(putFields, row, formatter, issueKey);

                    if (enviarPutV2(issueKey, putPayload, "Actualización General de Campos")) {

                        // 2) DESPUÉS: manejar imágenes
                        if (!rutasImagenes.isEmpty()) {
                            eliminarAdjuntosExistentes(issueKey);
                            nombresImagenesSubidas = subirImagenesYObtenerNombres(issueKey, rutasImagenes);

                            // 3) Luego actualizar solo solución con texto + imágenes
                            String textoSolucion = getValRobust(row, COL_SOLUCION, formatter);
                            if (textoSolucion.isEmpty()) {
                                textoSolucion = obtenerTextoActual(issueKey, FIELD_SOLUCION);
                                textoSolucion = limpiarWikiViejo(textoSolucion);
                            }

                            String solucionFinal = construirSolucionFinal(textoSolucion, nombresImagenesSubidas);

                            if (!solucionFinal.isEmpty()) {
                                ObjectNode payloadSol = mapper.createObjectNode();
                                ObjectNode fieldsSol = payloadSol.putObject("fields");
                                fieldsSol.put(FIELD_SOLUCION, solucionFinal);
                                enviarPutV2(issueKey, payloadSol, "Actualización de Solución con Imágenes");
                            }
                        }

                        // 4) Cambio de estado y post-actualización (CORREGIDO)
                        String fechaSol = getValRobust(row, COL_FECHA_SOL_IDX, formatter);
                        String textoSolucion = getValRobust(row, COL_SOLUCION, formatter);

                        if (!fechaSol.isEmpty() && (!textoSolucion.isEmpty() || !nombresImagenesSubidas.isEmpty())) {
                            
                            // 1ro: PRE-RESOLUCIÓN - Forzar las fechas mientras el ticket sigue abierto/editable
                            ObjectNode finalPayload = mapper.createObjectNode();
                            ObjectNode fFields = finalPayload.putObject("fields");

                            String fSolFormateada = formatearParaJira(fechaSol);
                            if (fSolFormateada != null) fFields.put(FIELD_FECHA_SOL, fSolFormateada);

                            String tSol = getValRobust(row, COL_TIEMPO_SOLUCION, formatter);
                            if (!tSol.isEmpty()) fFields.put(FIELD_TIEMPO_SOLUCION, tSol);

                            enviarPutV2(issueKey, finalPayload, "Pre-Resolución (Fechas y Tiempo forzados)");

                            // 2do: RESOLUCIÓN - Cambiamos el estado a 'Resuelta'
                            System.out.println("   🔄 Cambiando estado a 'Resuelta'...");
                            cambiarEstadoTicket(issueKey, "Resuelta");
                        }

                        exitosos++;
                    } else {
                        errores++;
                    }

                    Thread.sleep(500); // Pausa para no saturar la API
                } catch (Exception rowEx) {
                    errores++;
                    System.err.println("❌ Error procesando ticket " + issueKey + ": " + rowEx.getMessage());
                }
            }

            System.out.println("\n🏁 PROCESO FINALIZADO | Correctos: " + exitosos + " | Fallidos: " + errores);

        } catch (Exception e) {
            System.err.println("❌ Error crítico procesando el archivo Excel:");
            e.printStackTrace();
        }
    }

    private static void prepararCamposActualizacion(ObjectNode fields, Row row, DataFormatter fmt, String issueKey) {
        String descAlarma = getValRobust(row, COL_DESC, fmt);
        if (!descAlarma.isEmpty()) fields.put(FIELD_DESC_INCIDENTE, descAlarma);

        // Texto de solución SIN imágenes en esta fase
        String textoSolucion = getValRobust(row, COL_SOLUCION, fmt);
        if (!textoSolucion.isEmpty()) {
            fields.put(FIELD_SOLUCION, textoSolucion);
        }

        String resumen = getValRobust(row, COL_RESUMEN, fmt);
        if (!resumen.isEmpty()) fields.put("summary", resumen.length() > 250 ? resumen.substring(0, 245) + "..." : resumen);

        String tipoInc = getValRobust(row, COL_TIPO_INC, fmt);
        if (!tipoInc.isEmpty()) fields.put("customfield_10469", tipoInc.toUpperCase());

        putIfNotEmpty(fields, "customfield_10090", getValRobust(row, COL_CONTACTO_NOM, fmt));
        putIfNotEmpty(fields, "customfield_10091", getValRobust(row, COL_CONTACTO_CEL, fmt));

        String catServicio = getValRobust(row, COL_CAT_SERVICIO, fmt);
        String causaRaiz = getValRobust(row, COL_CAUSA_RAIZ, fmt);
        String area = getValRobust(row, COL_AREA, fmt);
        String imputabilidad = getValRobust(row, COL_IMPUTABILIDAD, fmt); // <-- EXTRACCIÓN AÑADIDA

        if (!catServicio.isEmpty()) fields.putObject(FIELD_CATEGORIA).put("value", catServicio.trim());
        if (!causaRaiz.isEmpty()) fields.putObject(FIELD_CAUSA_RAIZ).put("value", causaRaiz.trim());
        if (!area.isEmpty()) fields.putObject("customfield_10504").put("value", area.equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
        if (!imputabilidad.isEmpty()) fields.putObject(FIELD_IMPUTABILIDAD).put("value", imputabilidad.trim()); // <-- INYECCIÓN AÑADIDA

        String fGen = formatearParaJira(getValRobust(row, COL_FECHA_GEN_IDX, fmt));
        String tNoDisp = getValRobust(row, COL_TIEMPO_NODISP, fmt);
        if (fGen != null) fields.put(FIELD_FECHA_GEN, fGen);
        if (!tNoDisp.isEmpty()) fields.put("customfield_10178", tNoDisp);

        putIfNotEmpty(fields, "customfield_10355", getValRobust(row, COL_DEP, fmt));
        putIfNotEmpty(fields, "customfield_10356", getValRobust(row, COL_PROV, fmt));
        putIfNotEmpty(fields, "customfield_10357", getValRobust(row, COL_DIST, fmt));
        putIfNotEmpty(fields, "customfield_10358", getValRobust(row, COL_DIR, fmt));
        putIfNotEmpty(fields, "customfield_10359", getValRobust(row, COL_NOMBRE_IE, fmt));
        putIfNotEmpty(fields, "customfield_10169", getValRobust(row, COL_COD_MODULAR, fmt));
        putIfNotEmpty(fields, "customfield_10168", getValRobust(row, COL_COD_LOCAL, fmt));
        putIfNotEmpty(fields, "customfield_10361", getValRobust(row, COL_MEDIO_TRANS, fmt));

        String colId = getValRobust(row, COL_COLEGIO, fmt);
        String dispId = getValRobust(row, COL_DISPOSITIVO, fmt);
        String itemNum = extraerSoloNumeros(getValRobust(row, COL_ITEM, fmt));
        String[] config = obtenerConfiguracionItem(itemNum);
        if (!colId.isEmpty()) agregarActivo(fields, "customfield_10170", WORKSPACE_ID, colId);
        if (!dispId.isEmpty() && !config[1].isEmpty()) agregarActivo(fields, config[1], WORKSPACE_ID, dispId);
    }

    // =========================================================================
    // MÉTODOS DE RED Y API
    // =========================================================================

    private static String obtenerTextoActual(String issueKey, String fieldId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "?fields=" + fieldId))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode node = mapper.readTree(res.body()).path("fields").path(fieldId);
                if (!node.isMissingNode() && !node.isNull()) {
                    return node.asText();
                }
            }
        } catch (Exception e) {
        }
        return "";
    }

    private static void eliminarAdjuntosExistentes(String issueKey) {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "?fields=attachment"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();

            HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());

            if (getRes.statusCode() == 200) {
                JsonNode attachments = mapper.readTree(getRes.body()).path("fields").path("attachment");
                if (attachments.isArray() && attachments.size() > 0) {
                    System.out.println("   🗑️ Eliminando " + attachments.size() + " adjuntos previos...");
                    for (JsonNode attachment : attachments) {
                        String id = attachment.path("id").asText();
                        HttpRequest delReq = HttpRequest.newBuilder()
                                .uri(URI.create(jiraUrl + "/rest/api/2/attachment/" + id))
                                .header("Authorization", "Basic " + encodedAuth)
                                .DELETE()
                                .build();
                        client.send(delReq, HttpResponse.BodyHandlers.ofString());
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private static List<String> subirImagenesYObtenerNombres(String issueKey, String rutas) {
        List<String> nombresCargados = new ArrayList<>();
        String rutasNormalizadas = rutas.replace(";", ",");

        for (String r : rutasNormalizadas.split(",")) {
            String rutaLimpia = r.trim();
            if (rutaLimpia.isEmpty()) continue;

            File f = new File(rutaLimpia);
            if (!f.exists()) continue;

            try {
                String boundary = "---" + UUID.randomUUID();
                byte[] body = createMultipartBody(f, boundary);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/attachments"))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("X-Atlassian-Token", "no-check")
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                        .build();

                HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode jsonResponse = mapper.readTree(response.body());
                    if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                        String nombreFinalJira = jsonResponse.get(0).path("filename").asText();
                        nombresCargados.add(nombreFinalJira);
                        System.out.println("   📸 Imagen subida y enlazada como: " + nombreFinalJira);
                    }
                } else {
                    System.err.println("   ❌ Error subiendo imagen: " + response.body());
                }
            } catch (Exception e) {
                System.err.println("   ❌ Excepción subiendo imagen: " + e.getMessage());
            }
        }
        return nombresCargados;
    }

    private static boolean enviarPutV2(String issueKey, ObjectNode payload, String nombreLog) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                System.out.println("   ✅ " + nombreLog + " OK");
                return true;
            } else {
                System.err.println("   ⚠️ Falló " + nombreLog + " (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void cambiarEstadoTicket(String issueKey, String estadoDestino) {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();

            HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            JsonNode transitions = mapper.readTree(getRes.body()).path("transitions");

            String transitionId = null;
            for (JsonNode t : transitions) {
                if (t.path("to").path("name").asText().equalsIgnoreCase(estadoDestino)) {
                    transitionId = t.path("id").asText();
                    break;
                }
            }
            if (transitionId == null) return;

            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("transition").put("id", transitionId);

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            client.send(postReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES (EXCEL Y UTILIDADES)
    // =========================================================================

    private static byte[] createMultipartBody(File file, String boundary) throws Exception {
        String fileName = file.getName();

        String mimeType = "application/octet-stream";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) mimeType = "image/png";
        else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) mimeType = "image/jpeg";
        else if (lowerName.endsWith(".gif")) mimeType = "image/gif";

        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n";

        byte[] h = header.getBytes(StandardCharsets.UTF_8);
        byte[] f = Files.readAllBytes(file.toPath());
        byte[] t = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[h.length + f.length + t.length];
        System.arraycopy(h, 0, res, 0, h.length);
        System.arraycopy(f, 0, res, h.length, f.length);
        System.arraycopy(t, 0, res, h.length + f.length, t.length);
        return res;
    }

    private static String getValRobust(Row row, int index, DataFormatter fmt) {
        Cell c = row.getCell(index);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(c.getDateCellValue());
        }
        return fmt.formatCellValue(c).trim();
    }

    private static String formatearParaJira(String raw) {
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
        } catch (Exception e) {
            return null;
        }
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        values.putArray(fieldId).addObject()
                .put("workspaceId", wsId)
                .put("id", wsId + ":" + objId)
                .put("objectId", objId);
    }

    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1":
                return new String[]{"126", "customfield_10250", "2"};
            case "2":
                return new String[]{"127", "customfield_10251", "1"};
            case "3":
                return new String[]{"128", "customfield_10252", "3"};
            case "4":
                return new String[]{"129", "customfield_10253", "4"};
            default:
                return new String[]{"129", "customfield_10253", ""};
        }
    }

    private static String extraerSoloNumeros(String input) {
        if (input == null) return "";
        Matcher m = Pattern.compile("\\d+").matcher(input);
        return m.find() ? m.group() : "";
    }

    private static void putIfNotEmpty(ObjectNode fields, String key, String value) {
        if (value != null && !value.isEmpty()) fields.put(key, value);
    }

    private static String limpiarWikiViejo(String texto) {
        if (texto == null) return "";
        return texto.replaceAll("!([^!\\n]+)(\\|[^!\\n]+)?!", "").trim();
    }

    private static String construirSolucionFinal(String textoSolucion, List<String> nombresImagenes) {
        StringBuilder sb = new StringBuilder();

        if (textoSolucion != null && !textoSolucion.trim().isEmpty()) {
            sb.append(textoSolucion.trim());
        }

        if (nombresImagenes != null && !nombresImagenes.isEmpty()) {
            if (sb.length() > 0) sb.append("\n\n");
            for (String nombreImg : nombresImagenes) {
                sb.append("!").append(nombreImg).append("|width=1000!\n\n");
            }
        }

        return sb.toString().trim();
    }
}