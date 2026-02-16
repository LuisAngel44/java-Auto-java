package report;

import io.github.cdimascio.dotenv.Dotenv;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class espia2 {

    public static void main(String[] args) {
        // 1. PON AQUÍ UN TICKET QUE ESTÉ "EN CURSO" O "PENDIENTE"
        // (Si pones uno resuelto, no te saldrá el ID para resolverlo)
        String TICKET_DE_PRUEBA = "MSP-39832"; 

        // Carga credenciales
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        String jiraUrl = dotenv.get("JIRA_URL").trim().replaceAll("/$", "");
        String email = dotenv.get("JIRA_EMAIL").trim();
        String token = dotenv.get("JIRA_TOKEN").trim();
        String auth = Base64.getEncoder().encodeToString((email + ":" + token).getBytes());

        try {
            System.out.println("🕵️‍♂️ ESPIANDO TRANSICIONES PARA: " + TICKET_DE_PRUEBA);
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jiraUrl + "/rest/api/2/issue/" + TICKET_DE_PRUEBA + "/transitions"))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Imprimimos el resultado crudo pero legible
            String json = response.body();
            
            // Un truco simple para imprimirlo más bonito sin librerías extra:
            System.out.println(json.replace("},{", "},\n{")); 

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}