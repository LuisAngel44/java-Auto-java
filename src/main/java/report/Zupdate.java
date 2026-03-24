package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Zupdate {

    // --- CONFIGURACIÓN DE JIRA ---
    static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";

    // ✅ Regla: palabra que indica "limpiar/borrar campo"
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

    // ÍNDICES DE COLUMNAS EXCEL (Sincronizado con TTT)
    static final int COL_RESUMEN = 0, COL_DESC = 1, COL_COLEGIO = 2, COL_DISPOSITIVO = 3;
    static final int COL_CONTACTO_NOM = 4, COL_CONTACTO_CEL = 5, COL_DEP = 6, COL_PROV = 7;
    static final int COL_DIST = 8, COL_DIR = 9, COL_FECHA_GEN_IDX = 10, COL_FECHA_SOL_IDX = 11;
    static final int COL_NOMBRE_IE = 12, COL_COD_MODULAR = 13, COL_COD_LOCAL = 14;
    static final int COL_MEDIO_TRANS = 16, COL_TIPO_INC = 17, COL_TIEMPO_NODISP = 18;
    static final int COL_TIEMPO_SOLUCION = 19;
    static final int COL_ITEM = 20, COL_SOLUCION = 21, COL_RUTAS_IMG = 22, COL_AREA = 23;
    static final int COL_CAUSA_RAIZ = 24, COL_CAT_SERVICIO = 25;

    // COLUMNA CLAVE PARA LA ACTUALIZACIÓN
    static final int COL_TICKET_KEY = 26;

    // Carpeta donde se generan evidencias localmente
    static final String CARPETA_EVIDENCIAS = "evidencias_minedu";

    // ✅ Patrón de evidencias por “fecha en nombre” (para borrar evidencias previas)
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
            System.err.println("❌ ERROR: Faltan credenciales en el archivo .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes(StandardCharsets.UTF_8));
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        System.out.println(">>> 🚀 INICIANDO ACTUALIZACIÓN MASIVA (Versión Definitiva + Reglas Evidencia) <<<");
        System.out.println("Reglas:");
        System.out.println(" - Celda vacía => NO actualiza ese campo.");
        System.out.println(" - Celda = 'BORRAR' => Limpia el campo en Jira (lo deja vacío) para campos soportados.");
        System.out.println(" - Columna Imagen (COL_RUTAS_IMG):");
        System.out.println("     * CID numérico => genera PNG (solo fecha final), borra evidencias previas, sube nueva, y resetea+reescribe Solución (conserva texto + referencia).");
        System.out.println("     * BORRAR => borra evidencias previas y resetea+reescribe Solución conservando solo el texto (sin preview roto).");

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
                System.out.println("🔄 Ticket: " + issueKey + " | " + getValRobust(row, COL_RESUMEN, formatter));

                // Texto actual de solución para conservarlo si hace falta
                String solucionActual = obtenerTextoActual(issueKey, FIELD_SOLUCION);
                if (solucionActual == null) solucionActual = "";

                // 1️⃣ GESTIÓN DE IMÁGENES / EVIDENCIA (CID o BORRAR o vacío)
                String celdaImg = getValRobust(row, COL_RUTAS_IMG, formatter).trim();

                // Si SOLUCIÓN (texto) dice BORRAR, entonces se limpia el campo por regla original, y no tocamos imágenes
                String solucionExcel = getValRobust(row, COL_SOLUCION, formatter).trim();
                boolean borrarSolucionTexto = isBorrar(solucionExcel);

                if (isBorrar(celdaImg)) {
                    // BORRAR en columna imagen: borrar evidencias previas + reset+reescribir solución solo texto
                    System.out.println("   🧹 BORRAR en columna imagen => borrando evidencias previas y arreglando campo Solución sin borrar texto.");

                    eliminarAdjuntosEvidenciaPorPatron(issueKey);
                    resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);

                } else {
                    // CID => generar evidencia y reemplazar (solo si no están borrando el texto solución)
                    String cid = extraerSoloNumeros(celdaImg);
                    if (!cid.isEmpty() && !borrarSolucionTexto) {
                        String rawSolStr = getValRobust(row, COL_FECHA_SOL_IDX, formatter).trim();
                        LocalDateTime fechaSol = parseFechaFlexible(rawSolStr);

                        if (fechaSol == null) {
                            System.out.println("   ⚠️ Fecha final inválida para evidencia. Sol='" + rawSolStr + "'. Se omite imagen.");
                        } else {
                            try {
                                // 1) borrar evidencias previas (adjuntos)
                                eliminarAdjuntosEvidenciaPorPatron(issueKey);

                                // 2) reset del campo solución para limpiar previews rotos
                                resetearSolucion(issueKey);

                                // 3) crear imagen (solo fecha final)
                                File img = crearImagenConsolaEvidenciaSoloFechaFinal(cid, fechaSol, CARPETA_EVIDENCIAS);

                                // 4) subir
                                String nombreAdjunto = subirArchivoYObtenerNombre(issueKey, img);

                                // 5) reescribir solución: conservar texto + referencia al adjunto
                                if (nombreAdjunto != null && !nombreAdjunto.isBlank()) {
                                    // Si Excel trae texto de solución, usamos ese; si no, conservamos lo que ya tenía Jira
                                    String textoBase = solucionExcel.isEmpty() ? solucionActual : solucionExcel;
                                    reescribirSolucionTextoMasReferencia(issueKey, textoBase, nombreAdjunto);
                                } else {
                                    // si falla subida, restauramos texto previo
                                    resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);
                                }
                            } catch (Exception e) {
                                System.err.println("   ❌ Error generando/subiendo evidencia: " + e.getMessage());
                                // restaurar
                                resetearYReescribirSolucionSoloTexto(issueKey, solucionActual);
                            }
                        }
                    }
                }

                // 2️⃣ PREPARAR TODOS LOS CAMPOS (26 del Excel + campo solución con reglas)
                ObjectNode putPayload = mapper.createObjectNode();
                ObjectNode putFields = putPayload.putObject("fields");

                prepararCamposActualizacion(putFields, row, formatter, issueKey);

                // ✅ Si no hay campos, omitimos PUT (excepto que ya hayamos hecho PUTs de solución por evidencia arriba)
                if (putFields.size() == 0) {
                    System.out.println("   ⏭️ No hay campos (en PUT general) para actualizar. Se omite PUT general.");
                    omitidos++;
                    continue;
                }

                // 3️⃣ ENVIAR ACTUALIZACIÓN GENERAL
                if (enviarPutV2(issueKey, putPayload, "Actualización General de Campos")) {

                    // 4️⃣ CAMBIO DE ESTADO Y POST-ACTUALIZACIÓN (igual que tu versión)
                    String fechaSol = getValRobust(row, COL_FECHA_SOL_IDX, formatter).trim();
                    String textoSolucion = getValRobust(row, COL_SOLUCION, formatter).trim();

                    // Solo si NO están usando BORRAR en esos campos
                    if (!isBorrar(fechaSol) && !isBorrar(textoSolucion)) {
                        if (!fechaSol.isEmpty() && (!textoSolucion.isEmpty())) {
                            System.out.println("   🔄 Cambiando estado a 'Resuelta'...");
                            cambiarEstadoTicket(issueKey, "Resuelta");

                            ObjectNode finalPayload = mapper.createObjectNode();
                            ObjectNode fFields = finalPayload.putObject("fields");

                            String fSolFormateada = formatearParaJira(fechaSol);
                            if (fSolFormateada != null) fFields.put(FIELD_FECHA_SOL, fSolFormateada);

                            String tSol = getValRobust(row, COL_TIEMPO_SOLUCION, formatter).trim();
                            if (!tSol.isEmpty() && !isBorrar(tSol)) fFields.put(FIELD_TIEMPO_SOLUCION, tSol);

                            if (fFields.size() > 0) {
                                enviarPutV2(issueKey, finalPayload, "Post-Resolución (Fechas y Tiempo forzados)");
                            }
                        }
                    }

                    exitosos++;
                } else {
                    errores++;
                }

                Thread.sleep(500); // Pausa para no saturar la API
            }

            System.out.println("\n🏁 PROCESO FINALIZADO | Correctos: " + exitosos + " | Fallidos: " + errores + " | Omitidos: " + omitidos);

        } catch (Exception e) {
            System.err.println("❌ Error crítico procesando el archivo Excel:");
            e.printStackTrace();
        }
    }

    private static void prepararCamposActualizacion(ObjectNode fields, Row row, DataFormatter fmt, String issueKey) {
        // ===== Regla general: vacío => no tocar; BORRAR => limpiar =====

        // 1) Descripción incidente
        putStringOrClear(fields, FIELD_DESC_INCIDENTE, getValRobust(row, COL_DESC, fmt));

        // 2) Solución (texto) - OJO: la evidencia ya se gestiona arriba con reset+reescritura.
        // Si Excel trae BORRAR en solución, aquí sí limpiamos el campo completo.
        String textoSolucion = getValRobust(row, COL_SOLUCION, fmt);
        if (isBorrar(textoSolucion)) {
            fields.putNull(FIELD_SOLUCION);
        } else {
            // Si trae texto, actualiza. Si está vacío, no toca (para no pisar lo reescrito por evidencia).
            if (textoSolucion != null && !textoSolucion.trim().isEmpty()) {
                fields.put(FIELD_SOLUCION, textoSolucion.trim());
            }
        }

        // 3) Summary
        String resumen = getValRobust(row, COL_RESUMEN, fmt);
        if (isBorrar(resumen)) {
            // normalmente Jira no deja summary null, pero respetamos regla
            fields.putNull("summary");
        } else if (!resumen.trim().isEmpty()) {
            fields.put("summary", resumen.trim().length() > 250 ? resumen.trim().substring(0, 245) + "..." : resumen.trim());
        }

        // 4) Tipo incidencia
        String tipoInc = getValRobust(row, COL_TIPO_INC, fmt);
        if (isBorrar(tipoInc)) fields.putNull("customfield_10469");
        else if (!tipoInc.trim().isEmpty()) fields.put("customfield_10469", tipoInc.trim().toUpperCase());

        // 5) Contacto
        putStringOrClear(fields, "customfield_10090", getValRobust(row, COL_CONTACTO_NOM, fmt));
        putStringOrClear(fields, "customfield_10091", getValRobust(row, COL_CONTACTO_CEL, fmt));

        // 6) Categoría / Causa raíz (options)
        putOptionOrClear(fields, FIELD_CATEGORIA, getValRobust(row, COL_CAT_SERVICIO, fmt));
        putOptionOrClear(fields, FIELD_CAUSA_RAIZ, getValRobust(row, COL_CAUSA_RAIZ, fmt));

        // 7) Área (option)
        String area = getValRobust(row, COL_AREA, fmt);
        if (isBorrar(area)) {
            fields.putNull("customfield_10504");
        } else if (!area.trim().isEmpty()) {
            fields.putObject("customfield_10504").put("value", area.trim().equalsIgnoreCase("Urbana") ? "Urbana" : "Rural");
        }

        // 8) Fecha generación
        String rawGen = getValRobust(row, COL_FECHA_GEN_IDX, fmt);
        if (isBorrar(rawGen)) fields.putNull(FIELD_FECHA_GEN);
        else {
            String fGen = formatearParaJira(rawGen);
            if (fGen != null) fields.put(FIELD_FECHA_GEN, fGen);
        }

        // 9) Tiempo no disponibilidad
        String tNoDisp = getValRobust(row, COL_TIEMPO_NODISP, fmt);
        if (isBorrar(tNoDisp)) fields.putNull("customfield_10178");
        else if (!tNoDisp.trim().isEmpty()) fields.put("customfield_10178", tNoDisp.trim());

        // 10) Ubicación y datos IE
        putStringOrClear(fields, "customfield_10355", getValRobust(row, COL_DEP, fmt));
        putStringOrClear(fields, "customfield_10356", getValRobust(row, COL_PROV, fmt));
        putStringOrClear(fields, "customfield_10357", getValRobust(row, COL_DIST, fmt));
        putStringOrClear(fields, "customfield_10358", getValRobust(row, COL_DIR, fmt));
        putStringOrClear(fields, "customfield_10359", getValRobust(row, COL_NOMBRE_IE, fmt));
        putStringOrClear(fields, "customfield_10169", getValRobust(row, COL_COD_MODULAR, fmt));
        putStringOrClear(fields, "customfield_10168", getValRobust(row, COL_COD_LOCAL, fmt));
        putStringOrClear(fields, "customfield_10361", getValRobust(row, COL_MEDIO_TRANS, fmt));

        // 11) Assets (IE y Dispositivo)
        String colId = getValRobust(row, COL_COLEGIO, fmt);
        if (isBorrar(colId)) fields.putArray("customfield_10170"); // limpia multi
        else if (!colId.trim().isEmpty()) agregarActivo(fields, "customfield_10170", WORKSPACE_ID, colId.trim());

        String dispId = getValRobust(row, COL_DISPOSITIVO, fmt);
        String itemNum = extraerSoloNumeros(getValRobust(row, COL_ITEM, fmt));
        String[] config = obtenerConfiguracionItem(itemNum);

        if (config.length > 1 && config[1] != null && !config[1].isEmpty()) {
            if (isBorrar(dispId)) {
                fields.putArray(config[1]); // limpia
            } else if (!dispId.trim().isEmpty()) {
                agregarActivo(fields, config[1], WORKSPACE_ID, dispId.trim());
            }
        }
    }

    // =========================================================================
    // ✅ Solución: reset + reescritura (para eliminar preview roto)
    // =========================================================================

    private static void resetearSolucion(String issueKey) {
        ObjectNode payloadReset = mapper.createObjectNode();
        payloadReset.putObject("fields").put(FIELD_SOLUCION, "");
        enviarPutV2(issueKey, payloadReset, "Reset Solución (vaciar campo)");
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
        enviarPutV2(issueKey, payloadWrite, "Reescribir Solución (texto + referencia adjunto)");
    }

    // =========================================================================
    // ✅ Generación de evidencia (solo fecha final)
    // =========================================================================

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

        BufferedImage img = new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

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
        ImageIO.write(img, "png", out);
        return out;
    }

    // =========================================================================
    // MÉTODOS DE RED Y API
    // =========================================================================

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
        } catch (Exception ignored) {
        }
        return "";
    }

    /**
     * ✅ Borrar SOLO evidencias previas:
     * - se eliminan adjuntos cuyo filename tenga patrón de fecha en el nombre (EVIDENCIA_* normalmente)
     * - NO borra otros adjuntos del ticket
     */
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

            if (borrados > 0) System.out.println("   🗑️ Evidencias borradas: " + borrados);
        } catch (Exception e) {
            System.err.println("   ❌ Error borrando evidencias: " + e.getMessage());
        }
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
                    String nombreFinalJira = jsonResponse.get(0).path("filename").asText();
                    System.out.println("   📎 Evidencia subida: " + nombreFinalJira);
                    return nombreFinalJira;
                }
            } else {
                System.err.println("   ❌ Error subiendo evidencia: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.err.println("   ❌ Excepción subiendo evidencia: " + e.getMessage());
        }
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
                System.out.println("   ✅ " + nombreLog + " OK");
                return true;
            } else {
                System.err.println("   ⚠️ Falló " + nombreLog + " (" + response.statusCode() + "): " + response.body());
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void cambiarEstadoTicket(String issueKey, String estadoDestino) {
        try {
            HttpRequest getReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth).GET().build();
            HttpResponse<String> getRes = client.send(getReq, HttpResponse.BodyHandlers.ofString());
            JsonNode transitions = mapper.readTree(getRes.body()).path("transitions");

            String transitionId = null;
            for (JsonNode t : transitions) {
                if (t.path("to").path("name").asText().equalsIgnoreCase(estadoDestino)) {
                    transitionId = t.path("id").asText();
                    break;
                }
            }
            if (transitionId == null) return;

            ObjectNode payload = mapper.createObjectNode();
            payload.putObject("transition").put("id", transitionId);
            HttpRequest postReq = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/3/issue/" + issueKey + "/transitions"))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
            client.send(postReq, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {
        }
    }

    // =========================================================================
    // MÉTODOS AUXILIARES (EXCEL Y UTILIDADES)
    // =========================================================================

    private static byte[] createMultipartBody(File file, String boundary) throws Exception {
        String fileName = file.getName();

        // Detectar tipo MIME para que Jira renderice la vista previa correctamente
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
        } catch (Exception e) {
            return null;
        }
    }

    private static void agregarActivo(ObjectNode values, String fieldId, String wsId, String objId) {
        values.putArray(fieldId).addObject()
                .put("workspaceId", wsId)
                .put("id", wsId + ":" + objId)
                .put("objectId", objId);
    }

    private static String[] obtenerConfiguracionItem(String numeroItem) {
        switch (numeroItem) {
            case "1":
                return new String[]{"126", "customfield_10250", "2"};
            case "2":
                return new String[]{"127", "customfield_10251", "1"};
            case "3":
                return new String[]{"128", "customfield_10252", "3"};
            case "4":
                return new String[]{"129", "customfield_10253", "4"};
            default:
                return new String[]{"129", "customfield_10253", ""};
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

    // ✅ Caso Jira con offset Perú: 2026-03-07T17:59:10.000-0500
    // Mantiene la hora "17:59:10" y descarta "-0500" para poder obtener LocalDateTime.
    try {
        if (val.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]\\d{4}$")) {
            String sinZona = val.substring(0, 23); // yyyy-MM-ddTHH:mm:ss.SSS
            return LocalDateTime.parse(sinZona, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        }
    } catch (Exception ignored) { }

    // ✅ Formatos típicos de Excel
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
            // Si solo hay fecha, asumimos 00:00:00
            if (val.length() == 10 && val.contains("/")) {
                return LocalDateTime.parse(val + " 00:00:00",
                        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            }
            if (val.length() == 10 && val.contains("-")) {
                return LocalDateTime.parse(val + " 00:00:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            return LocalDateTime.parse(val, f);
        } catch (Exception ignored) { }
    }

    return null;
}

    private static boolean isBorrar(String v) {
        return v != null && v.trim().equalsIgnoreCase(PALABRA_BORRAR);
    }

    private static void putStringOrClear(ObjectNode fields, String key, String value) {
        if (isBorrar(value)) {
            fields.putNull(key);
            return;
        }
        if (value != null && !value.trim().isEmpty()) {
            fields.put(key, value.trim());
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
}