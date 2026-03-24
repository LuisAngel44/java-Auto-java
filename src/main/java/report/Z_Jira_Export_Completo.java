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

/**
 * Exportador "FULL" Jira:
 * - Pide FECHA INICIO y FECHA FIN (YYYY-MM-DD)
 * - Busca tickets con JQL usando created en rango
 * - Por cada ticket llama /rest/api/3/issue/{key}?expand=names,schema
 *   para obtener TODOS los fields y sus nombres.
 * - Genera Excel con columnas dinámicas (unión de campos encontrados).
 */
public class Z_Jira_Export_Completo {

    // Base JQL (puedes cambiar project, etc.)
    private static final String PROJECT_KEY = "MSP";

    // Jira Cloud maxResults típico 100 (a veces 100); dejamos 50 para estabilidad
    private static final int PAGE_SIZE = 50;

    public static void main(String[] args) {
        // =============== INPUT (RANGO) ===============
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
        // =============================================

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

        // =============== WORKBOOK ===============
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Sheet sheet = workbook.createSheet("Reporte FULL");

        LocalDateTime ahora = LocalDateTime.now();

        // --- Metadata header ---
        CellStyle metaStyle = workbook.createCellStyle();
        Font metaFont = workbook.createFont();
        metaFont.setBold(true);
        metaFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        metaStyle.setFont(metaFont);

        String jqlQuery = String.format(
                "project = '%s' AND created >= '%s 00:00' AND created <= '%s 23:59' ORDER BY created DESC",
                PROJECT_KEY, fechaInicioStr, fechaFinStr
        );

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

        // =============== EXTRACTION ===============
        System.out.println("🚀 INICIANDO EXTRACCIÓN FULL (TODOS LOS CAMPOS)...");
        System.out.println("   Rango: " + fechaInicioStr + " 00:00 a " + fechaFinStr + " 23:59");
        System.out.println("   JQL: " + jqlQuery);

        // 1) Traer lista de issue keys por paginación
        List<String> issueKeys = new ArrayList<>();
        String nextPageToken = null;
        boolean hasMore = true;

        try {
            while (hasMore) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", jqlQuery);
                payload.put("maxResults", PAGE_SIZE);
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                // Para listar keys es suficiente pedir campos mínimos
                payload.putArray("fields").add("key");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(jiraUrl + "/rest/api/3/search/jql"))
                        .header("Authorization", "Basic " + auth)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    System.err.println("❌ Error Search JQL (" + response.statusCode() + "): " + response.body());
                    return;
                }

                JsonNode root = mapper.readTree(response.body());
                JsonNode issues = root.path("issues");
                for (JsonNode issue : issues) {
                    String key = issue.path("key").asText();
                    if (key != null && !key.isBlank()) issueKeys.add(key);
                }

                nextPageToken = root.path("nextPageToken").asText(null);
                hasMore = nextPageToken != null && issues.size() > 0;

                System.out.println(">> Keys acumuladas: " + issueKeys.size());
                Thread.sleep(150);
            }

            if (issueKeys.isEmpty()) {
                System.out.println("⚠️ No se encontraron tickets en el rango indicado.");
                workbook.close();
                return;
            }

            // 2) Para "todos los campos": necesitamos saber TODAS las columnas (union de fields)
            // Haremos 2 pasadas:
            //  - pasada A: obtener names + fields por issue, guardar en memoria (map)
            //  - construir headers con union
            //  - pasada B: escribir filas
            //
            // Esto consume memoria, pero permite columnas consistentes.
            Map<String, Map<String, String>> issueFieldValues = new LinkedHashMap<>(); // key -> (fieldId -> valueAsText)
            Map<String, String> fieldIdToName = new LinkedHashMap<>(); // fieldId -> display name

            int count = 0;
            for (String key : issueKeys) {
                count++;
                System.out.println("🔎 Leyendo ticket (" + count + "/" + issueKeys.size() + "): " + key);

                IssueData data = fetchIssueAllFields(client, mapper, jiraUrl, auth, key);
                if (data == null) continue;

                // merge names
                for (Map.Entry<String, String> e : data.fieldIdToName.entrySet()) {
                    fieldIdToName.putIfAbsent(e.getKey(), e.getValue());
                }

                issueFieldValues.put(key, data.fieldIdToValue);

                Thread.sleep(120);
            }

            // 3) Construir headers (base + todos los fields)
            // Campos base recomendados (si existen)
            List<String> baseFieldIds = Arrays.asList(
                    "key",
                    "summary",
                    "status",
                    "priority",
                    "issuetype",
                    "reporter",
                    "assignee",
                    "created",
                    "updated",
                    "resolutiondate"
            );

            // Orden final de columnas:
            // - Base primero
            // - Luego el resto (custom/system) en orden alfabético por nombre
            Set<String> allFieldIds = new LinkedHashSet<>();
            allFieldIds.addAll(baseFieldIds);
            allFieldIds.addAll(fieldIdToName.keySet());

            // Quitar duplicados (ya es set), ahora separamos base vs otros
            List<String> otherFieldIds = new ArrayList<>();
            for (String fid : allFieldIds) {
                if (!baseFieldIds.contains(fid)) otherFieldIds.add(fid);
            }

            otherFieldIds.sort(Comparator.comparing(fid -> fieldIdToName.getOrDefault(fid, fid).toLowerCase(Locale.ROOT)));

            List<String> finalFieldOrder = new ArrayList<>();
            finalFieldOrder.addAll(baseFieldIds);
            finalFieldOrder.addAll(otherFieldIds);

            // 4) Escribir fila de headers (fila 4)
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
                // mostrar (ID) para que puedas identificar el customfield
                String headerText = name + " (" + fid + ")";
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headerText);
                cell.setCellStyle(headerStyle);

                sheet.setColumnWidth(i, (i == 1 || headerText.toLowerCase().contains("descrip")) ? 12000 : 4500);
            }

            sheet.createFreezePane(0, headerRowIndex + 1);
            sheet.setAutoFilter(new CellRangeAddress(headerRowIndex, headerRowIndex, 0, finalFieldOrder.size() - 1));

            // 5) Escribir data rows
            int rowIdx = headerRowIndex + 1;
            int written = 0;

            for (String issueKey : issueKeys) {
                Map<String, String> values = issueFieldValues.get(issueKey);
                if (values == null) continue;

                Row row = sheet.createRow(rowIdx++);
                for (int col = 0; col < finalFieldOrder.size(); col++) {
                    String fid = finalFieldOrder.get(col);
                    String val = values.getOrDefault(fid, "");
                    createCell(row, col, val);
                }

                written++;
                if (written % 50 == 0) {
                    System.out.println(">> Filas escritas: " + written);
                }
            }

            // 6) Guardar
            String fileName = "Reporte_Jira_FULL_CAMPOS_" +
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

    // ===================== FETCH ISSUE (ALL FIELDS) =====================

    private static class IssueData {
        Map<String, String> fieldIdToName = new LinkedHashMap<>();
        Map<String, String> fieldIdToValue = new LinkedHashMap<>();
    }

    /**
     * Obtiene TODOS los fields de un issue, además del mapa names (fieldId->nombre).
     * Usamos /rest/api/3/issue/{key}?expand=names,schema
     */
    private static IssueData fetchIssueAllFields(HttpClient client, ObjectMapper mapper, String jiraUrl, String auth, String issueKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "?expand=names,schema"))
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                System.err.println("   ⚠️ No se pudo leer " + issueKey + " (" + res.statusCode() + ")");
                return null;
            }

            JsonNode root = mapper.readTree(res.body());
            JsonNode names = root.path("names");   // mapa fieldId -> display name
            JsonNode fields = root.path("fields"); // todos los valores

            IssueData data = new IssueData();

            // fieldId->name
            if (names.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = names.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    data.fieldIdToName.put(e.getKey(), e.getValue().asText());
                }
            }

            // Guardar también algunos nombres base si faltaran
            data.fieldIdToName.putIfAbsent("key", "Clave");
            // summary/status etc normalmente vendrán en names, pero aseguramos
            data.fieldIdToName.putIfAbsent("summary", "Resumen");
            data.fieldIdToName.putIfAbsent("status", "Estado");
            data.fieldIdToName.putIfAbsent("priority", "Prioridad");
            data.fieldIdToName.putIfAbsent("issuetype", "Tipo");
            data.fieldIdToName.putIfAbsent("reporter", "Reportador");
            data.fieldIdToName.putIfAbsent("assignee", "Asignado");
            data.fieldIdToName.putIfAbsent("created", "Creado");
            data.fieldIdToName.putIfAbsent("updated", "Actualizado");
            data.fieldIdToName.putIfAbsent("resolutiondate", "Fecha de resolución");

            // fieldId->value
            // key no está dentro de fields; está en root
            data.fieldIdToValue.put("key", root.path("key").asText(""));

            if (fields.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> it = fields.fields();
                while (it.hasNext()) {
                    Map.Entry<String, JsonNode> e = it.next();
                    String fieldId = e.getKey();
                    JsonNode valueNode = e.getValue();
                    String valueText = stringifyFieldValue(valueNode);
                    data.fieldIdToValue.put(fieldId, valueText);
                }
            }

            return data;

        } catch (Exception e) {
            System.err.println("   ⚠️ Error leyendo issue " + issueKey + ": " + e.getMessage());
            return null;
        }
    }

    // ===================== STRINGIFIERS =====================

    /**
     * Convierte cualquier field value de Jira a texto para Excel:
     * - null => ""
     * - objetos típicos (con name/value/displayName) => extrae uno
     * - arrays => join por ", " cuando se puede; si no, JSON
     * - ADF (type=doc) => intenta extraer texto; si no, JSON
     */
    private static String stringifyFieldValue(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return "";

        // String/number/bool directos
        if (node.isTextual()) return node.asText();
        if (node.isNumber() || node.isBoolean()) return node.asText();

        // ADF (Atlassian Document Format)
        if (node.isObject() && "doc".equalsIgnoreCase(node.path("type").asText()) && node.has("content")) {
            String txt = parseADFtoText(node);
            return txt.isEmpty() ? node.toString() : txt;
        }

        // Objetos comunes de Jira: {name:..} o {value:..} o {displayName:..}
        if (node.isObject()) {
            if (node.has("displayName")) return node.path("displayName").asText("");
            if (node.has("name")) return node.path("name").asText("");
            if (node.has("value")) return node.path("value").asText("");
            if (node.has("key")) return node.path("key").asText("");

            // Organizations a veces viene como objeto/array con "name"
            // Si no reconoce, devolvemos JSON compacto
            return node.toString();
        }

        // Arrays: intentar sacar textos legibles
        if (node.isArray()) {
            if (node.size() == 0) return "";
            List<String> parts = new ArrayList<>();
            boolean allSimple = true;

            for (JsonNode n : node) {
                String s = "";
                if (n == null || n.isNull() || n.isMissingNode()) {
                    s = "";
                } else if (n.isTextual() || n.isNumber() || n.isBoolean()) {
                    s = n.asText();
                } else if (n.isObject()) {
                    if (n.has("name")) s = n.path("name").asText("");
                    else if (n.has("value")) s = n.path("value").asText("");
                    else if (n.has("displayName")) s = n.path("displayName").asText("");
                    else {
                        allSimple = false;
                        parts.add(n.toString());
                        continue;
                    }
                } else {
                    allSimple = false;
                    parts.add(n.toString());
                    continue;
                }
                if (!s.isBlank()) parts.add(s);
            }

            if (parts.isEmpty()) return "";
            if (allSimple) return String.join(", ", parts);

            // mixto/objeto complejo -> JSON
            return node.toString();
        }

        return node.asText("");
    }

    private static String parseADFtoText(JsonNode adf) {
        // Parser simple: recorre content y recoge "text" y hardBreak
        StringBuilder sb = new StringBuilder();
        try {
            JsonNode content = adf.path("content");
            if (!content.isArray()) return "";

            for (JsonNode block : content) {
                JsonNode inner = block.path("content");
                if (inner.isArray()) {
                    for (JsonNode item : inner) {
                        String type = item.path("type").asText("");
                        if (item.has("text")) {
                            sb.append(item.get("text").asText());
                        } else if ("hardBreak".equals(type)) {
                            sb.append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }
        } catch (Exception e) {
            return "";
        }
        return sb.toString().trim();
    }

    // ===================== EXCEL HELPERS =====================

    private static void createCell(Row row, int col, String val) {
        Cell c = row.createCell(col);
        if (val == null || "null".equalsIgnoreCase(val)) val = "";
        c.setCellValue(val);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}