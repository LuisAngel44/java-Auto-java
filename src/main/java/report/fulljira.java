package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook; // Importante: Cambiado a SXSSF

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class fulljira {

    // MAPA DE CAMPOS SEGÚN MSP-22605
    private static final String ID_COD_LOCAL       = "customfield_10168";
    private static final String ID_COD_MODULAR     = "customfield_10169";
    private static final String ID_NOMBRE_IE       = "customfield_10359";
    private static final String ID_DEPARTAMENTO    = "customfield_10355";
    private static final String ID_PROVINCIA       = "customfield_10356";
    private static final String ID_DISTRITO        = "customfield_10357";
    private static final String ID_DIRECCION       = "customfield_10358";
    private static final String ID_AREA            = "customfield_10504";
    private static final String ID_NOMBRE_CONTACTO = "customfield_10090";
    private static final String ID_NUMERO_CONTACTO = "customfield_10091";
    private static final String ID_ITEM            = "customfield_10249";
    private static final String ID_DESCRIP_INCID   = "customfield_10180";
    
    // LAS 2 FECHAS DE GENERACIÓN Y 2 DE SOLUCIÓN
    private static final String ID_FECHA_GEN_MANUAL = "customfield_10321"; // Fecha manual reporte
    private static final String ID_FECHA_SOL_MANUAL = "customfield_10322"; // Fecha manual solución
    
    private static final String ID_DESCRIP_SOLUC   = "customfield_10089";
    private static final String ID_TIEMPO_SOLUCION = "customfield_10177";
    private static final String ID_DISPONIBILIDAD  = "customfield_10178";
    private static final String ID_MEDIO_TX        = "customfield_10361";
    private static final String ID_CANAL_COMUNIC   = "customfield_10286";
    private static final String ID_CAT_SERVICIO    = "customfield_10394";

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String auth = Base64.getEncoder().encodeToString((dotenv.get("JIRA_EMAIL") + ":" + dotenv.get("JIRA_TOKEN")).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newBuilder().build();
        
        // USO DE SXSSF: Mantiene solo 100 filas en memoria RAM, el resto va al disco duro temporalmente
        SXSSFWorkbook workbook = new SXSSFWorkbook(100); 
        Sheet sheet = workbook.createSheet("Reporte Total Minedu");

        // ENCABEZADOS EXTENDIDOS
        String[] headers = {
            "Clave Ticket", "Estado", "Categoría Servicio", "Canal Comunicación", "Ítem", 
            "Cód. Local", "Cód. Modular", "Nombre IE", "Departamento", "Provincia", "Distrito", "Dirección", "Área",
            "Contacto", "Celular", "Descripción Incidente", 
            "F. Generación (SISTEMA)", "F. Generación (REPORTE)", 
            "F. Solución (SISTEMA)", "F. Solución (REPORTE)", 
            "Descripción Solución", "Tiempo Solución", "Tiempo No Disponibilidad", "Medio Transmisión"
        };

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont(); font.setBold(true);
        headerStyle.setFont(font);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        String nextPageToken = null;
        boolean hasMore = true;

        System.out.println("🚀 Iniciando descarga masiva de Jira...");

        try {
            while (hasMore) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", "project = 'MSP' ORDER BY created DESC");
                payload.put("maxResults", 50);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);
                
                ArrayNode fields = payload.putArray("fields");
                fields.add("status").add("created").add("resolutiondate")
                      .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                      .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO).add(ID_DIRECCION)
                      .add(ID_AREA).add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO).add(ID_ITEM)
                      .add(ID_DESCRIP_INCID).add(ID_FECHA_GEN_MANUAL).add(ID_FECHA_SOL_MANUAL)
                      .add(ID_DESCRIP_SOLUC).add(ID_TIEMPO_SOLUCION).add(ID_DISPONIBILIDAD)
                      .add(ID_MEDIO_TX).add(ID_CANAL_COMUNIC).add(ID_CAT_SERVICIO);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                // LÓGICA DE REINTENTOS PARA EVITAR 'CONNECTION RESET'
                int maxRetries = 3;
                int retryCount = 0;
                boolean success = false;

                while (!success && retryCount < maxRetries) {
                    try {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        
                        if (response.statusCode() == 429) {
                            System.out.println("⚠️ Jira pide frenar (HTTP 429). Esperando 5 segundos...");
                            Thread.sleep(5000);
                            retryCount++;
                            continue;
                        }

                        JsonNode root = mapper.readTree(response.body());
                        JsonNode issues = root.path("issues");

                        for (JsonNode issue : issues) {
                            JsonNode f = issue.path("fields");
                            Row row = sheet.createRow(rowIdx++);

                            row.createCell(0).setCellValue(issue.path("key").asText());
                            row.createCell(1).setCellValue(f.path("status").path("name").asText());
                            row.createCell(2).setCellValue(obtenerValor(f.path(ID_CAT_SERVICIO)));
                            row.createCell(3).setCellValue(obtenerValor(f.path(ID_CANAL_COMUNIC)));
                            row.createCell(4).setCellValue(obtenerValor(f.path(ID_ITEM)));
                            row.createCell(5).setCellValue(obtenerValor(f.path(ID_COD_LOCAL)));
                            row.createCell(6).setCellValue(obtenerValor(f.path(ID_COD_MODULAR)));
                            row.createCell(7).setCellValue(obtenerValor(f.path(ID_NOMBRE_IE)));
                            row.createCell(8).setCellValue(obtenerValor(f.path(ID_DEPARTAMENTO)));
                            row.createCell(9).setCellValue(obtenerValor(f.path(ID_PROVINCIA)));
                            row.createCell(10).setCellValue(obtenerValor(f.path(ID_DISTRITO)));
                            row.createCell(11).setCellValue(obtenerValor(f.path(ID_DIRECCION)));
                            row.createCell(12).setCellValue(obtenerValor(f.path(ID_AREA)));
                            row.createCell(13).setCellValue(obtenerValor(f.path(ID_NOMBRE_CONTACTO)));
                            row.createCell(14).setCellValue(obtenerValor(f.path(ID_NUMERO_CONTACTO)));
                            row.createCell(15).setCellValue(parseADF(f.path(ID_DESCRIP_INCID)));
                            
                            // LAS 4 FECHAS
                            row.createCell(16).setCellValue(formatearFecha(f.path("created").asText()));
                            row.createCell(17).setCellValue(formatearFecha(f.path(ID_FECHA_GEN_MANUAL).asText()));
                            row.createCell(18).setCellValue(formatearFecha(f.path("resolutiondate").asText()));
                            row.createCell(19).setCellValue(formatearFecha(f.path(ID_FECHA_SOL_MANUAL).asText()));
                            
                            row.createCell(20).setCellValue(parseADF(f.path(ID_DESCRIP_SOLUC)));
                            row.createCell(21).setCellValue(obtenerValor(f.path(ID_TIEMPO_SOLUCION)));
                            row.createCell(22).setCellValue(obtenerValor(f.path(ID_DISPONIBILIDAD)));
                            row.createCell(23).setCellValue(obtenerValor(f.path(ID_MEDIO_TX)));
                        }

                        nextPageToken = root.path("nextPageToken").asText(null);
                        hasMore = nextPageToken != null && !issues.isEmpty();
                        System.out.println(">>> Descargados " + (rowIdx - 1) + " registros...");
                        
                        success = true; // Éxito, salimos del bucle de reintentos
                        
                        // Pausa muy ligera para estabilizar las peticiones a la API
                        Thread.sleep(100); 

                    } catch (Exception e) {
                        retryCount++;
                        System.out.println("⚠️ Error temporal de red (Intento " + retryCount + "): " + e.getMessage());
                        if (retryCount >= maxRetries) {
                            System.out.println("❌ Fallo definitivo en este bloque. Procediendo a guardar los " + (rowIdx - 1) + " registros obtenidos...");
                            hasMore = false; // Cortamos para guardar lo que tenemos
                        } else {
                            Thread.sleep(3000 * retryCount); // Espera progresiva antes de reintentar
                        }
                    }
                }
            }

            // Nota: Con SXSSF autoSizeColumn consume muchísima memoria porque debe leer todo de nuevo.
            // Para reportes masivos de miles de filas, es mejor omitirlo para que termine rápido.
            // for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            String fileName = "Data_Maestra_Jira_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";
            try (FileOutputStream out = new FileOutputStream(fileName)) {
                workbook.write(out);
            }
            
            // Limpia los archivos temporales creados por SXSSFWorkbook en el disco duro
            workbook.dispose(); 
            
            System.out.println("✅ ¡REPORTE TOTAL COMPLETADO CON ÉXITO!: " + fileName);

        } catch (Exception e) {
            System.out.println("❌ Ocurrió un error inesperado al generar el archivo Excel:");
            e.printStackTrace();
        }
    }

    private static String obtenerValor(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        if (node.has("value")) return node.get("value").asText();
        return node.asText();
    }

    private static String parseADF(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = node.path("content");
            for (JsonNode block : content) {
                JsonNode innerContent = block.path("content");
                if (innerContent.isArray()) {
                    for (JsonNode item : innerContent) {
                        if (item.has("text")) sb.append(item.get("text").asText());
                        if ("hardBreak".equals(item.path("type").asText())) sb.append(" ");
                    }
                }
                sb.append(" | "); 
            }
        } catch (Exception e) { return ""; }
        return sb.toString().trim();
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equalsIgnoreCase("null")) return "";
        try {
            return dateStr.substring(0, 16).replace("T", " ");
        } catch (Exception e) { return dateStr; }
    }
}