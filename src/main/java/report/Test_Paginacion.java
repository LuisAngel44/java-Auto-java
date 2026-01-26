package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class Test_Paginacion {

    private static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";
    
    // IDs (Asegúrate que el 10 o 13 sea el correcto para Items)
    private static final int ID_COLEGIO = 9;   
    private static final int ID_ITEM_SERVICIO = 10; 

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

        System.out.println("🚀 INICIANDO DESCARGA (Modo GET Forzado)...");

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            
            // --- HOJA 1 ---
            System.out.println("\n📊 1. Descargando COLEGIOS (ID " + ID_COLEGIO + ")...");
            descargarYFormatear(workbook, "Maestro_Colegios", ID_COLEGIO);

            // --- HOJA 2 ---
            System.out.println("\n📊 2. Descargando ITEMS (ID " + ID_ITEM_SERVICIO + ")...");
            descargarYFormatear(workbook, "Maestro_Items_Servicios", ID_ITEM_SERVICIO);

            try (FileOutputStream fileOut = new FileOutputStream("Reporte_Vinculacion_Assets_GET.xlsx")) {
                workbook.write(fileOut);
                System.out.println("\n✅ ¡EXITO TOTAL! Archivo: Reporte_Vinculacion_Assets_GET.xlsx");
            }
            workbook.dispose();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void descargarYFormatear(SXSSFWorkbook workbook, String sheetName, int objectTypeId) {
        Sheet sheet = workbook.createSheet(sheetName);
        crearEncabezados(workbook, sheet);

        int filaActual = 1;
        int pagina = 1;
        boolean hayMasPaginas = true;
        int totalDescargados = 0;
        String ultimoKey = ""; 

        while (hayMasPaginas) {
            try {
                // 1. Preparamos la Query (AQL) codificada para URL
                // Ordenamos por Key ASC para estabilidad
                String aql = "objectTypeId = " + objectTypeId + " ORDER BY Key ASC";
                String aqlEncoded = URLEncoder.encode(aql, StandardCharsets.UTF_8);

                // 2. Construimos la URL con TODOS los parámetros visibles
                String url = String.format(
                    "https://api.atlassian.com/jsm/assets/workspace/%s/v1/object/aql?qlQuery=%s&page=%d&resultPerPage=50",
                    WORKSPACE_ID, aqlEncoded, pagina
                );

                // 3. Usamos GET (Sin Body)
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Accept", "application/json")
                        .GET() 
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = mapper.readTree(response.body());
                    JsonNode values = root.path("values");

                    if (values.isEmpty()) {
                        hayMasPaginas = false;
                        break;
                    }

                    // CHECK ANTI-BUCLE (Por si acaso)
                    String keyActual = values.get(0).path("objectKey").asText();
                    if (keyActual.equals(ultimoKey)) {
                        System.out.println("⚠️ ALERTA: Bucle detectado en página " + pagina + ". Deteniendo hoja.");
                        hayMasPaginas = false;
                        break;
                    }
                    ultimoKey = keyActual;

                    for (JsonNode objeto : values) {
                        Row row = sheet.createRow(filaActual++);
                        
                        String objectId = objeto.path("id").asText();
                        row.createCell(0).setCellValue(objectId); 
                        row.createCell(1).setCellValue(objeto.path("label").asText()); 

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
                                if (colIndex < 15) row.createCell(colIndex++).setCellValue(valor);
                            }
                        }
                        totalDescargados++;
                    }
                    
                    if (pagina % 10 == 0) System.out.print(".");
                    pagina++; 

                } else {
                    System.err.println("❌ Error Pág " + pagina + ": " + response.statusCode());
                    hayMasPaginas = false; 
                }
            } catch (Exception e) {
                e.printStackTrace();
                hayMasPaginas = false;
            }
        }
        System.out.println("\n   ✅ Completado: " + totalDescargados + " objetos en '" + sheetName + "'.");
    }

    private static void crearEncabezados(SXSSFWorkbook wb, Sheet sheet) {
        String[] headers = {
            "ID", "Nombre", "JSON PARA COPIAR", "Key", 
            "Attr 1", "Attr 2", "Attr 3", "Attr 4", "Attr 5", "Attr 6", "Attr 7", "Attr 8", "Attr 9", "Attr 10"
        };
        Row row = sheet.createRow(0);
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i=0; i<headers.length; i++) {
            Cell c = row.createCell(i);
            c.setCellValue(headers[i]);
            c.setCellStyle(style);
        }
    }
}