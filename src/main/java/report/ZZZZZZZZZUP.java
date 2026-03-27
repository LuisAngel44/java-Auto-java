package report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXCEL -> JIRA (actualiza solo celdas llenas)
 * Reglas:
 * - Ticket Key vacío => omite fila
 * - Celda vacía => NO actualiza ese campo
 * - Celda = BORRAR => borra el campo en Jira
 *
 * Fechas:
 * - En Excel: dd/MM/yyyy HH:mm:ss (también soporta dd/MM/yyyy HH:mm y dd/MM/yyyy)
 * - En Jira: yyyy-MM-ddTHH:mm:ss.000-0500 (se respeta el mismo valor día/mes/hora)
 */
public class ZZZZZZZZZUP {

    // ========= CONFIG =========
    static final String EXCEL_FILE = "DDDFINAL11.xlsx";
    static final String PALABRA_BORRAR = "BORRAR";
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";

    // ========= JIRA FIELD IDS =========
    static final String FIELD_DESC_INCIDENTE = "customfield_10180";
    static final String FIELD_SOLUCION       = "customfield_10089";
    static final String FIELD_CATEGORIA      = "customfield_10394";
    static final String FIELD_CAUSA_RAIZ     = "customfield_10135";
    static final String FIELD_FECHA_GEN      = "customfield_10321";
    static final String FIELD_FECHA_SOL      = "customfield_10322";
    static final String FIELD_TIEMPO_SOL     = "customfield_10177";
    static final String FIELD_TIEMPO_NODISP  = "customfield_10178";
    static final String FIELD_IMPUTABILIDAD  = "customfield_10471";
    static final String FIELD_TIPO_INCIDENCIA= "customfield_10469";
    static final String FIELD_MEDIO_TX       = "customfield_10361";

    // Assets / Objects
    static final String FIELD_IE_ASSET        = "customfield_10170"; // Institución Educativa (Assets)
    static final String FIELD_AFFECTED_ITEM1  = "customfield_10250";
    static final String FIELD_AFFECTED_ITEM2  = "customfield_10251";
    static final String FIELD_AFFECTED_ITEM3  = "customfield_10252";
    static final String FIELD_AFFECTED_ITEM4  = "customfield_10253";

    // ========= COLUMN INDEXES (ajusta si tu Excel difiere) =========
    static final int COL_RESUMEN          = 0;
    static final int COL_DESC             = 1;
    static final int COL_COLEGIO_ASSET    = 2;  // objectId IE (Assets)
    static final int COL_DISPOSITIVO_ASSET= 3;  // objectId dispositivo (Assets)
    static final int COL_CONTACTO_NOM     = 4;
    static final int COL_CONTACTO_CEL     = 5;
    static final int COL_DEP              = 6;
    static final int COL_PROV             = 7;
    static final int COL_DIST             = 8;
    static final int COL_DIR              = 9;
    static final int COL_FECHA_GEN_IDX    = 10; // dd/MM/yyyy HH:mm:ss
    static final int COL_FECHA_SOL_IDX    = 11; // dd/MM/yyyy HH:mm:ss
    static final int COL_NOMBRE_IE        = 12;
    static final int COL_COD_MODULAR      = 13;
    static final int COL_COD_LOCAL        = 14;
    static final int COL_MEDIO_TRANS      = 16;
    static final int COL_TIPO_INCIDENCIA  = 17;
    static final int COL_TIEMPO_NODISP    = 18;
    static final int COL_TIEMPO_SOLUCION  = 19;
    static final int COL_ITEM             = 20; // 1..4
    static final int COL_SOLUCION_TEXTO    = 21;
    static final int COL_CAUSA_RAIZ       = 24;
    static final int COL_CAT_SERVICIO     = 25;
    static final int COL_TICKET_KEY       = 26; // MSP-xxxxx
    static final int COL_IMPUTABILIDAD    = 27;

    static final Pattern ONLY_NUMBERS = Pattern.compile("\\d+");

    // ========= HTTP =========
    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";

        if (jiraUrl.isEmpty() || email.isEmpty() || token.isEmpty()) {
            System.err.println("❌ ERROR: Falta JIRA_URL / JIRA_EMAIL / JIRA_TOKEN en .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        client = HttpClient.newHttpClient();
        mapper = new ObjectMapper();

        System.out.println(">>> 🚀 EXCEL -> JIRA (Solo celdas llenas, BORRAR limpia) <<<");
        System.out.println("Excel entrada: " + EXCEL_FILE);
        System.out.println("Formato fecha Excel esperado: dd/MM/yyyy HH:mm:ss");

        try (FileInputStream fis = new FileInputStream(new File(EXCEL_FILE));
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();

            int ok = 0, fail = 0, skipped = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String issueKey = getVal(row, COL_TICKET_KEY, fmt);
                if (issueKey.isEmpty()) {
                    skipped++;
                    continue;
                }

                ObjectNode payload = mapper.createObjectNode();
                ObjectNode fields = payload.putObject("fields");

                // ===== summary =====
                putSummary(fields, getVal(row, COL_RESUMEN, fmt));

                // ===== ADF fields en Jira (aquí mandamos string; si tu Jira exige ADF, se adapta) =====
                putStringOrClear(fields, FIELD_DESC_INCIDENTE, getVal(row, COL_DESC, fmt));
                putStringOrClear(fields, FIELD_SOLUCION, getVal(row, COL_SOLUCION_TEXTO, fmt));

                // ===== Options =====
                putOptionOrClear(fields, FIELD_IMPUTABILIDAD, getVal(row, COL_IMPUTABILIDAD, fmt));
                putOptionOrClear(fields, FIELD_CATEGORIA, getVal(row, COL_CAT_SERVICIO, fmt));
                putOptionOrClear(fields, FIELD_CAUSA_RAIZ, getVal(row, COL_CAUSA_RAIZ, fmt));

                // ===== Ubicación / IE =====
                putStringOrClear(fields, "customfield_10355", getVal(row, COL_DEP, fmt));
                putStringOrClear(fields, "customfield_10356", getVal(row, COL_PROV, fmt));
                putStringOrClear(fields, "customfield_10357", getVal(row, COL_DIST, fmt));
                putStringOrClear(fields, "customfield_10358", getVal(row, COL_DIR, fmt));
                putStringOrClear(fields, "customfield_10359", getVal(row, COL_NOMBRE_IE, fmt));
                putStringOrClear(fields, "customfield_10169", getVal(row, COL_COD_MODULAR, fmt));
                putStringOrClear(fields, "customfield_10168", getVal(row, COL_COD_LOCAL, fmt));

                // ===== Contacto =====
                putStringOrClear(fields, "customfield_10090", getVal(row, COL_CONTACTO_NOM, fmt));
                putStringOrClear(fields, "customfield_10091", getVal(row, COL_CONTACTO_CEL, fmt));

                // ===== Medio / Tipo Incidencia =====
                putStringOrClear(fields, FIELD_MEDIO_TX, getVal(row, COL_MEDIO_TRANS, fmt));
                putUpperOrClear(fields, FIELD_TIPO_INCIDENCIA, getVal(row, COL_TIPO_INCIDENCIA, fmt));

                // ===== Fechas manuales (respetando dd/MM/yyyy HH:mm:ss) =====
                putDateOrClear(fields, FIELD_FECHA_GEN, getVal(row, COL_FECHA_GEN_IDX, fmt));
                putDateOrClear(fields, FIELD_FECHA_SOL, getVal(row, COL_FECHA_SOL_IDX, fmt));

                // ===== Tiempos =====
                putStringOrClear(fields, FIELD_TIEMPO_SOL, getVal(row, COL_TIEMPO_SOLUCION, fmt));
                putStringOrClear(fields, FIELD_TIEMPO_NODISP, getVal(row, COL_TIEMPO_NODISP, fmt));

                // ===== Assets: IE =====
                putAssetArrayOrClear(fields, FIELD_IE_ASSET, getVal(row, COL_COLEGIO_ASSET, fmt));

                // ===== Assets: affected device según item =====
                String dispId = getVal(row, COL_DISPOSITIVO_ASSET, fmt);
                String itemNum = extractOnlyNumbers(getVal(row, COL_ITEM, fmt));
                String affectedField = affectedFieldByItem(itemNum);
                if (affectedField != null) {
                    putAssetArrayOrClear(fields, affectedField, dispId);
                }

                if (fields.size() == 0) {
                    System.out.println("⏭️ " + issueKey + " | nada que actualizar (fila vacía).");
                    skipped++;
                    continue;
                }

                boolean sent = sendPut(issueKey, payload);
                if (sent) {
                    ok++;
                    System.out.println("✅ " + issueKey + " actualizado.");
                } else {
                    fail++;
                    System.err.println("❌ " + issueKey + " falló.");
                }

                Thread.sleep(350);
            }

            System.out.println("\n🏁 FINAL | OK=" + ok + " | FAIL=" + fail + " | OMITIDOS=" + skipped);

        } catch (Exception e) {
            System.err.println("❌ Error crítico:");
            e.printStackTrace();
        }
    }

    // ================= EXCEL HELPERS =================

    private static String getVal(Row row, int idx, DataFormatter fmt) {
        Cell c = row.getCell(idx);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            // Respetamos: dd/MM/yyyy HH:mm:ss
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(c.getDateCellValue());
        }
        return fmt.formatCellValue(c).trim();
    }

    // ================= RULE HELPERS =================

    private static boolean isBorrar(String v) {
        return v != null && v.trim().equalsIgnoreCase(PALABRA_BORRAR);
    }

    private static void putStringOrClear(ObjectNode fields, String fieldId, String value) {
        if (isBorrar(value)) {
            fields.putNull(fieldId);
            return;
        }
        if (value != null && !value.trim().isEmpty()) {
            fields.put(fieldId, value.trim());
        }
    }

    private static void putUpperOrClear(ObjectNode fields, String fieldId, String value) {
        if (isBorrar(value)) {
            fields.putNull(fieldId);
            return;
        }
        if (value != null && !value.trim().isEmpty()) {
            fields.put(fieldId, value.trim().toUpperCase(Locale.ROOT));
        }
    }

    private static void putSummary(ObjectNode fields, String value) {
        if (isBorrar(value)) {
            // summary suele ser requerido; Jira puede rechazar null.
            fields.putNull("summary");
            return;
        }
        if (value != null && !value.trim().isEmpty()) {
            String v = value.trim();
            fields.put("summary", v.length() > 250 ? v.substring(0, 245) + "..." : v);
        }
    }

    private static void putOptionOrClear(ObjectNode fields, String fieldId, String value) {
        if (isBorrar(value)) {
            fields.putNull(fieldId);
            return;
        }
        if (value != null && !value.trim().isEmpty()) {
            fields.putObject(fieldId).put("value", value.trim());
        }
    }

    private static void putDateOrClear(ObjectNode fields, String fieldId, String raw) {
        if (isBorrar(raw)) {
            fields.putNull(fieldId);
            return;
        }
        String formatted = formatearParaJira(raw);
        if (formatted != null) fields.put(fieldId, formatted);
    }

    private static void putAssetArrayOrClear(ObjectNode fields, String fieldId, String objectIdRaw) {
        if (isBorrar(objectIdRaw)) {
            // para Assets multi: array vacío = limpiar
            fields.putArray(fieldId);
            return;
        }
        if (objectIdRaw == null || objectIdRaw.trim().isEmpty()) return;

        String objectId = objectIdRaw.trim();
        String onlyNum = extractOnlyNumbers(objectId);
        if (!onlyNum.isEmpty()) objectId = onlyNum;

        fields.putArray(fieldId).addObject()
                .put("workspaceId", WORKSPACE_ID)
                .put("id", WORKSPACE_ID + ":" + objectId)
                .put("objectId", objectId);
    }

    // ================= DATE: RESPETA dd/MM/yyyy HH:mm:ss =================

    private static String formatearParaJira(String raw) {
        if (raw == null) return null;
        String v = raw.trim();
        if (v.isEmpty() || v.contains("1900")) return null;

        // Si ya viene ISO/Jira con zona, lo respetamos
        if (v.contains("T") && v.matches(".*[+-]\\d{4}$")) return v;

        DateTimeFormatter DMY_HMS = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter DMY_HM = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);

        DateTimeFormatter YMD_HMS = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter YMD_HM = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);
        DateTimeFormatter YMD = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT)
                .withResolverStyle(ResolverStyle.STRICT);

        LocalDateTime ldt = null;

        try { ldt = LocalDateTime.parse(v, DMY_HMS); } catch (Exception ignored) {}
        if (ldt == null) { try { ldt = LocalDateTime.parse(v, DMY_HM); } catch (Exception ignored) {} }
        if (ldt == null) { try { ldt = LocalDate.parse(v, DMY).atStartOfDay(); } catch (Exception ignored) {} }

        if (ldt == null) { try { ldt = LocalDateTime.parse(v, YMD_HMS); } catch (Exception ignored) {} }
        if (ldt == null) { try { ldt = LocalDateTime.parse(v, YMD_HM); } catch (Exception ignored) {} }
        if (ldt == null) { try { ldt = LocalDate.parse(v, YMD).atStartOfDay(); } catch (Exception ignored) {} }

        if (ldt == null) return null;

        // Mantiene exactamente la misma fecha/hora (local) y fija -0500 (Perú)
        return ldt.format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")) + ".000-0500";
    }

    // ================= OTHER HELPERS =================

    private static String extractOnlyNumbers(String input) {
        if (input == null) return "";
        Matcher m = ONLY_NUMBERS.matcher(input);
        return m.find() ? m.group() : "";
    }

    private static String affectedFieldByItem(String itemNum) {
        return switch (itemNum) {
            case "1" -> FIELD_AFFECTED_ITEM1;
            case "2" -> FIELD_AFFECTED_ITEM2;
            case "3" -> FIELD_AFFECTED_ITEM3;
            case "4" -> FIELD_AFFECTED_ITEM4;
            default -> null;
        };
    }

    private static boolean sendPut(String issueKey, ObjectNode payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> res = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 204) return true;

            System.err.println("   ⚠️ PUT " + issueKey + " (" + res.statusCode() + "): " + res.body());
            return false;
        } catch (Exception e) {
            System.err.println("   ⚠️ Excepción PUT " + issueKey + ": " + e.getMessage());
            return false;
        }
    }
}