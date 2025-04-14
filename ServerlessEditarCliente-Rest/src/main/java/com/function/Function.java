package com.function;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
 * Azure Function para actualizar datos de empleados
 */
public class Function {
    private String BACKEND_URL;
    private String SERVERLESS_SECRET_KEY;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Function() {
        this.BACKEND_URL = System.getenv("BACKEND_URL");
        this.SERVERLESS_SECRET_KEY = System.getenv("SERVERLESS_SECRET_KEY");
    }

    /**
     * Función HTTP para actualizar datos de empleados
     */
    @FunctionName("HttpExample")
    public HttpResponseMessage run(
            @HttpTrigger(
                name = "req",
                methods = {HttpMethod.PUT},
                authLevel = AuthorizationLevel.ANONYMOUS)
                HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        
        context.getLogger().info("Procesando solicitud de actualización de empleado");

        // Validar token JWT
        String authHeader = null;
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getKey().equalsIgnoreCase("authorization")) {
                authHeader = entry.getValue();
                break;
            }
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return request.createResponseBuilder(HttpStatus.UNAUTHORIZED)
                .body("{\"error\": \"Se requiere token JWT de autenticación\"}")
                .build();
        }

        // Extraer el token JWT
        String token = authHeader.substring(7).trim();

        // Validar el cuerpo de la solicitud
        String requestBody = request.getBody().orElse("");
        if (requestBody.isEmpty()) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                .body("{\"error\": \"Se requieren datos del empleado\"}")
                .build();
        }

        try {
            // Validar JSON del cuerpo
            Map<String, Object> employeeData = objectMapper.readValue(requestBody, Map.class);
            
            if (!employeeData.containsKey("email") || !employeeData.containsKey("username")) {
                return request.createResponseBuilder(HttpStatus.BAD_REQUEST)
                    .body("{\"error\": \"Se requiere email y username del empleado\"}")
                    .build();
            }

            // Generar firma para autenticación
            String signature = generateSignature(requestBody);

            // Validar configuración
            if (BACKEND_URL == null || BACKEND_URL.trim().isEmpty()) {
                throw new RuntimeException("BACKEND_URL no está configurada");
            }

            // Construir URL completa
            String fullUrl = BACKEND_URL + (BACKEND_URL.endsWith("/") ? "" : "/") + "api/update/employee";
            context.getLogger().info("URL del backend: " + fullUrl);

            // Preparar petición HTTP
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("serverlessSignature", signature)
                .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            // Enviar petición
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(httpRequest, HttpResponse.BodyHandlers.ofString());

            context.getLogger().info("Respuesta del backend: " + response.statusCode());
            context.getLogger().info("Cuerpo de la respuesta: " + response.body());

            // Procesar respuesta
            return request.createResponseBuilder(HttpStatus.valueOf(response.statusCode()))
                .header("Content-Type", "application/json")
                .body(response.body())
                .build();

        } catch (Exception e) {
            context.getLogger().severe("Error: " + e.getMessage());
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("{\"error\": \"Error al procesar la solicitud: " + e.getMessage() + "\"}")
                .build();
        }
    }

    /**
     * Genera una firma HMAC-SHA256 para autenticar la petición
     */
    private String generateSignature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                SERVERLESS_SECRET_KEY.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);
            byte[] hmacData = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hmacData);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar la firma: " + e.getMessage());
        }
    }
}
