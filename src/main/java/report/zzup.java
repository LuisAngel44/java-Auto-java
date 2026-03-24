package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class zzup {

    // --- CONFIGURACIÓN DE JIRA ---
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    static final String PALABRA_BORRAR = "BORRAR";

    // IDs DE CAMPOS PERSONALIZADOS (Mapa MINEDU)
    static final String FIELD_DESC_INCIDENTE = "customfield_10180";
    static final String FIELD_SOLUCION = "customfield_10089";
    static final String FIELD_ORGANIZATION = "customfield_10002";
    static final String FIELD_CATEGORIA = "customfield_10394";
    static final String FIELD_CAUSA_RAIZ = "customfield_10135";
    static final String FIELD_FECHA_GEN = "customfield_10321";
    static final String FIELD_FECHA_SOL = "customfield_10322";
    static final String FIELD_TIEMPO_SOLUCION = "customfield_10177";
    static final String FIELD_IMPUTABILIDAD = "customfield_10471";

    // ÍNDICES DE COLUMNAS EXCEL
    static final int COL_RESUMEN = 0, COL_DESC = 1, COL_COLEGIO = 2, COL_DISPOSITIVO = 3;
    static final int COL_CONTACTO_NOM = 4, COL_CONTACTO_CEL = 5, COL_DEP = 6, COL_PROV = 7;
    static final int COL_DIST = 8, COL_DIR = 9, COL_FECHA_GEN_IDX = 10, COL_FECHA_SOL_IDX = 11;
    static final int COL_NOMBRE_IE = 12, COL_COD_MODULAR = 13, COL_COD_LOCAL = 14;
    static final int COL_MEDIO_TRANS = 16, COL_TIPO_INC = 17, COL_TIEMPO_NODISP = 18;
    static final int COL_TIEMPO_SOLUCION = 19;
    static final int COL_ITEM = 20, COL_SOLUCION = 21, COL_RUTAS_IMG = 22, COL_AREA = 23;
    static final int COL_CAUSA_RAIZ = 24, COL_CAT_SERVICIO = 25;
    static final int COL_TICKET_KEY = 26;
    static final int COL_IMPUTABILIDAD = 27;

    static final String CARPETA_EVIDENCIAS = "evidencias_minedu";
    static final Pattern PATRON_NOMBRE_IMAGEN_FECHA = Pattern.compile(".*\\d{1,4}[-_]\\d{1,2}[-_]\\d{1,4}.*");

    static String jiraUrl;
    static String encodedAuth;
    static HttpClient client;
    static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        jiraUrl = dotenv.get("JIRA_URL") != null ? dotenv.get("JIRA_URL").trim().replaceAll("/$", "") : "";
        String email = dotenv.get("JIRA_EMAIL") != null ? dotenv.get("JIRA_EMAIL").trim() : "";
        String token = dotenv.get("JIRA_TOKEN") != null ? dotenv.get("JIRA_TOKEN").trim() : "";

        if (email.isEmpty() || token.isEmpty() || jiraUrl.isEmpty()) {
            System.err.println("ERROR: Faltan credenciales en el archivo .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        System.out.println(">>> INICIANDO ACTUALIZACIÓN MASIVA Y GENERACIÓN DE REPORTE <<<");

        // --- PREPARAR EXCEL DE SALIDA (REPORTE) ---
        Workbook reportWorkbook = new XSSFWorkbook();
        Sheet reportSheet = reportWorkbook.createSheet("Resultados");
        Row headerRow = reportSheet.createRow(0);
        String[] headers = {"Ticket", "Estado Inicial", "Estado Final", "Resultado", "Detalle de Ejecución"};
        
        CellStyle headerStyle = reportWorkbook.createCellStyle();
        Font font = reportWorkbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
        int reportRowIdx = 1;

        try (FileInputStream file = new FileInputStream(new File("carga_Actualizar_tickets190326-item3-NOV-LOG.xlsx"));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            int exitosos = 0;
            int errores = 0;
            int omitidos = 0;

            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;

                String issueKey = getValRobust(row, COL_TICKET_KEY, formatter).trim();
                if (issueKey.isEmpty()) {
                    omitidos++;
                    continue;
                }

                System.out.println("\n------------------------------------------------");
                System.out.println("Ticket: " + issueKey + " | " + getValRobust(row, COL_RESUMEN, formatter));

                String logEstadoInicial = "Desconocido";
                String logEstadoFinal = "Desconocido";
                String logResultado = "Omitido";
                StringBuilder logDetalle = new StringBuilder();

                String estadoActual = obtenerEstadoActual(issueKey);
                if (!estadoActual.isEmpty()) {
                    logEstadoInicial = estadoActual;
                    logEstadoFinal = estadoActual; 
                    System.out.println("   Estado actual en Jira: [" + estadoActual + "]");
                } else {
                    logDetalle.append("No se pudo obtener estado inicial. ");
                }

                String solucionActual = obtenerTextoActual(issueKey, FIELD_SOLUCION);
                if (solucionActual == null) solucionActual = "";

                // 1. GESTIÓN DE IMÁGENES / EVIDENCIA
                String celdaImg = getValRobust(row, COL_RUTAS_IMG, formatter).trim();
                String solucionExcel = getValRobust(row, COL_SOLUCION, formatter).trim();
                boolean borrarSolucionTexto = isBorrar(solucionExcel);

                if (isBorrar(celdaImg)) {
                    eliminarAdjuntosEvidenciaPorPatron(issueKey);
                    resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);
                    logDetalle.append("Evidencias borradas. ");
                } else {
                    String cid = extraerSoloNumeros(celdaImg);
                    if (!cid.isEmpty() && !borrarSolucionTexto) {
                        String rawSolStr = getValRobust(row, COL_FECHA_SOL_IDX, formatter).trim();
                        LocalDateTime fechaSol = parseFechaFlexible(rawSolStr);

                        if (fechaSol != null) {
                            try {
                                eliminarAdjuntosEvidenciaPorPatron(issueKey);
                                resetearSolucion(issueKey);
                                File img = crearImagenConsolaEvidenciaSoloFechaFinal(cid, fechaSol, CARPETA_EVIDENCIAS);
                                String nombreAdjunto = subirArchivoYObtenerNombre(issueKey, img);

                                if (nombreAdjunto != null && !nombreAdjunto.isBlank()) {
                                    String textoBase = solucionExcel.isEmpty() ? solucionActual : solucionExcel;
                                    reescribirSolucionTextoMasReferencia(issueKey, textoBase, nombreAdjunto);
                                    logDetalle.append("Evidencia generada. ");
                                } else {
                                    resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);
                                }
                            } catch (Exception e) {
                                logDetalle.append("Error en evidencia. ");
                                resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);
                            }
                        }
                    }
                }

                // 2. PUT GENERAL
                ObjectNode putPayload = mapper.createObjectNode();
                ObjectNode putFields = putPayload.putObject("fields");

                prepararCamposActualizacion(putFields, row, formatter, issueKey);
                resolverImputabilidadOptionId(issueKey, putFields);

                if (putFields.size() > 0) {
                    if (enviarPutV2(issueKey, putPayload, "Actualización General de Campos")) {
                        logDetalle.append("Campos actualizados OK. ");
                        if (logResultado.equals("Omitido")) logResultado = "Exitoso";
                    } else {
                        logDetalle.append("Error al actualizar campos. ");
                        logResultado = "Error";
                        errores++;
                    }
                }

                // 3. REGLAS DE TRANSICIÓN DE ESTADOS
                String fechaSolRaw = getValRobust(row, COL_FECHA_SOL_IDX, formatter).trim();
                boolean tieneFechaFinal = !fechaSolRaw.isEmpty() && !isBorrar(fechaSolRaw);

                if ("Cancelado".equalsIgnoreCase(estadoActual)) {
                    System.out.println("   Regla detectada: Ticket 'Cancelado' -> Cambiando a 'Cerrado'...");
                    if (cambiarEstadoTicketConResultado(issueKey, "Cerrado")) {
                        logEstadoFinal = "Cerrado";
                        logDetalle.append("Estado cambiado a Cerrado. ");
                        logResultado = "Exitoso";
                    } else {
                        logDetalle.append("Error transicionando a Cerrado. ");
                        logResultado = "Error";
                    }

                } else if ("En pausa".equalsIgnoreCase(estadoActual)) {
                    System.out.println("   Regla detectada: Ticket 'En pausa' -> Cambiando a 'Resuelto'...");
                    if (cambiarEstadoTicketConResultado(issueKey, "Resuelto")) {
                        logEstadoFinal = "Resuelto";
                        logDetalle.append("Estado cambiado a Resuelto. ");
                        logResultado = "Exitoso";
                    } else {
                        logDetalle.append("Error transicionando a Resuelto. ");
                        logResultado = "Error";
                    }

                } else if (tieneFechaFinal) {
                    System.out.println("   Regla detectada: Tiene fecha final -> Cambiando estado a 'Resuelta'...");
                    if (cambiarEstadoTicketConResultado(issueKey, "Resuelta")) {
                        logEstadoFinal = "Resuelta";
                        logDetalle.append("Estado cambiado a Resuelta. ");
                        logResultado = "Exitoso";
                    } else {
                        if ("Resuelta".equalsIgnoreCase(estadoActual)) {
                            logDetalle.append("Ya estaba en Resuelta. ");
                        } else {
                            logDetalle.append("Error transicionando a Resuelta. ");
                            logResultado = "Error";
                        }
                    }
                }

                // 4. DESPUÉS: actualizar Fecha Final + Tiempo Solución
                if (tieneFechaFinal) {
                    ObjectNode finalPayload = mapper.createObjectNode();
                    ObjectNode fFields = finalPayload.putObject("fields");

                    String fSolFormateada = formatearParaJira(fechaSolRaw);
                    if (fSolFormateada != null) fFields.put(FIELD_FECHA_SOL, fSolFormateada);

                    String tSol = getValRobust(row, COL_TIEMPO_SOLUCION, formatter).trim();
                    if (!tSol.isEmpty() && !isBorrar(tSol)) fFields.put(FIELD_TIEMPO_SOLUCION, tSol);

                    if (fFields.size() > 0) {
                        enviarPutV2(issueKey, finalPayload, "Post-Resolución");
                    }
                }

                if (logResultado.equals("Exitoso")) exitosos++;
                if (logResultado.equals("Omitido")) omitidos++;

                // Escribir en Excel de reporte
                Row reportRow = reportSheet.createRow(reportRowIdx++);
                reportRow.createCell(0).setCellValue(issueKey);
                reportRow.createCell(1).setCellValue(logEstadoInicial);
                reportRow.createCell(2).setCellValue(logEstadoFinal);
                reportRow.createCell(3).setCellValue(logResultado);
                reportRow.createCell(4).setCellValue(logDetalle.toString().trim());

                Thread.sleep(500); 
            }

            System.out.println("\nPROCESO FINALIZADO | Correctos: " + exitosos + " | Fallidos: " + errores + " | Omitidos: " + omitidos);

        } catch (Exception e) {
            System.err.println("Error crítico procesando el archivo Excel:");
            e.printStackTrace();
        } finally {
            try {
                for (int i = 0; i < 5; i++) reportSheet.autoSizeColumn(i);
                String nombreReporte = "Resultados_Actualizacion_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
                FileOutputStream fileOut = new FileOutputStream(nombreReporte);
                reportWorkbook.write(fileOut);
                fileOut.close();
                reportWorkbook.close();
                System.out.println("📊 REPORTE GENERADO: " + nombreReporte);
            } catch (Exception e) {
                System.err.println("Error al guardar el Excel de reporte: " + e.getMessage());
            }
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static String obtenerEstadoActual(String issueKey) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "?fields=status"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                return mapper.readTree(res.body()).path("fields").path("status").path("name").asText("");
            }
        } catch (Exception e) {
            System.err.println("   Error obteniendo estado actual: " + e.getMessage());
        }
        return "";
    }

    private static void prepararCamposActualizacion(ObjectNode fields, Row row, DataFormatter fmt, String issueKey) {
        putStringOrClear(fields, FIELD_DESC_INCIDENTE, getValRobust(row, COL_DESC, fmt));

        String textoSolucion = getValRobust(row, COL_SOLUCION, fmt);
        if (isBorrar(textoSolucion)) {
            fields.putNull(FIELD_SOLUCION);
        } else {
            if (textoSolucion != null && !textoSolucion.trim().isEmpty()) {
                fields.put(FIELD_SOLUCION, textoSolucion.trim());
            }
        }

        String resumen = getValRobust(row, COL_RESUMEN, fmt);
        if (isBorrar(resumen)) {
            fields.putNull("summary");
        } else if (!resumen.trim().isEmpty()) {
            fields.put("summary", resumen.trim().length() > 250 ? resumen.trim().substring(0, 245) + "..." : resumen.trim());
        }

        String tipoInc = getValRobust(row, COL_TIPO_INC, fmt);
        if (isBorrar(tipoInc)) fields.putNull("customfield_10469");
        else if (!tipoInc.trim().isEmpty()) fields.put("customfield_10469", tipoInc.trim().toUpperCase());

        putStringOrClear(fields, "customfield_10090", getValRobust(row, COL_CONTACTO_NOM, fmt));
        putStringOrClear(fields, "customfield_10091", getValRobust(row, COL_CONTACTO_CEL, fmt));

        putOptionOrClear(fields, FIELD_CATEGORIA, getValRobust(row, COL_CAT_SERVICIO, fmt));
        putOptionOrClear(fields, FIELD_CAUSA_RAIZ, getValRobust(row, COL_CAUSA_RAIZ, fmt));

        String area = getValRobust(row, COL_AREA, fmt);
        if (isBorrar(area)) {
            fields.putNull("customfield_10504");
        } else if (!area.trim().isEmpty()) {
            fields.putObject("customfield_10504").put("value", area.trim().equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
        }

        String rawGen = getValRobust(row, COL_FECHA_GEN_IDX, fmt);
        if (isBorrar(rawGen)) fields.putNull(FIELD_FECHA_GEN);
        else {
            String fGen = formatearParaJira(rawGen);
            if (fGen != null) fields.put(FIELD_FECHA_GEN, fGen);
        }

        String tNoDisp = getValRobust(row, COL_TIEMPO_NODISP, fmt);
        if (isBorrar(tNoDisp)) fields.putNull("customfield_10178");
        else if (!tNoDisp.trim().isEmpty()) fields.put("customfield_10178", tNoDisp.trim());

        putStringOrClear(fields, "customfield_10355", getValRobust(row, COL_DEP, fmt));
        putStringOrClear(fields, "customfield_10356", getValRobust(row, COL_PROV, fmt));
        putStringOrClear(fields, "customfield_10357", getValRobust(row, COL_DIST, fmt));
        putStringOrClear(fields, "customfield_10358", getValRobust(row, COL_DIR, fmt));
        putStringOrClear(fields, "customfield_10359", getValRobust(row, COL_NOMBRE_IE, fmt));
        putStringOrClear(fields, "customfield_10169", getValRobust(row, COL_COD_MODULAR, fmt));
        putStringOrClear(fields, "customfield_10168", getValRobust(row, COL_COD_LOCAL, fmt));
        putStringOrClear(fields, "customfield_10361", getValRobust(row, COL_MEDIO_TRANS, fmt));

        String colId = getValRobust(row, COL_COLEGIO, fmt);
        if (isBorrar(colId)) fields.putArray("customfield_10170");
        else if (!colId.trim().isEmpty()) agregarActivo(fields, "customfield_10170", WORKSPACE_ID, colId.trim());

        String dispId = getValRobust(row, COL_DISPOSITIVO, fmt);
        String itemNum = extraerSoloNumeros(getValRobust(row, COL_ITEM, fmt));
        String[] config = obtenerConfiguracionItem(itemNum);

        if (config.length > 1 && config[1] != null && !config[1].isEmpty()) {
            if (isBorrar(dispId)) {
                fields.putArray(config[1]);
            } else if (!dispId.trim().isEmpty()) {
                agregarActivo(fields, config[1], WORKSPACE_ID, dispId.trim());
            }
        }

        String imputabilidad = getValRobust(row, COL_IMPUTABILIDAD, fmt);
        if (isBorrar(imputabilidad)) {
            fields.putNull(FIELD_IMPUTABILIDAD);
        } else if (imputabilidad != null && !imputabilidad.trim().isEmpty()) {
            fields.putObject(FIELD_IMPUTABILIDAD).put("value", imputabilidad.trim());
        }
    }

    private static void resetearSolucion(String issueKey) {
        ObjectNode payloadReset = mapper.createObjectNode();
        payloadReset.putObject("fields").put(FIELD_SOLUCION, "");
        enviarPutV2(issueKey, payloadReset, "Reset Solución");
    }

    private static void resetearYReescribirSolucionSoloTexto(String issueKey, String textoConservar) {
        if (textoConservar == null) textoConservar = "";
        resetearSolucion(issueKey);
        ObjectNode payloadWrite = mapper.createObjectNode();
        payloadWrite.putObject("fields").put(FIELD_SOLUCION, textoConservar.trim());
        enviarPutV2(issueKey, payloadWrite, "Reescribir Solución (solo texto)");
    }

    private static void reescribirSolucionTextoMasReferencia(String issueKey, String textoConservar, String filenameAdjunto) {
        if (textoConservar == null) textoConservar = "";
        String base = textoConservar.trim();
        StringBuilder sb = new StringBuilder();
        if (!base.isEmpty()) sb.append(base).append("\n\n");
        sb.append("Evidencia adjunta: ").append(filenameAdjunto);

        ObjectNode payloadWrite = mapper.createObjectNode();
        payloadWrite.putObject("fields").put(FIELD_SOLUCION, sb.toString());
        enviarPutV2(issueKey, payloadWrite, "Reescribir Solución (texto + evidencia)");
    }

    private static File crearImagenConsolaEvidenciaSoloFechaFinal(String cid, LocalDateTime fechaSol, String carpetaSalida) throws Exception {
        new File(carpetaSalida).mkdirs();
        DateTimeFormatter fmtJuniper = DateTimeFormatter.ofPattern("MMM dd HH:mm:ss", Locale.ENGLISH);

        String[] lineas = new String[]{
                "juniper@MINEDU5K2_" + cid + "_SRX300> show log chassisd | match \"power sequencer started\"",
                fechaSol.format(fmtJuniper) + "  .. power sequencer started .."
        };

        int fontSize = 16;
        java.awt.Font font = new java.awt.Font("Consolas", java.awt.Font.PLAIN, fontSize);
        int ancho = 900;
        int altoLinea = 25;
        int padding = 20;
        int alto = (lineas.length * altoLinea) + (padding * 2);

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(ancho, alto, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new java.awt.Color(20, 20, 25));
        g.fillRect(0, 0, ancho, alto);
        g.setFont(font);
        g.setColor(new java.awt.Color(200, 200, 200));

        int y = padding + fontSize;
        for (String l : lineas) {
            g.drawString(l, padding, y);
            y += altoLinea;
        }
        g.dispose();

        DateTimeFormatter fmtNombre = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
        String nombre = "EVIDENCIA_" + cid + "_" + fechaSol.format(fmtNombre) + ".png";

        File out = new File(carpetaSalida, nombre);
        javax.imageio.ImageIO.write(img, "png", out);
        return out;
    }

    private static void resolverImputabilidadOptionId(String issueKey, ObjectNode putFields) {
        try {
            if (putFields == null) return;
            JsonNode node = putFields.get(FIELD_IMPUTABILIDAD);
            if (node == null || node.isNull()) return;

            String desiredText = null;
            if (node.isObject() && node.has("value")) desiredText = node.path("value").asText(null);
            else if (node.isTextual()) desiredText = node.asText(null);

            if (desiredText == null || desiredText.trim().isEmpty()) return;
            desiredText = desiredText.trim();

            JsonNode allowedValues = obtenerAllowedValuesCampo(issueKey, FIELD_IMPUTABILIDAD);
            if (allowedValues == null || !allowedValues.isArray()) {
                putFields.remove(FIELD_IMPUTABILIDAD);
                return;
            }

            String optionId = buscarOptionIdPorTexto(allowedValues, desiredText);
            if (optionId == null) {
                putFields.remove(FIELD_IMPUTABILIDAD);
                return;
            }

            ObjectNode newObj = mapper.createObjectNode();
            newObj.put("id", optionId);
            putFields.set(FIELD_IMPUTABILIDAD, newObj);

        } catch (Exception e) {
            putFields.remove(FIELD_IMPUTABILIDAD);
        }
    }

    private static JsonNode obtenerAllowedValuesCampo(String issueKey, String fieldId) {
        try {
            String url = jiraUrl + "/rest/api/3/issue/" + issueKey + "?expand=editmeta&fields=" + fieldId;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return null;
            return mapper.readTree(res.body()).path("editmeta").path("fields").path(fieldId).path("allowedValues");
        } catch (Exception e) { return null; }
    }

    private static String buscarOptionIdPorTexto(JsonNode allowedValues, String desiredText) {
        if (allowedValues == null || !allowedValues.isArray()) return null;
        String norm = normalizarTexto(desiredText);
        for (JsonNode opt : allowedValues) {
            String value = opt.path("value").asText(null);
            String id = opt.path("id").asText(null);
            if (value == null || id == null) continue;
            if (normalizarTexto(value).equals(norm)) return id;
        }
        return null;
    }

    private static String normalizarTexto(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String obtenerTextoActual(String issueKey, String fieldId) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "?fields=" + fieldId))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                JsonNode node = mapper.readTree(res.body()).path("fields").path(fieldId);
                if (!node.isMissingNode() && !node.isNull()) return node.asText();
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static void eliminarAdjuntosEvidenciaPorPatron(String issueKey) {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey + "?fields=attachment"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getRes.statusCode() != 200) return;

            JsonNode attachments = mapper.readTree(getRes.body()).path("fields").path("attachment");
            if (!attachments.isArray() || attachments.size() == 0) return;

            int borrados = 0;
            for (JsonNode attachment : attachments) {
                String id = attachment.path("id").asText();
                String filename = attachment.path("filename").asText("");
                if (filename != null && PATRON_NOMBRE_IMAGEN_FECHA.matcher(filename).matches()) {
                    HttpRequest delReq = HttpRequest.newBuilder()
                            .uri(URI.create(jiraUrl + "/rest/api/2/attachment/" + id))
                            .header("Authorization", "Basic " + encodedAuth)
                            .DELETE()
                            .build();
                    HttpResponse<String> delRes = client.send(delReq, HttpResponse.BodyHandlers.ofString());
                    if (delRes.statusCode() == 204) borrados++;
                }
            }
        } catch (Exception ignored) {}
    }

    private static String subirArchivoYObtenerNombre(String issueKey, File f) {
        if (f == null || !f.exists()) return null;
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
            HttpResponse<String> response = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode jsonResponse = mapper.readTree(response.body());
                if (jsonResponse.isArray() && jsonResponse.size() > 0) {
                    return jsonResponse.get(0).path("filename").asText();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean enviarPutV2(String issueKey, ObjectNode payload, String nombreLog) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + issueKey))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                System.out.println("   " + nombreLog + " OK");
                return true;
            } else {
                System.err.println("   Falló " + nombreLog + " (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) { return false; }
    }

    private static boolean cambiarEstadoTicketConResultado(String issueKey, String estadoDestino) {
        try {
            HttpRequest statusReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "?fields=status"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> statusRes = client.send(statusReq, HttpResponse.BodyHandlers.ofString());
            
            if (statusRes.statusCode() == 200) {
                String estadoActual = mapper.readTree(statusRes.body()).path("fields").path("status").path("name").asText("");
                if (estadoActual.equalsIgnoreCase(estadoDestino)) return true;
            }

            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions?expand=transitions.fields"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .GET()
                    .build();
            HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            if (getRes.statusCode() != 200) return false;

            JsonNode transitions = mapper.readTree(getRes.body()).path("transitions");
            JsonNode targetTransition = null;
            String transitionId = null;
            
            for (JsonNode t : transitions) {
                if (t.path("to").path("name").asText().equalsIgnoreCase(estadoDestino)) {
                    transitionId = t.path("id").asText();
                    targetTransition = t;
                    break;
                }
            }
            
            if (transitionId == null) return false;

            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("transition").put("id", transitionId);

            if (targetTransition != null) {
                JsonNode resolutionField = targetTransition.path("fields").path("resolution");
                if (!resolutionField.isMissingNode()) {
                    JsonNode allowedValues = resolutionField.path("allowedValues");
                    if (allowedValues.isArray() && allowedValues.size() > 0) {
                        String resId = allowedValues.get(0).path("id").asText();
                        payload.putObject("fields").putObject("resolution").put("id", resId);
                    } else {
                        payload.putObject("fields").putObject("resolution").put("name", "Resuelto");
                    }
                }
            }

            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> postRes = client.send(postReq, HttpResponse.BodyHandlers.ofString());

            return postRes.statusCode() == 204;
        } catch (Exception e) { return false; }
    }

    private static byte[] createMultipartBody(File file, String boundary) throws Exception {
        String fileName = file.getName();
        String mimeType = "application/octet-stream";
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".png")) mimeType = "image/png";
        else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) mimeType = "image/jpeg";
        else if (lowerName.endsWith(".gif")) mimeType = "image/gif";

        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n" +
                "Content-Type: " + mimeType + "\r\n\r\n";

        byte[] h = header.getBytes(StandardCharsets.UTF_8);
        byte[] f = Files.readAllBytes(file.toPath());
        byte[] t = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] res = new byte[h.length + f.length + t.length];
        System.arraycopy(h, 0, res, 0, h.length);
        System.arraycopy(f, 0, res, h.length, f.length);
        System.arraycopy(t, 0, res, h.length + f.length, t.length);
        return res;
    }

    private static String getValRobust(Row row, int index, DataFormatter fmt) {
        Cell c = row.getCell(index);
        if (c == null) return "";
        if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(c.getDateCellValue());
        }
        return fmt.formatCellValue(c).trim();
    }

    private static String formatearParaJira(String raw) {
        if (raw == null || raw.trim().isEmpty() || raw.contains("1900")) return null;
        try {
            if (raw.contains("T") && raw.contains("-0500")) return raw;
            String limpio = raw.trim().replaceAll("\\s+", " ");
            String fechaPart = limpio, horaPart = "00:00:00";
            if (limpio.contains(" ")) {
                String[] partes = limpio.split(" ");
                fechaPart = partes[0];
                if (partes.length > 1) horaPart = partes[1];
            }
            String[] dmy = fechaPart.split("[/\\-]");
            if (dmy.length == 3) {
                String dia = dmy[0].length() == 4 ? dmy[2] : dmy[0];
                String mes = dmy[1];
                String anio = dmy[0].length() == 4 ? dmy[0] : dmy[2];
                dia = (dia.length() == 1) ? "0" + dia : dia;
                mes = (mes.length() == 1) ? "0" + mes : mes;
                anio = (anio.length() == 2) ? "20" + anio : anio;
                fechaPart = anio + "-" + mes + "-" + dia;
            }
            String[] hms = horaPart.split(":");
            String hh = (hms.length > 0 && hms[0].length() > 0) ? hms[0] : "00";
            String mm = (hms.length > 1 && hms[1].length() > 0) ? hms[1] : "00";
            String ss = (hms.length > 2 && hms[2].length() > 0) ? hms[2] : "00";
            hh = (hh.length() == 1) ? "0" + hh : hh;
            mm = (mm.length() == 1) ? "0" + mm : mm;
            ss = (ss.length() == 1) ? "0" + ss : ss;
            return fechaPart + "T" + hh + ":" + mm + ":" + ss + ".000-0500";
        } catch (Exception e) { return null; }
    }

    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1": return new String[]{"126", "customfield_10250", "2"};
            case "2": return new String[]{"127", "customfield_10251", "1"};
            case "3": return new String[]{"128", "customfield_10252", "3"};
            case "4": return new String[]{"129", "customfield_10253", "4"};
            default:  return new String[]{"129", "customfield_10253", ""};
        }
    }

    private static String extraerSoloNumeros(String input) {
        if (input == null) return "";
        Matcher m = Pattern.compile("\\d+").matcher(input);
        return m.find() ? m.group() : "";
    }

    private static LocalDateTime parseFechaFlexible(String raw) {
        if (raw == null) return null;
        String val = raw.trim();
        if (val.isEmpty() || val.contains("1900")) return null;
        DateTimeFormatter[] fmts = new DateTimeFormatter[]{
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };
        for (DateTimeFormatter f : fmts) {
            try {
                if (val.length() == 10 && val.contains("/")) return LocalDateTime.parse(val + " 00:00:00", DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
                if (val.length() == 10 && val.contains("-")) return LocalDateTime.parse(val + " 00:00:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return LocalDateTime.parse(val, f);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static boolean isBorrar(String v) {
        return v != null && v.trim().equalsIgnoreCase(PALABRA_BORRAR);
    }

    private static void putStringOrClear(ObjectNode fields, String key, String value) {
        if (isBorrar(value)) { fields.putNull(key); return; }
        if (value != null && !value.trim().isEmpty()) fields.put(key, value.trim());
    }

    private static void putOptionOrClear(ObjectNode fields, String fieldId, String value) {
        if (isBorrar(value)) { fields.putNull(fieldId); return; }
        if (value != null && !value.trim().isEmpty()) fields.putObject(fieldId).put("value", value.trim());
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        values.putArray(fieldId).addObject()
                .put("workspaceId", wsId)
                .put("id", wsId + ":" + objId)
                .put("objectId", objId);
    }
}