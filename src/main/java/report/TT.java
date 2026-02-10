package report;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TT {

    // --- CONFIGURACIÓN DE JIRA ---
    static final String SERVICE_DESK_ID = "34";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // IDs DE CAMPOS PERSONALIZADOS (Custom Fields)
    static final String FIELD_DESC_INCIDENTE = "customfield_10180"; 
    static final String FIELD_SOLUCION = "customfield_10089";
    static final String FIELD_TICKET_ID = "customfield_10320";
    static final String FIELD_ORGANIZATION = "customfield_10002"; // Organizations
    
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        // Cargar variables de entorno (.env)
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";
        
        if (email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ ERROR: Credenciales faltantes en el archivo .env");
            return;
        }
        
        // Autenticación Basic en Base64
        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        try (FileInputStream file = new FileInputStream(new File("carga_tickets.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            System.out.println(">>> 🚀 INICIANDO CARGA CORREGIDA (Soporte Organizaciones + API v3) <<<");
            
            int exitosos = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar cabecera

                String resumen = getCellValue(row, 0); 
                if (resumen.isEmpty()) break;

                // Lectura de celdas del Excel
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
                
                // Extraer el número del Item (ej: "Item 3" -> "3")
                String itemRaw = getCellValue(row, 19); 
                String itemNum = extraerSoloNumeros(itemRaw); 
                
                String textoSolucion = getCellValue(row, 20); 
                String rutasImagenes = getCellValue(row, 21); 
                String areaExcel = getCellValue(row, 22);
                String causaRaiz = getCellValue(row, 23);     
                String catServicio = getCellValue(row, 24);   

                System.out.println("\n------------------------------------------------");
                System.out.println("🔄 Procesando ticket: " + resumen);

                // 1️⃣ CREAR TICKET (Usa Service Desk API)
                String issueKey = crearTicketBasico(resumen, contactoNom, contactoCel, idColegio, idDispositivo, itemNum, areaExcel, catServicio);

                if (issueKey != null) {
                    System.out.println("   ✅ 1. Creado con éxito: " + issueKey);

                    // 2️⃣ ACTUALIZAR DESCRIPCIÓN (ADF format)
                    if (!descAlarma.isEmpty()) {
                        String descLimpia = descAlarma.replace("\\n", "\n").trim();
                        actualizarCampoADF(issueKey, FIELD_DESC_INCIDENTE, descLimpia, "Descripción");
                    }

                    // 3️⃣ NÚMERO DE TICKET
                    actualizarCampoTexto(issueKey, FIELD_TICKET_ID, issueKey, "Referencia Ticket");

                    // 4️⃣ DROPDOWNS Y CATEGORÍAS
                    if (!catServicio.isEmpty()) actualizarDropdown(issueKey, "customfield_10394", catServicio, "Categoría");
                    if (!causaRaiz.isEmpty()) actualizarDropdown(issueKey, "customfield_10135", causaRaiz, "Causa Raíz");
                    
                    // 5️⃣ SOLUCIÓN (ADF format)
                    if (!textoSolucion.isEmpty()) {
                        actualizarCampoADF(issueKey, FIELD_SOLUCION, textoSolucion, "Solución");
                    }
                    
                    if (!tipoIncidencia.isEmpty()) {
                        actualizarCampoTexto(issueKey, "customfield_10469", tipoIncidencia.toUpperCase(), "Tipo Incidencia");
                    }

                    // 6️⃣ TIEMPOS, UBICACIÓN E IMÁGENES
                    actualizarTiempos(issueKey, fechaGen, fechaSol, tiempoNoDisp);
                    actualizarUbicacion(issueKey, dep, prov, dist, direccion, nombreIE, codModular, codLocal, medioTrans);
                    if (!rutasImagenes.isEmpty()) subirImagenes(issueKey, rutasImagenes);

                    // Escribir resultado en el Excel
                    row.createCell(25).setCellValue(issueKey); 
                    exitosos++;
                }
                
                Thread.sleep(800); // Evitar Rate Limit de la API
            }

            // Guardar el archivo Excel con los resultados
            try (FileOutputStream fileOut = new FileOutputStream("resultado_carga_final.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n🏁 PROCESO FINALIZADO. Tickets exitosos: " + exitosos);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    /**
     * Crea el ticket inicial en Jira Service Management.
     * Maneja el mapeo de Request Types y el envío correcto de Organizaciones por ID.
     */
    private static String crearTicketBasico(String resumen, String nombre, String cel, String colId, String dispId, String itemNum, String area, String cat) {
        try {
            // Obtenemos la configuración según el número de item (1, 2, 3 o 4)
            String[] config = obtenerConfiguracionItem(itemNum); 
            String requestTypeId = config[0];
            String assetFieldId = config[1];
            String realOrgId = config[2]; // ID real de la organización en Jira

            ObjectNode payload = mapper.createObjectNode();
            payload.put("serviceDeskId", SERVICE_DESK_ID);
            payload.put("requestTypeId", requestTypeId); 
            
            ObjectNode values = payload.putObject("requestFieldValues");
            values.put("summary", resumen.length() > 250 ? resumen.substring(0, 245) + "..." : resumen);
            values.put("customfield_10090", nombre); // Nombre contacto
            values.put("customfield_10091", cel);    // Celular contacto
            values.putObject("customfield_10176").put("value", "Incidente");
            
            // 🚨 CORRECCIÓN CLAVE: Envío de Organización como Arreglo de IDs (Strings)
            if (!realOrgId.isEmpty()) {
                ArrayNode orgArray = values.putArray(FIELD_ORGANIZATION);
                orgArray.add(realOrgId); 
                System.out.println("      🏢 Asignando Org ID: " + realOrgId + " (Item " + itemNum + ")");
            }

            // Campos de selección urbana/rural y categoría
            values.putObject("customfield_10504").put("value", area.equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
            values.putObject("customfield_10394").put("value", cat.isEmpty() ? "Servicio de acceso a Internet" : cat);

            // Agregar activos (Assets/Insight)
            if (!colId.isEmpty()) agregarActivo(values, "customfield_10170", WORKSPACE_ID, colId);
            if (!dispId.isEmpty()) agregarActivo(values, assetFieldId, WORKSPACE_ID, dispId);

            String jsonBody = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/servicedeskapi/request"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .header("X-ExperimentalApi", "opt-in")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                return mapper.readTree(response.body()).get("issueKey").asText();
            } else {
                System.err.println("   ❌ Error en creación (Status " + response.statusCode() + "): " + response.body());
                return null;
            }
        } catch (Exception e) { return null; }
    }

    /**
     * Mapea el número de item del Excel con los IDs reales de Jira.
     * Formato: { RequestTypeID, AssetFieldID, RealOrganizationID }
     */
    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1": return new String[]{"126", "customfield_10250", "2"}; // Item 1 es ID 2
            case "2": return new String[]{"127", "customfield_10251", "1"}; // Item 2 es ID 1
            case "3": return new String[]{"128", "customfield_10252", "3"}; // Item 3 es ID 3
            case "4": return new String[]{"129", "customfield_10253", "4"}; // Item 4 es ID 4
            default:  return new String[]{"129", "customfield_10253", ""};
        }
    }

    /**
     * Actualiza campos de texto enriquecido usando Atlassian Document Format (ADF).
     * Requerido por la API v3 de Jira.
     */
    private static boolean actualizarCampoADF(String issueKey, String fieldId, String texto, String nombreLog) {
        try {
            ObjectNode doc = mapper.createObjectNode();
            doc.put("type", "doc").put("version", 1);
            ArrayNode content = doc.putArray("content");
            ObjectNode paragraph = content.addObject();
            paragraph.put("type", "paragraph");
            ArrayNode pContent = paragraph.putArray("content");
            pContent.addObject().put("type", "text").put("text", texto); 

            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("fields").set(fieldId, doc);

            return enviarPutV3(issueKey, payload, nombreLog);
        } catch (Exception e) { return false; }
    }

    private static void actualizarCampoTexto(String issueKey, String fieldId, String valor, String nombre) {
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("fields").put(fieldId, valor);
        enviarPutV3(issueKey, payload, nombre);
    }

    private static void actualizarDropdown(String issueKey, String fieldId, String valor, String nombre) {
        if (valor == null || valor.isEmpty()) return;
        ObjectNode payload = mapper.createObjectNode();
        payload.putObject("fields").putObject(fieldId).put("value", valor.trim());
        enviarPutV3(issueKey, payload, nombre);
    }

    private static boolean enviarPutV3(String issueKey, ObjectNode payload, String nombreLog) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                System.out.println("      📝 " + nombreLog + ": OK");
                return true;
            } else {
                System.err.println("      ⚠️ Falló " + nombreLog + " (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) { return false; }
    }

    // --- MÉTODOS DE APOYO (FECHAS, UBICACIÓN, IMÁGENES) ---

    private static void actualizarTiempos(String issueKey, String fGen, String fSol, String tNoDisp) {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode fields = payload.putObject("fields");
        boolean update = false;
        if (fGen.length() > 5 && !fGen.contains("1900")) { fields.put("customfield_10321", formatearFecha(fGen)); update = true; }
        if (fSol.length() > 5 && !fSol.contains("1900")) { fields.put("customfield_10322", formatearFecha(fSol)); update = true; }
        if (!tNoDisp.isEmpty()) { fields.put("customfield_10178", tNoDisp); update = true; }
        if (update) enviarPutV3(issueKey, payload, "Tiempos");
    }

    private static void actualizarUbicacion(String issueKey, String dep, String prov, String dist, String dir, String nom, String mod, String loc, String med) {
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode f = payload.putObject("fields");
        putIfNotEmpty(f, "customfield_10355", dep);
        putIfNotEmpty(f, "customfield_10356", prov);
        putIfNotEmpty(f, "customfield_10357", dist);
        putIfNotEmpty(f, "customfield_10358", dir);
        putIfNotEmpty(f, "customfield_10359", nom);
        putIfNotEmpty(f, "customfield_10169", mod);
        putIfNotEmpty(f, "customfield_10168", loc);
        putIfNotEmpty(f, "customfield_10361", med);
        f.putObject("customfield_10286").put("value", "Creado por alertas");
        f.putObject("customfield_10471").put("value", "Cliente");
        enviarPutV3(issueKey, payload, "Ubicación");
    }

    private static void subirImagenes(String issueKey, String rutas) {
        for (String r : rutas.split(",")) {
            File f = new File(r.trim());
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
                if (client.send(req, HttpResponse.BodyHandlers.ofString()).statusCode() == 200)
                    System.out.println("      📸 Imagen cargada: " + f.getName());
            } catch (Exception e) {}
        }
    }

    // --- UTILIDADES ---

    private static byte[] createMultipartBody(File file, String boundary) throws Exception {
        String header = "--" + boundary + "\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n";
        byte[] h = header.getBytes(StandardCharsets.UTF_8);
        byte[] f = Files.readAllBytes(file.toPath());
        byte[] t = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[h.length + f.length + t.length];
        System.arraycopy(h, 0, res, 0, h.length);
        System.arraycopy(f, 0, res, h.length, f.length);
        System.arraycopy(t, 0, res, h.length + f.length, t.length);
        return res;
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        ArrayNode assetArray = values.putArray(fieldId);
        ObjectNode assetObj = assetArray.addObject();
        assetObj.put("workspaceId", wsId);
        assetObj.put("id", wsId + ":" + objId);
        assetObj.put("objectId", objId);
    }

    private static String extraerSoloNumeros(String input) {
        if (input == null) return "";
        Matcher m = Pattern.compile("\\d+").matcher(input);
        return m.find() ? m.group() : "";
    }

    private static String formatearFecha(String f) {
        if (f.contains("T")) return f;
        return f.replace(" ", "T") + ".000-0500";
    }

    private static String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return new DataFormatter().formatCellValue(cell).trim();
    }
    
    private static void putIfNotEmpty(ObjectNode fields, String key, String value) {
        if (value != null && !value.isEmpty()) fields.put(key, value);
    }
}