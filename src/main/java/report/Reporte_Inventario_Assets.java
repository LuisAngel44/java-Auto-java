package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Reporte_Inventario_Assets{

    private static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // IDs CLAVE (Basado en tu inspector anterior)
    private static final int ID_COLEGIO = 9;   // "Center Education"
    private static final int ID_ITEM_SERVICIO = 11; // "Item" (Probablemente tus servicios 1,2,3,4)

    private static String encodedAuth;
    private static HttpClient client;
    private static ObjectMapper mapper;

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String email = dotenv.get("JIRA_EMAIL");
        String token = dotenv.get("JIRA_TOKEN");

        if (email == null || token == null) {
            System.err.println("❌ ERROR: Faltan credenciales en .env");
            return;
        }

        encodedAuth = Base64.getEncoder().encodeToString((email.trim() + ":" + token.trim()).getBytes());
        mapper = new ObjectMapper();
        client = HttpClient.newHttpClient();

        // Usamos SXSSF para manejar miles de datos sin memoria llena
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            
            // --- HOJA 1: COLEGIOS ---
            // Aquí obtendrás el JSON para 'customfield_10170'
            System.out.println("📊 Descargando COLEGIOS (ID 9)...");
            descargarYFormatear(workbook, "Maestro_Colegios", ID_COLEGIO);

            // --- HOJA 2: ITEMS / SERVICIOS ---
            // Aquí obtendrás los JSON para Item 1, 2, 3, 4 (customfield_10250-53)
            // Busca en los atributos el nombre del colegio para saber a cuál pertenece.
            System.out.println("\n📊 Descargando ITEMS DE SERVICIO (ID 10)...");
            descargarYFormatear(workbook, "Maestro_Items_Servicios", ID_ITEM_SERVICIO);

            // Guardar Excel
            try (FileOutputStream fileOut = new FileOutputStream("Reporte_Vinculacion_Assets.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n✅ ¡LISTO! Archivo generado: Reporte_Vinculacion_Assets.xlsx");
            }
            workbook.dispose();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void descargarYFormatear(SXSSFWorkbook workbook, String sheetName, int objectTypeId) {
        Sheet sheet = workbook.createSheet(sheetName);
        
        String[] headers = {
            "ID Objeto", "Nombre (Label)", "JSON PARA COPIAR", "Key", 
            "Attr 1", "Attr 2", "Attr 3", "Attr 4", "Attr 5", "Attr 6", "Attr 7", "Attr 8"
        };
        
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int filaActual = 1;
        int pagina = 1;
        boolean hayMasPaginas = true;
        int totalDescargados = 0;

        // URL LIMPIA (Sin parámetros, porque todo va en el Body)
        String url = "https://api.atlassian.com/jsm/assets/workspace/" + WORKSPACE_ID + "/v1/object/aql";

        while (hayMasPaginas) {
            try {
                // 🛑 CORRECCIÓN CLAVE: La paginación va AQUÍ, dentro del JSON
                ObjectNode payload = mapper.createObjectNode();
                payload.put("qlQuery", "objectTypeId = " + objectTypeId); 
                payload.put("page", pagina);         // <--- Esto faltaba en el Body
                payload.put("resultPerPage", 50);    // <--- Esto también

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode values = root.path("values");

                    // Si la lista viene vacía, SIGNIFICA QUE TERMINAMOS REALMENTE
                    if (values.isEmpty()) {
                        hayMasPaginas = false;
                        break;
                    }

                    for (JsonNode objeto : values) {
                        Row row = sheet.createRow(filaActual++);
                        
                        String objectId = objeto.path("id").asText();
                        String label = objeto.path("label").asText();
                        
                        row.createCell(0).setCellValue(objectId);
                        row.createCell(1).setCellValue(label);

                        // JSON LISTO PARA JIRA
                        String jsonString = String.format(
                            "[{\"workspaceId\":\"%s\",\"id\":\"%s:%s\",\"objectId\":\"%s\"}]",
                            WORKSPACE_ID, WORKSPACE_ID, objectId, objectId
                        );
                        row.createCell(2).setCellValue(jsonString);

                        row.createCell(3).setCellValue(objeto.path("objectKey").asText());

                        int colIndex = 4;
                        for (JsonNode attr : objeto.path("attributes")) {
                            JsonNode attrValues = attr.path("objectAttributeValues");
                            if (attrValues.size() > 0) {
                                String valor = attrValues.get(0).path("displayValue").asText();
                                if (valor.isEmpty()) valor = attrValues.get(0).path("value").asText();
                                if (colIndex < 12) row.createCell(colIndex++).setCellValue(valor);
                            }
                        }
                        totalDescargados++;
                    }
                    
                    pagina++; // Avanzamos a la siguiente página
                    if (totalDescargados % 500 == 0) System.out.print("."); 

                } else {
                    System.err.println("❌ Error Pág " + pagina + ": " + response.statusCode());
                    hayMasPaginas = false; 
                }
            } catch (Exception e) {
                e.printStackTrace();
                hayMasPaginas = false;
            }
        }
        System.out.println("\n   -> Total Real: " + totalDescargados + " objetos únicos en '" + sheetName + "'.");
    }
}