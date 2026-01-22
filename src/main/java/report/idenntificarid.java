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

	public class idenntificarid {
	
		// =========================================================================
	    // ⚙️ CONFIGURACIÓN DE IDs (Confirmados con tu lista)
	    // =========================================================================
	    
	    // Datos del Local
	    private static final String ID_COD_LOCAL       = "customfield_10168";
	    private static final String ID_COD_MODULAR     = "customfield_10169";
	    private static final String ID_NOMBRE_IE       = "customfield_10359";
	    
	    // Ubicación
	    private static final String ID_DEPARTAMENTO    = "customfield_10355";
	    private static final String ID_PROVINCIA       = "customfield_10356";
	    private static final String ID_DISTRITO        = "customfield_10357";
	    
	    // Contacto
	    private static final String ID_NOMBRE_CONTACTO = "customfield_10090";
	    private static final String ID_NUMERO_CONTACTO = "customfield_10091";

	    // Datos Técnicos / Ticket
	    private static final String ID_ITEM            = "customfield_10249";
	    private static final String ID_TIPO_INCIDENCIA = "customfield_10469";
	    private static final String ID_DESCRIP_SOLUCION= "customfield_10089";
	    private static final String ID_MEDIO_TX        = "customfield_10361";
	    private static final String ID_TIEMPO_NO_DISP  = "customfield_10178";
	    
	    // --- ASSETS / INSIGHT ---
	    private static final String ID_INSTITUCION_ASSET = "customfield_10170"; 

	    // --- LOS 4 CAMPOS DE DISPOSITIVOS (Unified Logic) ---
	    private static final String ID_DEV_ITEM_1 = "customfield_10250"; // Affected Devices of Item 1
	    private static final String ID_DEV_ITEM_2 = "customfield_10251"; // Affected Devices of Item 2
	    private static final String ID_DEV_ITEM_3 = "customfield_10252"; // Affected Devices of Item 3
	    private static final String ID_DEV_ITEM_4 = "customfield_10253"; // Affected Devices of Item 4

	    // Campos Estándar
	    private static final String FIELD_SUMMARY = "summary";
	    private static final String FIELD_STATUS = "status";
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
	        Sheet sheet = workbook.createSheet("Reporte Minedu Unificado");

	        // DEFINICIÓN DE COLUMNAS (Total 21)
	        // Nota: Solo hay UNA columna para "Dispositivo Afectado"
	        String[] encabezados = {
	            "Código de Local", "Código Modular", "Nombre de la IE", "Departamento", 
	            "Provincia", "Distrito", "Nombre de Contacto", "Número de Contacto", 
	            "Ítem", "Clave", "Tipo de Incidencia", "Estado", 
	            "Descripción", "Fecha Generación", "Fecha Solución", 
	            "Descripción Solución", "Tiempo solución", "No disponibilidad", "Medio transmisión",
	            "IE (Asset ID)", 
	            "Dispositivo Afectado (Cualquier Item)" // <--- Columna Inteligente
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

	        String nextPageToken = null;
	        boolean seguirBuscando = true;
	        int filaActual = 1;

	        System.out.println("🚀 Iniciando reporte unificado (Item 1, 2, 3, 4)...");

	        try {
	            while (seguirBuscando) {
	                ObjectNode payload = mapper.createObjectNode();
	                payload.put("jql", "project = 'MSP' ORDER BY created DESC");
	                payload.put("maxResults", 100);
	                if (nextPageToken != null) payload.put("nextPageToken", nextPageToken);

	                // Solicitamos TODOS los campos necesarios a la API
	                ArrayNode fields = payload.putArray("fields");
	                fields.add(FIELD_SUMMARY).add(FIELD_STATUS).add(FIELD_CREATED).add(FIELD_RESOLUTION_DATE)
	                      .add(ID_COD_LOCAL).add(ID_COD_MODULAR).add(ID_NOMBRE_IE)
	                      .add(ID_DEPARTAMENTO).add(ID_PROVINCIA).add(ID_DISTRITO)
	                      .add(ID_NOMBRE_CONTACTO).add(ID_NUMERO_CONTACTO)
	                      .add(ID_ITEM).add(ID_TIPO_INCIDENCIA)
	                      .add(ID_DESCRIP_SOLUCION).add(ID_MEDIO_TX).add(ID_TIEMPO_NO_DISP)
	                      .add(ID_INSTITUCION_ASSET)
	                      // IMPORTANTE: Pedimos los 4 posibles campos de dispositivos
	                      .add(ID_DEV_ITEM_1).add(ID_DEV_ITEM_2).add(ID_DEV_ITEM_3).add(ID_DEV_ITEM_4);

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

	                        // Celdas 0-18 (Datos estándar)
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
	                        row.createCell(12).setCellValue(f.path("summary").asText());
	                        
	                        String fechaCreacion = f.path("created").asText();
	                        String fechaSolucion = f.path("resolutiondate").asText(null);
	                        
	                        row.createCell(13).setCellValue(formatearFecha(fechaCreacion));
	                        row.createCell(14).setCellValue(formatearFecha(fechaSolucion));
	                        row.createCell(15).setCellValue(obtenerTexto(f.path(ID_DESCRIP_SOLUCION)));
	                        row.createCell(16).setCellValue(calcularTiempo(fechaCreacion, fechaSolucion));
	                        row.createCell(17).setCellValue(obtenerTexto(f.path(ID_TIEMPO_NO_DISP)));
	                        row.createCell(18).setCellValue(obtenerTexto(f.path(ID_MEDIO_TX)));

	                        // --- 19. IE Asset (10170) ---
	                        row.createCell(19).setCellValue(obtenerTexto(f.path(ID_INSTITUCION_ASSET)));

	                        // --- 20. DISPOSITIVO UNIFICADO ---
	                        // Busca en los 4 cajones y trae el que tenga datos
	                        String dispositivoEncontrado = buscarDispositivoEnItems(f);
	                        row.createCell(20).setCellValue(dispositivoEncontrado);
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

	            for (int i = 0; i < encabezados.length; i++) sheet.autoSizeColumn(i);

	            try (FileOutputStream fileOut = new FileOutputStream("Reporte_Jira_Final.xlsx")) {
	                workbook.write(fileOut);
	            }
	            workbook.close();
	            System.out.println("🎉 ¡Excel generado con éxito!");

	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    // =========================================================================
	    // 🧠 LÓGICA DE BÚSQUEDA INTELIGENTE
	    // =========================================================================
	    private static String buscarDispositivoEnItems(JsonNode fields) {
	        // Intento 1: ¿Está en Affected Devices of Item 1?
	        String d1 = obtenerTexto(fields.path(ID_DEV_ITEM_1));
	        if (!d1.isEmpty()) return d1; // Retorna limpio

	        // Intento 2: ¿Está en Affected Devices of Item 2?
	        String d2 = obtenerTexto(fields.path(ID_DEV_ITEM_2));
	        if (!d2.isEmpty()) return d2;

	        // Intento 3: ¿Está en Affected Devices of Item 3?
	        String d3 = obtenerTexto(fields.path(ID_DEV_ITEM_3));
	        if (!d3.isEmpty()) return d3;

	        // Intento 4: ¿Está en Affected Devices of Item 4?
	        String d4 = obtenerTexto(fields.path(ID_DEV_ITEM_4));
	        if (!d4.isEmpty()) return d4;

	        return ""; // Si los 4 están vacíos
	    }

	    private static String obtenerTexto(JsonNode node) {
	        if (node.isMissingNode() || node.isNull()) return "";
	        
	        // Manejo de Arrays (Assets o Multi-select)
	        if (node.isArray()) {
	            StringBuilder sb = new StringBuilder();
	            for (JsonNode n : node) {
	                if (sb.length() > 0) sb.append(", ");
	                
	                if (n.has("objectId")) sb.append(n.get("objectId").asText()); // PRIORIDAD 1: Asset ID
	                else if (n.has("value")) sb.append(n.get("value").asText());  // PRIORIDAD 2: Dropdown
	                else sb.append(n.asText());
	            }
	            return sb.toString();
	        }

	        // Manejo de Objeto simple (Asset único)
	        if (node.isObject() && node.has("objectId")) {
	            return node.get("objectId").asText();
	        }

	        // Manejo de Dropdown simple
	        if (node.has("value")) {
	            String val = node.get("value").asText();
	            if (node.has("child") && node.get("child").has("value")) {
	                return val + " - " + node.get("child").get("value").asText();
	            }
	            return val;
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