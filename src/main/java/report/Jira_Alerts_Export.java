package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class Jira_Alerts_Export {

    public static void main(String[] args) {
        // Cargar variables de entorno
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", ""); 
        // Ej: https://tudominio.atlassian.net
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        
        // Autenticación Basic (Email:Token) funciona para el proxy de JSM Opsgenie
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Reporte Alertas");

        // 1. Definir Encabezados basados en tu imagen (ID, Priority, Summary, Status, Tags)
        String[] encabezados = {
            "ID Alerta (G#)", "Prioridad", "Estado", "Mensaje (Summary)", 
            "Tags", "Fecha Creación", "Fuente", "Alias"
        };

        // Estilos
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

        int filaActual = 1;
        int limit = 100; // Máximo permitido por Opsgenie
        int offset = 0;
        boolean seguirBuscando = true;

        System.out.println("🚀 Iniciando descarga de ALERTAS de Operaciones...");

        try {
            while (seguirBuscando) {
                // 2. Construcción de la URL para la API de Opsgenie (Alertas)
                // Nota: Usamos el endpoint proxy de JSM: /jsm/opsgenie/v2/alerts
                // Filtramos para ver alertas abiertas o cerradas. Si quieres todas, quita el param 'query'.
                // query=status:open (o status:closed, o dejar vacío para todo)
                
                String query = "status:open"; // Puedes cambiar a "" para traer todo
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

                String url = String.format("%s/jsm/opsgenie/v2/alerts?limit=%d&offset=%d&sort=createdAt&order=desc&query=%s", 
                                           jiraUrl, limit, offset, encodedQuery);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Content-Type", "application/json")
                        .GET() // Las alertas usan GET, no POST con JQL
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode data = root.path("data");

                    if (data.isEmpty()) {
                        seguirBuscando = false;
                        break;
                    }

                    for (JsonNode alert : data) {
                        Row row = sheet.createRow(filaActual++);

                        // A. ID (TinyId es el G0338 que ves en la foto)
                        row.createCell(0).setCellValue(alert.path("tinyId").asText());

                        // B. Prioridad (P1, P2, P3...)
                        row.createCell(1).setCellValue(alert.path("priority").asText());

                        // C. Estado (open, closed, acked)
                        row.createCell(2).setCellValue(alert.path("status").asText());

                        // D. Summary / Mensaje
                        row.createCell(3).setCellValue(alert.path("message").asText());

                        // E. Tags (Vienen como array, los unimos)
                        row.createCell(4).setCellValue(obtenerTags(alert.path("tags")));

                        // F. Fecha Creación
                        row.createCell(5).setCellValue(formatearFecha(alert.path("createdAt").asText()));

                        // G. Fuente (Source)
                        row.createCell(6).setCellValue(alert.path("source").asText());
                        
                        // H. Alias (ID interno único)
                        row.createCell(7).setCellValue(alert.path("alias").asText());
                    }

                    // Paginación: Opsgenie usa offset. Si devolvió menos del límite, acabamos.
                    if (data.size() < limit) {
                        seguirBuscando = false;
                    } else {
                        offset += limit;
                        System.out.println("🔄 Cargando siguiente página (Offset: " + offset + ")...");
                    }

                } else {
                    System.err.println("❌ Error " + response.statusCode() + ": " + response.body());
                    System.err.println("💡 PISTA: Si recibes 401/403, verifica si necesitas una 'Opsgenie API Key' en lugar del Token de Jira.");
                    seguirBuscando = false;
                }
            }

            // Autoajustar columnas
            for (int i = 0; i < encabezados.length; i++) sheet.autoSizeColumn(i);

            try (FileOutputStream fileOut = new FileOutputStream("Reporte_Alertas_JSM.xlsx")) {
                workbook.write(fileOut);
            }
            workbook.close();
            System.out.println("🎉 ¡Excel de ALERTAS generado con éxito! Filas: " + (filaActual - 1));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- Métodos Auxiliares ---

    private static String obtenerTags(JsonNode tagsNode) {
        if (tagsNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode t : tagsNode) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(t.asText());
            }
            return sb.toString();
        }
        return "";
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";
        try {
            // Opsgenie suele devolver ISO_INSTANT o ISO_OFFSET_DATE_TIME
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr);
            return zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return dateStr;
        }
    }
}