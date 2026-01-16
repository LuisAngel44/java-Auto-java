package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class Jira_Full {

    // =========================================================================
    // ⚙️ CONFIGURACIÓN DE IDs (ACTUALIZADO CON TU LISTA REAL)
    // =========================================================================
    
    // Datos del Local
    private static final String ID_COD_LOCAL       = "customfield_10168"; // Código de Local del Local Educativo
    private static final String ID_COD_MODULAR     = "customfield_10169"; // Código Modular
    private static final String ID_NOMBRE_IE       = "customfield_10359"; // Nombre de la Institución Educativa
    
    // Ubicación
    private static final String ID_DEPARTAMENTO    = "customfield_10355"; // Departamento
    private static final String ID_PROVINCIA       = "customfield_10356"; // Provincia
    private static final String ID_DISTRITO        = "customfield_10357"; // Distrito
    
    // Contacto
    private static final String ID_NOMBRE_CONTACTO = "customfield_10090"; // Nombre de Contacto
    private static final String ID_NUMERO_CONTACTO = "customfield_10091"; // Número de Contacto

    // Datos Técnicos / Ticket
    private static final String ID_ITEM            = "customfield_10249"; // Ítem al que pertenece
    private static final String ID_TIPO_INCIDENCIA = "customfield_10469"; // Tipo de Incidencia
    private static final String ID_DESCRIP_SOLUCION= "customfield_10089"; // Descripción de la solución
    private static final String ID_MEDIO_TX        = "customfield_10361"; // Medio de transmisión implementado
    private static final String ID_TIEMPO_NO_DISP  = "customfield_10178"; // Tiempo de no disponibilidad efectiva
    
    // Campos Estándar de Sistema (No requieren ID numérico)
    private static final String FIELD_KEY = "key";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_SUMMARY = "summary";
    private static final String FIELD_CREATED = "created";
    private static final String FIELD_RESOLUTION_DATE = "resolutiondate";
    // =========================================================================

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Reporte Bitel Minedu");

        String[] encabezados = {
            "Código de Local", "Código Modular", "Nombre de la IE", "Departamento", 
            "Provincia", "Distrito", "Nombre de Contacto", "Número de Contacto", 
            "Ítem", "Clave", "Tipo de Incidencia", "Estado", 
            "Descripción", "Fecha Generación", "Fecha Solución", 
            "Descripción Solución", "Tiempo solución", "No disponibilidad", "Medio transmisión"
        };

        // Estilos para el encabezado (Negrita)
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < encabezados.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(encabezados[i]);
            cell.setCellStyle(headerStyle);
        }

        String nextPageToken = null;
        boolean seguirBuscando = true;
        int filaActual = 1;

        System.out.println("🚀 Iniciando generación de reporte con IDs reales...");

        try {
            while (seguirBuscando) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", "project = 'MSP' ORDER BY created DESC");
                payload.put("maxResults", 100);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                // Solicitamos campos a la API
                ArrayNode fields = payload.putArray("fields");
                fields.add(FIELD_SUMMARY).add(FIELD_STATUS).add(FIELD_CREATED).add(FIELD_RESOLUTION_DATE)
                      .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                      .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO)
                      .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
                      .add(ID_ITEM).add(ID_TIPO_INCIDENCIA)
                      .add(ID_DESCRIP_SOLUCION).add(ID_MEDIO_TX).add(ID_TIEMPO_NO_DISP);

                String jsonBody = mapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode issues = root.path("issues");

                    if (issues.isEmpty()) break;

                    for (JsonNode issue : issues) {
                        JsonNode f = issue.path("fields");
                        Row row = sheet.createRow(filaActual++);

                        // 1. Código de Local
                        row.createCell(0).setCellValue(obtenerTexto(f.path(ID_COD_LOCAL)));
                        // 2. Código Modular
                        row.createCell(1).setCellValue(obtenerTexto(f.path(ID_COD_MODULAR)));
                        // 3. Nombre de la IE
                        row.createCell(2).setCellValue(obtenerTexto(f.path(ID_NOMBRE_IE)));
                        // 4. Departamento
                        row.createCell(3).setCellValue(obtenerTexto(f.path(ID_DEPARTAMENTO)));
                        // 5. Provincia
                        row.createCell(4).setCellValue(obtenerTexto(f.path(ID_PROVINCIA)));
                        // 6. Distrito
                        row.createCell(5).setCellValue(obtenerTexto(f.path(ID_DISTRITO)));
                        
                        // 7. Nombre de Contacto
                        row.createCell(6).setCellValue(obtenerTexto(f.path(ID_NOMBRE_CONTACTO)));
                        // 8. Número de Contacto
                        row.createCell(7).setCellValue(obtenerTexto(f.path(ID_NUMERO_CONTACTO)));
                        
                        // 9. Ítem (Ahora usamos el campo real 10249)
                        row.createCell(8).setCellValue(obtenerTexto(f.path(ID_ITEM))); 

                        // 10. Clave (MSP-XXX)
                        row.createCell(9).setCellValue(issue.path("key").asText());
                        // 11. Tipo de Incidencia
                        row.createCell(10).setCellValue(obtenerTexto(f.path(ID_TIPO_INCIDENCIA)));
                        // 12. Estado
                        row.createCell(11).setCellValue(f.path("status").path("name").asText());
                        // 13. Descripción (Summary)
                        row.createCell(12).setCellValue(f.path("summary").asText());
                        
                        // Fechas
                        String fechaCreacion = f.path("created").asText();
                        String fechaSolucion = f.path("resolutiondate").asText(null);
                        
                        // 14. Fecha Generación
                        row.createCell(13).setCellValue(formatearFecha(fechaCreacion));
                        // 15. Fecha Solución
                        row.createCell(14).setCellValue(formatearFecha(fechaSolucion));

                        // 16. Descripción Solución
                        row.createCell(15).setCellValue(obtenerTexto(f.path(ID_DESCRIP_SOLUCION)));

                        // 17. Tiempo Solución (Calculado matemáticamente para exactitud)
                        row.createCell(16).setCellValue(calcularTiempo(fechaCreacion, fechaSolucion));

                        // 18. No disponibilidad (Usamos el campo real 10178)
                        row.createCell(17).setCellValue(obtenerTexto(f.path(ID_TIEMPO_NO_DISP)));
                        
                        // 19. Medio transmisión
                        row.createCell(18).setCellValue(obtenerTexto(f.path(ID_MEDIO_TX)));
                    }

                    if (root.has("nextPageToken")) {
                        nextPageToken = root.get("nextPageToken").asText();
                    } else {
                        seguirBuscando = false;
                    }
                    System.out.println("✅ Filas procesadas: " + (filaActual - 1));
                } else {
                    System.err.println("❌ Error: " + response.body());
                    break;
                }
            }

            // Autoajustar ancho de columnas
            for (int i = 0; i < encabezados.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fileOut = new FileOutputStream("Reporte_Jira_Minedu_Final.xlsx")) {
                workbook.write(fileOut);
            }
            workbook.close();
            System.out.println("🎉 ¡Excel generado con éxito!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static String obtenerTexto(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        
        // Si tiene 'value' (Listas desplegables)
        if (node.has("value")) {
            String val = node.get("value").asText();
            // Si tiene 'child' (Listas en cascada como Dep/Prov)
            if (node.has("child") && node.get("child").has("value")) {
                return val + " - " + node.get("child").get("value").asText();
            }
            return val;
        }
        
        // Si es un Array (Ej. Etiquetas o múltiples opciones)
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : node) {
                if (sb.length() > 0) sb.append(", ");
                if (n.has("value")) sb.append(n.get("value").asText());
                else sb.append(n.asText());
            }
            return sb.toString();
        }

        return node.asText("");
    }

    private static String calcularTiempo(String startStr, String endStr) {
        if (startStr == null || endStr == null || endStr.isEmpty()) return "En curso";
        try {
            ZonedDateTime start = ZonedDateTime.parse(startStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            ZonedDateTime end = ZonedDateTime.parse(endStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            Duration duration = Duration.between(start, end);
            long hours = duration.toHours();
            long minutes = duration.toMinutesPart();
            return hours + "h " + minutes + "m";
        } catch (Exception e) {
            return "-";
        }
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            return zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return dateStr;
        }
    }
}