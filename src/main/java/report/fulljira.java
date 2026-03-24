package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class fulljira {

    // === CONFIGURACIÓN DE CAMPOS ===
    // Campos Estándar de Jira (No requieren ID numérico)
    // summary, status, priority, issuetype, reporter, assignee, created, resolutiondate
    
    // Campos Personalizados (IDs extraídos de tu análisis)
    private static final String ID_ORGANIZACION    = "customfield_10002";
    private static final String ID_PRIMERA_RESP    = "customfield_10025"; 
    
    // Ubicación y Datos IE
    private static final String ID_COD_LOCAL       = "customfield_10168";
    private static final String ID_COD_MODULAR     = "customfield_10169";
    private static final String ID_NOMBRE_IE       = "customfield_10359";
    private static final String ID_DEPARTAMENTO    = "customfield_10355";
    private static final String ID_PROVINCIA       = "customfield_10356";
    private static final String ID_DISTRITO        = "customfield_10357";
    private static final String ID_DIRECCION       = "customfield_10358";
    private static final String ID_AREA            = "customfield_10504";

    // Contacto
    private static final String ID_NOMBRE_CONTACTO = "customfield_10090";
    private static final String ID_NUMERO_CONTACTO = "customfield_10091";

    // Clasificación Técnica
    private static final String ID_ITEM            = "customfield_10249";
    private static final String ID_CAT_SERVICIO    = "customfield_10394";
    private static final String ID_MEDIO_TX        = "customfield_10361";
    private static final String ID_CANAL_COMUNIC   = "customfield_10286";
    
    // Fechas Manuales y Métricas
    private static final String ID_FECHA_GEN_MANUAL = "customfield_10321";
    private static final String ID_FECHA_SOL_MANUAL = "customfield_10322";
    private static final String ID_TIEMPO_SOLUCION  = "customfield_10177";
    private static final String ID_DISPONIBILIDAD   = "customfield_10178";
    
    // Descripciones Largas (Rich Text)
    private static final String ID_DESCRIP_INCID    = "customfield_10180";
    private static final String ID_DESCRIP_SOLUC    = "customfield_10089";

    private static final String JQL_QUERY = "project = 'MSP' ORDER BY created DESC";

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String auth = Base64.getEncoder().encodeToString((dotenv.get("JIRA_EMAIL") + ":" + dotenv.get("JIRA_TOKEN")).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newBuilder().build();
        
        SXSSFWorkbook workbook = new SXSSFWorkbook(100); 
        Sheet sheet = workbook.createSheet("Reporte Maestro Jira");

        // --- ENCABEZADOS DE METADATA (FILAS 0 y 1) ---
        LocalDateTime ahora = LocalDateTime.now();
        CellStyle metaStyle = workbook.createCellStyle();
        Font metaFont = workbook.createFont(); metaFont.setBold(true); metaFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        metaStyle.setFont(metaFont);

        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("FECHA EXTRACCIÓN:");
        r0.createCell(1).setCellValue(ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        r0.getCell(0).setCellStyle(metaStyle);

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("JQL UTILIZADO:");
        r1.createCell(1).setCellValue(JQL_QUERY);
        r1.getCell(0).setCellStyle(metaStyle);

        // --- ENCABEZADOS DE TABLA (FILA 3) ---
        // 32 COLUMNAS TOTALES
        String[] headers = {
            "CLAVE", "RESUMEN (SUMMARY)", "ESTADO", "PRIORIDAD", "TIPO TICKET", 
            "REPORTADOR", "ASIGNADO", "ORGANIZACIÓN", "F. 1RA RESPUESTA",
            "CATEGORÍA SERVICIO", "CANAL", "MEDIO TX", "ÍTEM",
            "CÓD. LOCAL", "CÓD. MODULAR", "NOMBRE IE", 
            "DEPARTAMENTO", "PROVINCIA", "DISTRITO", "DIRECCIÓN", "ÁREA",
            "CONTACTO", "CELULAR",
            "F. CREACIÓN (SIS)", "F. GEN. MANUAL (REP)", 
            "F. RESOLUCIÓN (SIS)", "F. SOL. MANUAL (REP)",
            "TIEMPO SOLUCIÓN", "TIEMPO INDISPONIBILIDAD",
            "DESCRIPCIÓN INCIDENTE", "DESCRIPCIÓN SOLUCIÓN"
        };

        Row headerRow = sheet.createRow(3);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(font);
        headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
            sheet.setColumnWidth(i, (i == 1) ? 12000 : 4500); // Resumen más ancho
        }

        sheet.createFreezePane(0, 4); 
        sheet.setAutoFilter(new CellRangeAddress(3, 3, 0, headers.length - 1));

        int rowIdx = 4;
        String nextPageToken = null;
        boolean hasMore = true;

        System.out.println("🚀 INICIANDO EXTRACCIÓN TOTAL (INCLUYENDO SUMMARY)...");

        try {
            while (hasMore) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", JQL_QUERY);
                payload.put("maxResults", 50);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);
                
                ArrayNode fields = payload.putArray("fields");
                // CAMPOS ESTÁNDAR + CUSTOM
                fields.add("key").add("summary").add("status").add("priority").add("issuetype")
                      .add("reporter").add("assignee").add("created").add("resolutiondate")
                      .add(ID_ORGANIZACION).add(ID_PRIMERA_RESP)
                      .add(ID_CAT_SERVICIO).add(ID_CANAL_COMUNIC).add(ID_MEDIO_TX).add(ID_ITEM)
                      .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                      .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO).add(ID_DIRECCION).add(ID_AREA)
                      .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
                      .add(ID_FECHA_GEN_MANUAL).add(ID_FECHA_SOL_MANUAL)
                      .add(ID_TIEMPO_SOLUCION).add(ID_DISPONIBILIDAD)
                      .add(ID_DESCRIP_INCID).add(ID_DESCRIP_SOLUC);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode issues = root.path("issues");

                    for (JsonNode issue : issues) {
                        JsonNode f = issue.path("fields");
                        Row row = sheet.createRow(rowIdx++);
                        int c = 0;

                        // 1. GESTIÓN Y CABECERA
                        createCell(row, c++, issue.path("key").asText());
                        createCell(row, c++, f.path("summary").asText()); // <--- ¡AQUÍ ESTÁ EL SUMMARY!
                        createCell(row, c++, f.path("status").path("name").asText());
                        createCell(row, c++, f.path("priority").path("name").asText());
                        createCell(row, c++, f.path("issuetype").path("name").asText());
                        createCell(row, c++, f.path("reporter").path("displayName").asText());
                        createCell(row, c++, f.path("assignee").path("displayName").asText());
                        createCell(row, c++, obtenerOrganizacion(f.path(ID_ORGANIZACION)));
                        createCell(row, c++, formatearFecha(f.path(ID_PRIMERA_RESP).asText()));

                        // 2. CLASIFICACIÓN
                        createCell(row, c++, obtenerValor(f.path(ID_CAT_SERVICIO)));
                        createCell(row, c++, obtenerValor(f.path(ID_CANAL_COMUNIC)));
                        createCell(row, c++, obtenerValor(f.path(ID_MEDIO_TX)));
                        createCell(row, c++, obtenerValor(f.path(ID_ITEM)));

                        // 3. UBICACIÓN / IE
                        createCell(row, c++, obtenerValor(f.path(ID_COD_LOCAL)));
                        createCell(row, c++, obtenerValor(f.path(ID_COD_MODULAR)));
                        createCell(row, c++, obtenerValor(f.path(ID_NOMBRE_IE)));
                        createCell(row, c++, obtenerValor(f.path(ID_DEPARTAMENTO)));
                        createCell(row, c++, obtenerValor(f.path(ID_PROVINCIA)));
                        createCell(row, c++, obtenerValor(f.path(ID_DISTRITO)));
                        createCell(row, c++, obtenerValor(f.path(ID_DIRECCION)));
                        createCell(row, c++, obtenerValor(f.path(ID_AREA)));

                        // 4. CONTACTO
                        createCell(row, c++, obtenerValor(f.path(ID_NOMBRE_CONTACTO)));
                        createCell(row, c++, obtenerValor(f.path(ID_NUMERO_CONTACTO)));

                        // 5. FECHAS COMPARATIVAS
                        createCell(row, c++, formatearFecha(f.path("created").asText()));
                        createCell(row, c++, formatearFecha(f.path(ID_FECHA_GEN_MANUAL).asText()));
                        createCell(row, c++, formatearFecha(f.path("resolutiondate").asText()));
                        createCell(row, c++, formatearFecha(f.path(ID_FECHA_SOL_MANUAL).asText()));

                        // 6. TIEMPOS Y NARRATIVA
                        createCell(row, c++, obtenerValor(f.path(ID_TIEMPO_SOLUCION)));
                        createCell(row, c++, obtenerValor(f.path(ID_DISPONIBILIDAD)));
                        createCell(row, c++, parseADF(f.path(ID_DESCRIP_INCID)));
                        createCell(row, c++, parseADF(f.path(ID_DESCRIP_SOLUC)));
                    }

                    nextPageToken = root.path("nextPageToken").asText(null);
                    hasMore = nextPageToken != null && !issues.isEmpty();
                    System.out.println(">> Procesados: " + (rowIdx - 4));
                    Thread.sleep(100); 

                } else {
                    System.out.println("⚠️ Error " + response.statusCode() + ". Esperando 5s...");
                    Thread.sleep(5000);
                }
            }

            String fileName = "Reporte_Jira_FULL_SUMMARY_" + ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
            try (FileOutputStream out = new FileOutputStream(fileName)) {
                workbook.write(out);
            }
            workbook.dispose();
            System.out.println("✅ REPORTE GENERADO: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- UTILIDADES ---
    private static void createCell(Row row, int col, String val) {
        Cell c = row.createCell(col);
        c.setCellValue((val == null || val.equalsIgnoreCase("null")) ? "" : val);
    }

    private static String obtenerValor(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        if (node.has("value")) return node.get("value").asText();
        if (node.has("name")) return node.get("name").asText();
        return node.asText();
    }

    private static String obtenerOrganizacion(JsonNode node) {
        if (node.isArray() && node.size() > 0) return node.get(0).path("name").asText();
        return "";
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.length() < 16) return "";
        try { return dateStr.substring(0, 16).replace("T", " "); } catch(Exception e) { return dateStr; }
    }

    private static String parseADF(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = node.path("content");
            for (JsonNode block : content) {
                String type = block.path("type").asText();
                if ("paragraph".equals(type)) {
                    for (JsonNode t : block.path("content")) if (t.has("text")) sb.append(t.get("text").asText());
                    sb.append("\n");
                }
            }
        } catch (Exception e) { return ""; }
        return sb.toString().trim();
    }
}