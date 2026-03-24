package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Z_Jira_Export_Completo_Rapido {

    private static final String PROJECT_KEY = "MSP";
    private static final int PAGE_SIZE = 50;

    // Espera por página (para estabilidad). 0 si quieres máximo speed.
    private static final int SLEEP_MS_PER_PAGE = 150;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("📅 Ingresa FECHA INICIO a extraer (Formato YYYY-MM-DD):");
        System.out.print(">> ");
        String fechaInicioStr = scanner.nextLine().trim();

        System.out.println("📅 Ingresa FECHA FIN a extraer (Formato YYYY-MM-DD):");
        System.out.print(">> ");
        String fechaFinStr = scanner.nextLine().trim();

        if (!fechaInicioStr.matches("\\d{4}-\\d{2}-\\d{2}") || !fechaFinStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            System.err.println("❌ Formato incorrecto. Debe ser YYYY-MM-DD");
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
            System.err.println("❌ Rango inválido: FECHA FIN no puede ser menor que FECHA INICIO.");
            return;
        }

        Dotenv dotenv = Dotenv.load();
        String jiraUrl = safe(dotenv.get("JIRA_URL")).trim().replaceAll("/$", "");
        String email = safe(dotenv.get("JIRA_EMAIL")).trim();
        String token = safe(dotenv.get("JIRA_TOKEN")).trim();

        if (jiraUrl.isEmpty() || email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ ERROR: Revisa tu .env (JIRA_URL, JIRA_EMAIL, JIRA_TOKEN).");
            return;
        }

        String auth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newBuilder().build();

        String jqlQuery = String.format(
                "project = '%s' AND created >= '%s 00:00' AND created <= '%s 23:59' ORDER BY created DESC",
                PROJECT_KEY, fechaInicioStr, fechaFinStr
        );

        System.out.println("🚀 EXTRACCIÓN RÁPIDA (SEARCH *all)");
        System.out.println("   Rango: " + fechaInicioStr + " 00:00 a " + fechaFinStr + " 23:59");
        System.out.println("   JQL: " + jqlQuery);
        System.out.println("   PAGE_SIZE: " + PAGE_SIZE + " (≈ " + (int)Math.ceil(5000.0 / PAGE_SIZE) + " páginas si fueran 5000 tickets)");

        // 1) Mapa id->name (1 request)
        Map<String, String> fieldIdToName = fetchFieldIdToName(client, mapper, jiraUrl, auth);

        // 2) Workbook
        SXSSFWorkbook workbook = new SXSSFWorkbook(200);
        Sheet sheet = workbook.createSheet("Reporte FULL");
        LocalDateTime ahora = LocalDateTime.now();

        // Metadata
        CellStyle metaStyle = workbook.createCellStyle();
        Font metaFont = workbook.createFont();
        metaFont.setBold(true);
        metaFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        metaStyle.setFont(metaFont);

        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("FECHA EXTRACCIÓN:");
        r0.createCell(1).setCellValue(ahora.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        r0.getCell(0).setCellStyle(metaStyle);

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("RANGO:");
        r1.createCell(1).setCellValue(fechaInicioStr + " a " + fechaFinStr);
        r1.getCell(0).setCellStyle(metaStyle);

        Row r2 = sheet.createRow(2);
        r2.createCell(0).setCellValue("JQL UTILIZADO:");
        r2.createCell(1).setCellValue(jqlQuery);
        r2.getCell(0).setCellStyle(metaStyle);

        // 3) Leer páginas y guardar filas + union de campos
        List<Map<String, String>> rows = new ArrayList<>(6000);
        LinkedHashSet<String> allFieldIds = new LinkedHashSet<>();

        List<String> baseFieldIds = Arrays.asList(
                "key", "summary", "status", "priority", "issuetype",
                "reporter", "assignee", "created", "updated", "resolutiondate"
        );
        allFieldIds.addAll(baseFieldIds);

        String nextPageToken = null;
        boolean hasMore = true;
        int total = 0;
        int pageCount = 0;

        try {
            while (hasMore) {
                pageCount++;

                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", jqlQuery);
                payload.put("maxResults", PAGE_SIZE);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                payload.putArray("fields").add("*all");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    System.err.println("❌ Error Search (" + response.statusCode() + "): " + response.body());
                    break;
                }

                JsonNode root = mapper.readTree(response.body());
                JsonNode issues = root.path("issues");
                total = Math.max(total, root.path("total").asInt(0));

                for (JsonNode issue : issues) {
                    Map<String, String> oneRow = new HashMap<>();
                    oneRow.put("key", issue.path("key").asText(""));

                    JsonNode fields = issue.path("fields");
                    if (fields.isObject()) {
                        Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                        while (it.hasNext()) {
                            Map.Entry<String, JsonNode> e = it.next();
                            String fid = e.getKey();
                            allFieldIds.add(fid);
                            oneRow.put(fid, stringifyFieldValue(e.getValue()));
                        }
                    }
                    rows.add(oneRow);
                }

                nextPageToken = root.path("nextPageToken").asText(null);
                hasMore = nextPageToken != null && issues.size() > 0;

                System.out.println(">> Página " + pageCount + " | Tickets leídos: " + rows.size() + " / Total Jira: " + total);

                if (SLEEP_MS_PER_PAGE > 0) Thread.sleep(SLEEP_MS_PER_PAGE);
            }

            if (rows.isEmpty()) {
                System.out.println("⚠️ No se encontraron tickets.");
                workbook.close();
                return;
            }

            // 4) Orden columnas
            List<String> otherFieldIds = new ArrayList<>();
            for (String fid : allFieldIds) if (!baseFieldIds.contains(fid)) otherFieldIds.add(fid);

            otherFieldIds.sort(Comparator.comparing(fid -> fieldIdToName.getOrDefault(fid, fid).toLowerCase(Locale.ROOT)));

            List<String> finalFieldOrder = new ArrayList<>();
            finalFieldOrder.addAll(baseFieldIds);
            finalFieldOrder.addAll(otherFieldIds);

            // 5) Header
            int headerRowIndex = 4;
            Row headerRow = sheet.createRow(headerRowIndex);

            CellStyle headerStyle = workbook.createCellStyle();
            Font hfont = workbook.createFont();
            hfont.setBold(true);
            hfont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(hfont);
            headerStyle.setFillForegroundColor(IndexedColors.ROYAL_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < finalFieldOrder.size(); i++) {
                String fid = finalFieldOrder.get(i);
                String name = fieldIdToName.getOrDefault(fid, fid);
                String headerText = name + " (" + fid + ")";

                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headerText);
                cell.setCellStyle(headerStyle);

                sheet.setColumnWidth(i, (headerText.toLowerCase().contains("summary")
                        || headerText.toLowerCase().contains("resumen")
                        || headerText.toLowerCase().contains("descrip")) ? 12000 : 4500);
            }

            sheet.createFreezePane(0, headerRowIndex + 1);
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex, 0, finalFieldOrder.size() - 1));

            // 6) Data
            int rowIdx = headerRowIndex + 1;
            int written = 0;
            for (Map<String, String> r : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < finalFieldOrder.size(); col++) {
                    String fid = finalFieldOrder.get(col);
                    createCell(row, col, r.getOrDefault(fid, ""));
                }
                written++;
                if (written % 500 == 0) System.out.println(">> Filas escritas: " + written);
            }

            // 7) Save
            String fileName = "Reporte_Jira_FULL_RAPIDO_" +
                    fechaInicioStr + "_a_" + fechaFinStr + "_" +
                    ahora.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(fileName)) {
                workbook.write(out);
            }
            workbook.dispose();

            System.out.println("✅ REPORTE GENERADO: " + fileName);
            System.out.println("✅ Tickets exportados: " + written);
            System.out.println("✅ Columnas exportadas: " + finalFieldOrder.size());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> fetchFieldIdToName(HttpClient client, ObjectMapper mapper, String jiraUrl, String auth) {
        Map<String, String> map = new HashMap<>();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/field"))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return map;

            JsonNode arr = mapper.readTree(res.body());
            if (arr.isArray()) {
                for (JsonNode f : arr) {
                    String id = f.path("id").asText("");
                    String name = f.path("name").asText("");
                    if (!id.isBlank() && !name.isBlank()) map.put(id, name);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return map;
    }

    private static String stringifyFieldValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";

        if (node.isTextual()) return node.asText();
        if (node.isNumber() || node.isBoolean()) return node.asText();

        // ADF
        if (node.isObject() && "doc".equalsIgnoreCase(node.path("type").asText()) && node.has("content")) {
            String txt = parseADFtoText(node);
            return txt.isEmpty() ? node.toString() : txt;
        }

        if (node.isObject()) {
            if (node.has("displayName")) return node.path("displayName").asText("");
            if (node.has("name")) return node.path("name").asText("");
            if (node.has("value")) return node.path("value").asText("");
            if (node.has("key")) return node.path("key").asText("");
            return node.toString();
        }

        if (node.isArray()) {
            if (node.size() == 0) return "";
            List<String> parts = new ArrayList<>();
            boolean allSimple = true;

            for (JsonNode n : node) {
                if (n == null || n.isNull() || n.isMissingNode()) continue;

                if (n.isTextual() || n.isNumber() || n.isBoolean()) {
                    parts.add(n.asText());
                } else if (n.isObject()) {
                    if (n.has("name")) parts.add(n.path("name").asText(""));
                    else if (n.has("value")) parts.add(n.path("value").asText(""));
                    else if (n.has("displayName")) parts.add(n.path("displayName").asText(""));
                    else { allSimple = false; break; }
                } else { allSimple = false; break; }
            }

            if (allSimple) {
                parts.removeIf(String::isBlank);
                return String.join(", ", parts);
            }
            return node.toString();
        }

        return node.asText("");
    }

    private static String parseADFtoText(JsonNode adf) {
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = adf.path("content");
            if (!content.isArray()) return "";
            for (JsonNode block : content) {
                JsonNode inner = block.path("content");
                if (inner.isArray()) {
                    for (JsonNode item : inner) {
                        String type = item.path("type").asText("");
                        if (item.has("text")) sb.append(item.get("text").asText());
                        else if ("hardBreak".equals(type)) sb.append("\n");
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString().trim();
    }

    private static void createCell(Row row, int col, String val) {
        Cell c = row.createCell(col);
        if (val == null || "null".equalsIgnoreCase(val)) val = "";
        c.setCellValue(val);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}