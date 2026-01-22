package report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class Assets_Inspector_Types {

    // Tu Workspace ID (Confirmado de tus logs anteriores)
    private static final String WORKSPACE_ID = "01cf423f-729d-4ecc-9da9-3df244069bb5";

    public static void main(String[] args) {
        Dotenv dotenv = Dotenv.load();
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String encodedAuth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("🔍 1. Buscando ESQUEMAS (Carpetas principales)...");
            
            // Endpoint para listar Esquemas
            String urlSchemas = "https://api.atlassian.com/jsm/assets/workspace/" + WORKSPACE_ID + "/v1/objectschema/list";
            
            HttpRequest reqSchema = HttpRequest.newBuilder()
                    .uri(URI.create(urlSchemas))
                    .header("Authorization", "Basic " + encodedAuth)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> respSchema = client.send(reqSchema, HttpResponse.BodyHandlers.ofString());
            
            if (respSchema.statusCode() != 200) {
                System.out.println("❌ Error Auth/Conexión: " + respSchema.statusCode());
                return;
            }

            JsonNode rootSchemas = mapper.readTree(respSchema.body());
            JsonNode valuesSchemas = rootSchemas.path("values");

            System.out.println("✅ Se encontraron " + valuesSchemas.size() + " Esquemas.");
            
            // Recorrer cada Esquema para buscar sus Tipos de Objeto
            for (JsonNode schema : valuesSchemas) {
                String schemaId = schema.path("id").asText();
                String schemaName = schema.path("name").asText();
                
                System.out.println("\n📂 ESQUEMA: " + schemaName + " (ID: " + schemaId + ")");
                System.out.println("   ----------------------------------------------------");

                // Buscar Tipos de Objetos dentro de este esquema
                String urlTypes = "https://api.atlassian.com/jsm/assets/workspace/" + WORKSPACE_ID + 
                                  "/v1/objectschema/" + schemaId + "/objecttypes";

                HttpRequest reqTypes = HttpRequest.newBuilder()
                        .uri(URI.create(urlTypes))
                        .header("Authorization", "Basic " + encodedAuth)
                        .header("Accept", "application/json")
                        .GET()
                        .build();

                HttpResponse<String> respTypes = client.send(reqTypes, HttpResponse.BodyHandlers.ofString());
                JsonNode types = mapper.readTree(respTypes.body());

                for (JsonNode type : types) {
                    String typeName = type.path("name").asText();
                    String typeId = type.path("id").asText();
                    
                    // IMPRIMIR NOMBRE Y ID
                    System.out.println("   👉 Tipo: [" + typeName + "]  --->  ID: " + typeId);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}