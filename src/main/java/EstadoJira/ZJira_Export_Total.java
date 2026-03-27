package EstadoJira;

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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class ZJira_Export_Total {

    // === IDs de Campos (Actualizados con el Spy de MSP-5517) ===
    private static final String ID_COD_LOCAL        = "customfield_10168";
    private static final String ID_COD_MODULAR      = "customfield_10169";
    private static final String ID_NOMBRE_IE        = "customfield_10359";
    private static final String ID_DEPARTAMENTO     = "customfield_10355";
    private static final String ID_PROVINCIA        = "customfield_10356";
    private static final String ID_DISTRITO         = "customfield_10357";
    private static final String ID_CENTRO_POBLADO   = "customfield_10287";
    private static final String ID_DIRECCION        = "customfield_10358";
    
    private static final String ID_NOMBRE_CONTACTO  = "customfield_10090";
    private static final String ID_NUMERO_CONTACTO  = "customfield_10091";
    
    private static final String ID_ITEM             = "customfield_10249";
    private static final String ID_IMPUTABILIDAD    = "customfield_10471";
    private static final String ID_TIPO_INCIDENCIA  = "customfield_10469";
    private static final String ID_CATEGORIA_SERV   = "customfield_10394";
    private static final String ID_AREA             = "customfield_10504";
    private static final String ID_TIPO_TICKET      = "customfield_10176";
    private static final String ID_CANAL_COMUNIC    = "customfield_10286";
    private static final String ID_MEDIO_TX         = "customfield_10361";
    
    private static final String ID_DESCRIP_INCIDENTE= "customfield_10180";
    private static final String ID_DESCRIP_SOLUCION = "customfield_10089";
    
    private static final String ID_TIEMPO_NO_DISP   = "customfield_10178";
    private static final String ID_TIEMPO_SOLUCION  = "customfield_10177";
    
    private static final String ID_FECHA_GEN_MANUAL = "customfield_10321";
    private static final String ID_FECHA_SOL_MANUAL = "customfield_10322";

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Reporte Total MSP");

        // === Encabezados Actualizados ===
        String[] encabezados = {
                "Clave / Ticket", "Estado", "Tipo de Solicitud", 
                "Código de Local", "Código Modular", "Nombre de la IE",
                "Departamento", "Provincia", "Distrito", "Centro Poblado", "Dirección",
                "Nombre de Contacto", "Número de Contacto",
                "Ítem al que pertenece", "Área", "Canal de Comunicación", 
                "Categoría Servicio", "Tipo de Incidencia", "Tipo Ticket", "Imputabilidad", "Medio TX",
                "Descripción del incidente", "Descripción Solución", 
                "Fecha Generación", "Fecha Solución",
                "Tiempo Solución (H:M calc)", "Tiempo Solución (Jira)", "Tiempo no disponibilidad"
        };

        // Estilo de encabezado
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
        boolean tieneMasPaginas = true;
        int filaActual = 1;
        
        // JQL para traer TODO el proyecto MSP
        String jqlQuery = "project = 'MSP' ORDER BY created ASC";

        System.out.println("🚀 Iniciando extracción total de tickets (Con todos los campos del Spy)...");

        try {
            while (tieneMasPaginas) {
                ObjectNode payload = mapper.createObjectNode();
                payload.put("jql", jqlQuery);
                payload.put("maxResults", 100); 
                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

                // Solicitamos todos los campos necesarios a Jira
                ArrayNode fields = payload.putArray("fields");
                fields.add("status").add("created").add("resolutiondate").add("issuetype")
                        .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
                        .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO).add(ID_CENTRO_POBLADO).add(ID_DIRECCION)
                        .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
                        .add(ID_ITEM).add(ID_AREA).add(ID_CANAL_COMUNIC)
                        .add(ID_CATEGORIA_SERV).add(ID_TIPO_INCIDENCIA).add(ID_TIPO_TICKET).add(ID_IMPUTABILIDAD).add(ID_MEDIO_TX)
                        .add(ID_DESCRIP_INCIDENTE).add(ID_DESCRIP_SOLUCION)
                        .add(ID_FECHA_GEN_MANUAL).add(ID_FECHA_SOL_MANUAL)
                        .add(ID_TIEMPO_SOLUCION).add(ID_TIEMPO_NO_DISP);

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

                        // 1. Datos del Ticket
                        row.createCell(c++).setCellValue(issue.path("key").asText());
                        row.createCell(c++).setCellValue(f.path("status").path("name").asText());
                        row.createCell(c++).setCellValue(obtenerTexto(f.path("issuetype")));

                        // 2. Ubicación y Colegio
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_COD_LOCAL)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_COD_MODULAR)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NOMBRE_IE)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_DEPARTAMENTO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_PROVINCIA)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_DISTRITO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_CENTRO_POBLADO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_DIRECCION)));

                        // 3. Contacto
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NOMBRE_CONTACTO)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_NUMERO_CONTACTO)));

                        // 4. Clasificación del Ticket
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_ITEM)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_AREA)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_CANAL_COMUNIC)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_CATEGORIA_SERV)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIPO_INCIDENCIA)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIPO_TICKET)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_IMPUTABILIDAD)));
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_MEDIO_TX)));

                        // 5. Descripciones (Manejo de formato Doc/ADF)
                        row.createCell(c++).setCellValue(extraerTextoADF(f.path(ID_DESCRIP_INCIDENTE)));
                        row.createCell(c++).setCellValue(extraerTextoADF(f.path(ID_DESCRIP_SOLUCION)));

                        // 6. Fechas
                        String fechaUsoGen = f.path(ID_FECHA_GEN_MANUAL).asText("");
                        if (fechaUsoGen.isBlank() || fechaUsoGen.equals("null")) fechaUsoGen = f.path("created").asText("");

                        String fechaUsoSol = f.path(ID_FECHA_SOL_MANUAL).asText("");
                        if (fechaUsoSol.isBlank() || fechaUsoSol.equals("null")) fechaUsoSol = f.path("resolutiondate").asText("");

                        row.createCell(c++).setCellValue(formatearFecha(fechaUsoGen));
                        row.createCell(c++).setCellValue(formatearFecha(fechaUsoSol));

                        // 7. Tiempos
                        row.createCell(c++).setCellValue(calcularTiempo(fechaUsoGen, fechaUsoSol)); // Tiempo calculado por Java
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIEMPO_SOLUCION))); // El número decimal que guarda Jira
                        row.createCell(c++).setCellValue(obtenerTexto(f.path(ID_TIEMPO_NO_DISP)));
                    }

                    System.out.println("⏳ Procesados: " + (filaActual - 1) + " tickets...");

                    if (root.has("nextPageToken") && !root.get("nextPageToken").asText().isEmpty()) {
                        nextPageToken = root.get("nextPageToken").asText();
                    } else {
                        tieneMasPaginas = false;
                    }

                } else {
                    System.err.println("❌ Error en la API: " + response.body());
                    break;
                }
            }

            // Ajustar el ancho de las columnas para que se vea bien
            for (int i = 0; i < encabezados.length; i++) {
                sheet.autoSizeColumn(i);
            }

            String nombreArchivo = "Reporte_Jira_TOTAL_MSP_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";

            try (FileOutputStream out = new FileOutputStream(nombreArchivo)) {
                workbook.write(out);
            }
            workbook.close();

            System.out.println("✅ EXTRACCIÓN COMPLETADA");
            System.out.println("📊 Total registros: " + (filaActual - 1));
            System.out.println("📁 Archivo guardado como: " + nombreArchivo);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- MÉTODOS AUXILIARES ---

    private static String extraerTextoADF(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        if (node.isTextual()) return node.asText();
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
        } catch (Exception e) { return ""; }
        return sb.toString().trim();
    }

    private static String obtenerTexto(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return "";
        // Prioriza "value" para campos tipo Opción (Select List)
        if (node.has("value")) return node.get("value").asText("");
        // Prioriza "name" para campos tipo Objeto (como issuetype)
        if (node.has("name")) return node.get("name").asText("");
        
        // Si es un array (ej. Assets, Casillas de verificación)
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode n : node) {
                if (sb.length() > 0) sb.append(", ");
                if (n.has("value")) sb.append(n.get("value").asText());
                else if (n.has("name")) sb.append(n.get("name").asText());
                else sb.append(n.asText());
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
        } catch (Exception e) { return "-"; }
    }

    private static String formatearFecha(String dateStr) {
        if (dateStr == null || dateStr.isEmpty() || "null".equals(dateStr)) return "";
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ"));
            return zdt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        } catch (Exception e) { return dateStr; }
    }
}