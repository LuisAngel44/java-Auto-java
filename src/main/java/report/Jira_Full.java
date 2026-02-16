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

    // IDs de Campos
    private static final String ID_COD_LOCAL       = "customfield_10168";
    private static final String ID_COD_MODULAR     = "customfield_10169";
    private static final String ID_NOMBRE_IE       = "customfield_10359";
    private static final String ID_DEPARTAMENTO    = "customfield_10355";
    private static final String ID_PROVINCIA       = "customfield_10356";
    private static final String ID_DISTRITO        = "customfield_10357";
    private static final String ID_NOMBRE_CONTACTO = "customfield_10090";
    private static final String ID_NUMERO_CONTACTO = "customfield_10091";
    private static final String ID_ITEM            = "customfield_10249";
    private static final String ID_TIPO_INCIDENCIA = "customfield_10469";
    private static final String ID_DESCRIP_INCIDENTE = "customfield_10180"; // <--- Nuevo Campo
    private static final String ID_DESCRIP_SOLUCION= "customfield_10089";
    private static final String ID_MEDIO_TX        = "customfield_10361";
    private static final String ID_TIEMPO_NO_DISP  = "customfield_10178";

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
            "Ítem al que pertenece", "Clave", "Tipo de Incidencia", "Estado", 
            "Descripción del incidente", "Fecha y hora de generación", "Fecha y hora de solución", 
            "Descripción de la solución", "Tiempo solución", "Tiempo de no disponibilidad", "Medio de transmisión"
        };

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
        int totalIssues = 0;

        System.out.println("🚀 Iniciando generación de reporte...");

        try {
            while (seguirBuscando) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", "project = 'MSP' ORDER BY created DESC");
                payload.put("maxResults", 100);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                ArrayNode fields = payload.putArray("fields");
                fields.add("summary").add("status").add("created").add("resolutiondate")
                      .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                      .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO)
                      .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
                      .add(ID_ITEM).add(ID_TIPO_INCIDENCIA).add(ID_DESCRIP_INCIDENTE)
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
                    totalIssues = root.path("total").asInt();

                    if (issues.isEmpty()) break;

                    for (JsonNode issue : issues) {
                        JsonNode f = issue.path("fields");
                        Row row = sheet.createRow(filaActual++);

                        row.createCell(0).setCellValue(obtenerTexto(f.path(ID_COD_LOCAL)));
                        row.createCell(1).setCellValue(obtenerTexto(f.path(ID_COD_MODULAR)));
                        row.createCell(2).setCellValue(obtenerTexto(f.path(ID_NOMBRE_IE)));
                        row.createCell(3).setCellValue(obtenerTexto(f.path(ID_DEPARTAMENTO)));
                        row.createCell(4).setCellValue(obtenerTexto(f.path(ID_PROVINCIA)));
                        row.createCell(5).setCellValue(obtenerTexto(f.path(ID_DISTRITO)));
                        row.createCell(6).setCellValue(obtenerTexto(f.path(ID_NOMBRE_CONTACTO)));
                        row.createCell(7).setCellValue(obtenerTexto(f.path(ID_NUMERO_CONTACTO)));
                        row.createCell(8).setCellValue(obtenerTexto(f.path(ID_ITEM))); 
                        row.createCell(9).setCellValue(issue.path("key").asText());
                        row.createCell(10).setCellValue(obtenerTexto(f.path(ID_TIPO_INCIDENCIA)));
                        row.createCell(11).setCellValue(f.path("status").path("name").asText());
                        
                        // 13. Descripción del incidente (Manejo de ADF)
                        row.createCell(12).setCellValue(extraerTextoADF(f.path(ID_DESCRIP_INCIDENTE)));
                        
                        String fechaCreacion = f.path("created").asText();
                        String fechaSolucion = f.path("resolutiondate").asText(null);
                        
                        row.createCell(13).setCellValue(formatearFecha(fechaCreacion));
                        row.createCell(14).setCellValue(formatearFecha(fechaSolucion));
                        row.createCell(15).setCellValue(obtenerTexto(f.path(ID_DESCRIP_SOLUCION)));
                        row.createCell(16).setCellValue(calcularTiempo(fechaCreacion, fechaSolucion));
                        row.createCell(17).setCellValue(obtenerTexto(f.path(ID_TIEMPO_NO_DISP)));
                        row.createCell(18).setCellValue(obtenerTexto(f.path(ID_MEDIO_TX)));

                        mostrarProgreso(filaActual - 1, totalIssues);
                    }

                    if (root.has("nextPageToken")) {
                        nextPageToken = root.get("nextPageToken").asText();
                    } else {
                        seguirBuscando = false;
                    }
                } else {
                    System.err.println("❌ Error: " + response.body());
                    break;
                }
            }

            for (int i = 0; i < encabezados.length; i++) sheet.autoSizeColumn(i);

            // Obtener fecha y hora actual para el nombre del archivo
            String fechaHora = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String nombreArchivo = "Reporte_Jira_Minedu_" + fechaHora + ".xlsx";

            try (FileOutputStream fileOut = new FileOutputStream(nombreArchivo)) {
                workbook.write(fileOut);
            }
            workbook.close();
            System.out.println("\n🎉 ¡Excel generado con éxito! Nombre: " + nombreArchivo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Procesa el JSON de Jira (ADF) para convertirlo en texto plano para el Excel
    private static String extraerTextoADF(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = node.path("content");
            for (JsonNode block : content) {
                for (JsonNode item : block.path("content")) {
                    if (item.has("text")) {
                        sb.append(item.get("text").asText());
                    } else if ("hardBreak".equals(item.path("type").asText())) {
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            return node.toString(); // Fallback si falla el parseo
        }
        return sb.toString().trim();
    }

    private static void mostrarProgreso(int actual, int total) {
        int width = 50; 
        double porcentaje = (double) actual / total;
        int progress = (int) (porcentaje * width);

        StringBuilder bar = new StringBuilder("\r[");
        for (int i = 0; i < width; i++) {
            if (i < progress) bar.append("=");
            else if (i == progress) bar.append(">");
            else bar.append(" ");
        }
        bar.append(String.format("] %d%% (%d/%d)", (int) (porcentaje * 100), actual, total));
        System.out.print(bar.toString());
    }

    private static String obtenerTexto(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) return "";
        if (node.has("value")) {
            String val = node.get("value").asText();
            if (node.has("child") && node.get("child").has("value")) {
                return val + " - " + node.get("child").get("value").asText();
            }
            return val;
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : node) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(n.has("value") ? n.get("value").asText() : n.asText());
            }
            return sb.toString();
        }
        return node.asText("");
    }

    private static String calcularTiempo(String startStr, String endStr) {
        if (startStr == null || endStr == null || endStr.isEmpty() || endStr.equals("null")) return "En curso";
        try {
            ZonedDateTime start = ZonedDateTime.parse(startStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            ZonedDateTime end = ZonedDateTime.parse(endStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            Duration duration = Duration.between(start, end);
            return String.format("%dh %dm", duration.toHours(), duration.toMinutesPart());
        } catch (Exception e) {
            return "-";
        }
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || dateStr.equals("null")) return "";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            return zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return dateStr;
        }
    }
}