package com.function;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

/**
 * Función Azure que implementa una consulta GraphQL para obtener usuarios
 */
public class Function {
    // Variables de configuración y utilidades
    private final String BACKEND_URL;
    private final String SERVERLESS_SECRET_KEY;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // Constructor: inicializa configuraciones y valida variables de entorno
    public Function() {
        this.BACKEND_URL = System.getenv("BACKEND_URL");
        this.SERVERLESS_SECRET_KEY = System.getenv("SERVERLESS_SECRET_KEY");
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newHttpClient();

        if (BACKEND_URL == null || BACKEND_URL.trim().isEmpty()) {
            throw new RuntimeException("BACKEND_URL no está configurada");
        }
        if (SERVERLESS_SECRET_KEY == null || SERVERLESS_SECRET_KEY.trim().isEmpty()) {
            throw new RuntimeException("SERVERLESS_SECRET_KEY no está configurada");
        }
    }

    /**
     * Endpoint principal que procesa la consulta GraphQL
     * @param request Petición HTTP recibida
     * @param context Contexto de ejecución de Azure Functions
     * @return Respuesta HTTP con los usuarios en formato GraphQL
     */
    @FunctionName("GraphQLUsers")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.POST},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        try {
            // Genera firma de autenticación para el backend
            String signature = generateSignature();
            
            // Construye la consulta GraphQL
            String graphqlQuery = "{ \"query\": \"{ users { id username email roles { name } } }\" }";
            
            // Prepara la petición HTTP al backend
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(BACKEND_URL + "/api/users"))
                .header("Content-Type", "application/json")
                .header("serverlessSignature", signature)
                .GET()
                .build();

            // Envía la petición y obtiene la respuesta
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            // Procesa la respuesta según el código de estado
            if (response.statusCode() == 200) {
                // Convierte la respuesta a formato GraphQL
                String graphqlResponse = convertToGraphQLResponse(response.body());
                return request.createResponseBuilder(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(graphqlResponse)
                    .build();
            } else {
                return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al consultar usuarios: " + response.body())
                    .build();
            }

        } catch (Exception e) {
            // Maneja errores y los registra
            context.getLogger().severe("Error en la función: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error: " + e.getMessage())
                .build();
        }
    }

    /**
     * Genera una firma HMAC para autenticar la petición
     * @return Firma en formato timestamp:hash
     */
    private String generateSignature() {
        try {
            // Genera timestamp actual
            long timestamp = Instant.now().getEpochSecond();
            String data = timestamp + ":" + BACKEND_URL;
            
            // Crea y configura HMAC-SHA256
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(SERVERLESS_SECRET_KEY.getBytes(), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            
            // Genera y codifica el hash
            String hash = Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(data.getBytes()));
            return timestamp + ":" + hash;
        } catch (Exception e) {
            throw new RuntimeException("Error generando firma: " + e.getMessage());
        }
    }

    /**
     * Convierte la respuesta JSON del backend a formato GraphQL
     * @param jsonResponse Respuesta JSON del backend
     * @return Respuesta en formato GraphQL
     */
    private String convertToGraphQLResponse(String jsonResponse) {
        try {
            // Parsea la respuesta y la estructura en formato GraphQL
            JsonNode users = objectMapper.readTree(jsonResponse);
            Map<String, Object> response = new HashMap<>();
            response.put("data", Map.of("users", users));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            throw new RuntimeException("Error convirtiendo respuesta: " + e.getMessage());
        }
    }
}
