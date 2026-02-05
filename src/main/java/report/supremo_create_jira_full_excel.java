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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

public class supremo_create_jira_full_excel {

    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        
        if (email == null || token == null) {
            System.err.println("❌ ERROR: Faltan credenciales en .env");
            return;
        }
        
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🚀 INICIANDO CARGA MASIVA CON MULTIMEDIA <<<");
            
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String host = getCellValue(row, 0);
                if (host.isEmpty()) break;

                // Lectura de datos existentes...
                String estado = getCellValue(row, 1);
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
                String numTicketRef = getCellValue(row, 15);
                String medioTrans = getCellValue(row, 16);
                String tipoIncidencia = getCellValue(row, 17);
                String tiempoNoDisp = getCellValue(row, 18);
                String itemSeleccionado = getCellValue(row, 19);

                // NUEVOS CAMPOS
                String solucionTexto = getCellValue(row, 21); // Columna V
                String rutasImagenes = getCellValue(row, 22); // Columna W (separadas por coma)

                System.out.println("\n🔄 Procesando Fila #" + row.getRowNum() + " | Host: " + host);

                // PASO 1: Crear Ticket
                String issueKey = crearTicketBasico(fechaGen, host, estado, contactoNom, contactoCel, idColegio, idDispositivo, itemSeleccionado);

                if (issueKey != null) {
                    // PASO 2: Subir imágenes y obtener IDs
                    List<String> attachmentIds = new ArrayList<>();
                    if (!rutasImagenes.isEmpty()) {
                        String[] paths = rutasImagenes.split(",");
                        for (String p : paths) {
                            String id = subirArchivoAJira(issueKey, p.trim());
                            if (id != null) attachmentIds.add(id);
                        }
                    }

                    // PASO 3: Actualizar campos y descripción ADF
                    actualizarCamposCompletos(issueKey, dep, prov, dist, direccion, fechaGen, fechaSol,
                            nombreIE, codModular, codLocal, numTicketRef, medioTrans, tipoIncidencia, 
                            tiempoNoDisp, solucionTexto, attachmentIds);

                    row.createCell(20).setCellValue(issueKey);
                } else {
                    row.createCell(20).setCellValue("ERROR");
                }
                Thread.sleep(300); 
            }

            try (FileOutputStream fileOut = new FileOutputStream("resultado_carga_full.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n✅ PROCESO TERMINADO: resultado_carga_full.xlsx");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String subirArchivoAJira(String issueKey, String rutaLocal) {
        try {
            Path path = Path.of(rutaLocal);
            if (!Files.exists(path)) {
                System.err.println("   ⚠️ Archivo no encontrado: " + rutaLocal);
                return null;
            }

            String boundary = "---" + UUID.randomUUID().toString();
            byte[] fileContent = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();

            String head = "--" + boundary + "\r\n" +
                          "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                          "Content-Type: application/octet-stream\r\n\r\n";
            String tail = "\r\n--" + boundary + "--\r\n";

            byte[] body = new byte[head.length() + fileContent.length + tail.length()];
            System.arraycopy(head.getBytes(), 0, body, 0, head.length());
            System.arraycopy(fileContent, 0, body, head.length(), fileContent.length);
            System.arraycopy(tail.getBytes(), 0, body, head.length() + fileContent.length, tail.length());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "/attachments"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("X-Atlassian-Token", "no-check")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode node = mapper.readTree(response.body());
                return node.get(0).get("id").asText();
            } else {
                System.err.println("   ❌ Error subiendo adjunto: " + response.body());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void actualizarCamposCompletos(String issueKey, String dep, String prov, String dist, String dir, 
                                                String fGen, String fSol, String nomIE, String codMod, String codLoc,
                                                String numTicket, String medio, String tipoInc, String tNoDisp,
                                                String solucionTexto, List<String> attachmentIds) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            ObjectNode fields = payload.putObject("fields");

            putIfNotEmpty(fields, "customfield_10355", dep);
            putIfNotEmpty(fields, "customfield_10356", prov);
            putIfNotEmpty(fields, "customfield_10357", dist);
            putIfNotEmpty(fields, "customfield_10358", dir);
            putIfNotEmpty(fields, "customfield_10321", fGen);
            putIfNotEmpty(fields, "customfield_10322", fSol);
            putIfNotEmpty(fields, "customfield_10359", nomIE);
            putIfNotEmpty(fields, "customfield_10169", codMod);
            putIfNotEmpty(fields, "customfield_10168", codLoc);
            putIfNotEmpty(fields, "customfield_10320", numTicket);
            putIfNotEmpty(fields, "customfield_10361", medio);
            putIfNotEmpty(fields, "customfield_10469", tipoInc);
            putIfNotEmpty(fields, "customfield_10178", tNoDisp);

            fields.putObject("customfield_10471").put("value", "Cliente");
            fields.putObject("customfield_10135").put("value", "Gabinete apagado");

            // --- CONSTRUCCIÓN DE LA SOLUCIÓN EN ADF ---
            ObjectNode adfDoc = mapper.createObjectNode();
            adfDoc.put("type", "doc");
            adfDoc.put("version", 1);
            ArrayNode contentArray = adfDoc.putArray("content");

            // Texto de solución
            if (solucionTexto != null && !solucionTexto.isEmpty()) {
                ObjectNode para = contentArray.addObject();
                para.put("type", "paragraph");
                para.putArray("content").addObject()
                    .put("type", "text")
                    .put("text", solucionTexto);
            }

            // Imágenes dinámicas (Array)
            for (String attId : attachmentIds) {
                ObjectNode mediaSingle = contentArray.addObject();
                mediaSingle.put("type", "mediaSingle");
                mediaSingle.putObject("attrs").put("layout", "align-start").put("width", 500);
                
                mediaSingle.putArray("content").addObject()
                    .put("type", "media")
                    .putObject("attrs")
                        .put("id", attId)
                        .put("type", "file")
                        .put("collection", "");
            }

            fields.set("customfield_10089", adfDoc);

            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("   ✨ Ticket actualizado con texto e imágenes.");

        } catch (Exception e) {
            System.err.println("Error en update: " + e.getMessage());
        }
    }

    // Métodos auxiliares (obtenerConfiguracionItem, crearTicketBasico, agregarActivo, getCellValue, putIfNotEmpty)
    // [Se mantienen iguales a tu código original pero adaptados a la lógica]
    
    private static String[] obtenerConfiguracionItem(String numeroItem) {
        String reqId = "128"; String campId = "customfield_10252";
        if (numeroItem.equals("1")) { reqId = "126"; campId = "customfield_10250"; }
        else if (numeroItem.equals("2")) { reqId = "127"; campId = "customfield_10251"; }
        else if (numeroItem.equals("4")) { reqId = "129"; campId = "customfield_10253"; }
        return new String[]{reqId, campId};
    }

    private static String crearTicketBasico(String fGen, String host, String estado, String nom, String cel, String colId, String dispId, String itemNum) {
        try {
            String[] config = obtenerConfiguracionItem(itemNum);
            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", SERVICE_DESK_ID);
            payload.put("requestTypeId", config[0]);
            ObjectNode values = payload.putObject("requestFieldValues");
            values.put("summary", host);
            values.put("customfield_10180", estado);
            values.put("customfield_10090", nom);
            values.put("customfield_10091", cel);
            values.putObject("customfield_10176").put("value", "Incidente");
            values.putObject("customfield_10504").put("value", "Rural");
            values.putObject("customfield_10394").put("value", "Servicio de acceso a Internet");
            values.putArray("customfield_10002").add(3);
            if (!colId.isEmpty()) agregarActivo(values, "customfield_10170", WORKSPACE_ID, colId);
            if (!dispId.isEmpty()) agregarActivo(values, config[1], WORKSPACE_ID, dispId);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("X-ExperimentalApi", "opt-in")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 201) return mapper.readTree(resp.body()).get("issueKey").asText();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private static void agregarActivo(ObjectNode v, String fId, String ws, String oId) {
        v.putArray(fId).addObject().put("id", ws + ":" + oId);
    }

    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        return (cell == null) ? "" : new DataFormatter().formatCellValue(cell).trim();
    }

    private static void putIfNotEmpty(ObjectNode fields, String key, String value) {
        if (value != null && !value.isEmpty()) fields.put(key, value);
    }
}