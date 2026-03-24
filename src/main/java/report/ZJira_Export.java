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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Scanner;

public class ZJira_Export {

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
    private static final String ID_DESCRIP_INCIDENTE = "customfield_10180";
    private static final String ID_DESCRIP_SOLUCION= "customfield_10089";
    private static final String ID_MEDIO_TX        = "customfield_10361";
    private static final String ID_TIEMPO_NO_DISP  = "customfield_10178";

    // Fechas manuales
    private static final String ID_FECHA_GEN_MANUAL = "customfield_10321";
    private static final String ID_FECHA_SOL_MANUAL = "customfield_10322";

    public static void main(String[] args) {

        Scanner scanner = new Scanner(System.in);

        System.out.println("📅 Ingresa FECHA INICIO a extraer (Formato YYYY-MM-DD):");
        System.out.print(">> ");
        String fechaInicioStr = scanner.nextLine().trim();

        System.out.println("📅 Ingresa FECHA FIN a extraer (Formato YYYY-MM-DD):");
        System.out.print(">> ");
        String fechaFinStr = scanner.nextLine().trim();

        if (!fechaInicioStr.matches("\\d{4}-\\d{2}-\\d{2}") || !fechaFinStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.err.println("❌ Formato de fecha incorrecto. Debe ser YYYY-MM-DD");
            return;
        }

        LocalDate fechaInicio;
        LocalDate fechaFin;
        try {
            fechaInicio = LocalDate.parse(fechaInicioStr);
            fechaFin = LocalDate.parse(fechaFinStr);
        } catch (Exception e) {
            System.err.println("❌ Fecha inválida.");
            return;
        }

        if (fechaFin.isBefore(fechaInicio)) {
            System.err.println("❌ Rango inválido.");
            return;
        }

        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Reporte");

        String[] encabezados = {
                "Código de Local", "Código Modular", "Nombre de la IE", "Departamento",
                "Provincia", "Distrito", "Nombre de Contacto", "Número de Contacto",
                "Ítem al que pertenece", "Clave", "Tipo de Incidencia", "Estado",
                "Descripción del incidente",
                "Fecha y hora de generación",
                "Fecha y hora de solución",
                "Descripción de la solución",
                "Tiempo solución", "Tiempo de no disponibilidad", "Medio de transmisión"
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

        String jqlQuery = String.format(
                "project = 'MSP' AND created >= '%s 00:00' AND created <= '%s 23:59' ORDER BY created DESC",
                fechaInicioStr, fechaFinStr
        );

        try {
            while (seguirBuscando) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", jqlQuery);
                payload.put("maxResults", 50);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                ArrayNode fields = payload.putArray("fields");
                fields.add("status").add("created").add("resolutiondate")
                        .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                        .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO)
                        .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
                        .add(ID_ITEM).add(ID_TIPO_INCIDENCIA).add(ID_DESCRIP_INCIDENTE)
                        .add(ID_DESCRIP_SOLUCION).add(ID_MEDIO_TX).add(ID_TIEMPO_NO_DISP)
                        .add(ID_FECHA_GEN_MANUAL).add(ID_FECHA_SOL_MANUAL);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode issues = root.path("issues");
                    if (issues.isEmpty()) break;

                    for (JsonNode issue : issues) {
                        JsonNode f = issue.path("fields");
                        Row row = sheet.createRow(filaActual++);
                        int c = 0;

                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_COD_LOCAL)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_COD_MODULAR)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NOMBRE_IE)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_DEPARTAMENTO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_PROVINCIA)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_DISTRITO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NOMBRE_CONTACTO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NUMERO_CONTACTO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_ITEM)));
                        row.createCell(c++).setCellValue(issue.path("key").asText());
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIPO_INCIDENCIA)));
                        row.createCell(c++).setCellValue(f.path("status").path("name").asText());
                        row.createCell(c++).setCellValue(extraerTextoADF(f.path(ID_DESCRIP_INCIDENTE)));

                        String fechaSistemaGen = f.path("created").asText(null);
                        String fechaManualGen = f.path(ID_FECHA_GEN_MANUAL).asText(null);
                        String fechaUsoGen = (fechaManualGen != null && !fechaManualGen.equals("null") && !fechaManualGen.isBlank())
                                ? fechaManualGen : fechaSistemaGen;

                        String fechaSistemaSol = f.path("resolutiondate").asText(null);
                        String fechaManualSol = f.path(ID_FECHA_SOL_MANUAL).asText(null);
                        String fechaUsoSol = (fechaManualSol != null && !fechaManualSol.equals("null") && !fechaManualSol.isBlank())
                                ? fechaManualSol : fechaSistemaSol;

                        row.createCell(c++).setCellValue(formatearFecha(fechaUsoGen));
                        row.createCell(c++).setCellValue(formatearFecha(fechaUsoSol));

                        // ✅ CAMBIO CLAVE: la solución suele venir en ADF (doc), no como texto plano
                        row.createCell(c++).setCellValue(extraerTextoADF(f.path(ID_DESCRIP_SOLUCION)));

                        row.createCell(c++).setCellValue(calcularTiempo(fechaUsoGen, fechaUsoSol));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIEMPO_NO_DISP)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_MEDIO_TX)));
                    }

                    if (root.has("nextPageToken")) nextPageToken = root.get("nextPageToken").asText();
                    else seguirBuscando = false;

                } else {
                    System.err.println("❌ Error: " + response.body());
                    break;
                }
            }

            String nombreArchivo = "Reporte_Jira_Minedu_" + fechaInicioStr + "_a_" + fechaFinStr + "_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss")) + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(nombreArchivo)) {
                workbook.write(out);
            }
            workbook.close();

            System.out.println("✅ Generado: " + nombreArchivo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static String extraerTextoADF(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText(); // por si viene plano
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = node.path("content");
            for (JsonNode block : content) {
                for (JsonNode item : block.path("content")) {
                    if (item.has("text")) sb.append(item.get("text").asText());
                    else if ("hardBreak".equals(item.path("type").asText())) sb.append("\n");
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            // Si no se puede parsear, devolvemos JSON para no perder información
            return node.toString();
        }
        return sb.toString().trim();
    }

    private static String obtenerTexto(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.has("value")) return node.get("value").asText("");
        if (node.has("name")) return node.get("name").asText("");
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
        if (startStr == null || startStr.isBlank() || endStr == null || endStr.isBlank() || "null".equals(endStr)) return "En curso";
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
        if (dateStr == null || dateStr.isEmpty() || "null".equals(dateStr)) return "";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            return zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) {
            return dateStr;
        }
    }
}