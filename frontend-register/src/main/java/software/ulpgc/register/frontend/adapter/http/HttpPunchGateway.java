package software.ulpgc.register.frontend.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;
import software.ulpgc.register.frontend.port.PunchGateway;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static software.ulpgc.register.frontend.port.PunchException.Kind.*;

/** Adaptador remoto: el servidor decide la secuencia y la fecha del fichaje. */
public final class HttpPunchGateway implements PunchGateway, AutoCloseable {
    private static final String UNCERTAIN =
            "No se puede confirmar el fichaje. Consulta los registros antes de repetirlo.";
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI endpoint;
    private final HttpClient client;
    private final Duration timeout;

    public HttpPunchGateway(URI baseUri) {
        this(baseUri, Duration.ofSeconds(10));
    }

    public HttpPunchGateway(URI baseUri, Duration timeout) {
        this.endpoint = endpoint(baseUri);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("El tiempo de espera debe ser positivo.");
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public CompletableFuture<PunchReceipt> register(PunchData punch) {
        Objects.requireNonNull(punch);
        String body;
        try {
            body = mapper.writeValueAsString(punch);
        } catch (JsonProcessingException error) {
            return CompletableFuture.failedFuture(new PunchException(BAD_REQUEST,
                    "No se pudo preparar la petición. El fichaje no se ha enviado.", error));
        }

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .exceptionally(error -> {
                    throw new PunchException(UNAVAILABLE,
                            "No se ha recibido una respuesta del servidor. " + UNCERTAIN, error);
                })
                .thenApply(response -> confirmedReceipt(response, punch));
    }

    private PunchReceipt confirmedReceipt(HttpResponse<String> response, PunchData punch) {
        if (response.statusCode() != 201) throw failure(response);
        return receipt(response.body(), punch);
    }

    private PunchReceipt receipt(String body, PunchData sent) {
        try {
            JsonNode json = mapper.readTree(body);
            PunchReceipt result = new PunchReceipt(text(json, "employeeId"), text(json, "placeId"),
                    PunchType.valueOf(text(json, "type")), Instant.parse(text(json, "timestamp")));
            if (!result.employeeId().equals(sent.employeeId()) || !result.placeId().equals(sent.placeId())
                    || result.type() != sent.type())
                throw new IllegalArgumentException("El justificante no corresponde al fichaje enviado.");
            return result;
        } catch (IOException | IllegalArgumentException | java.time.DateTimeException error) {
            throw new PunchException(INVALID_RESPONSE,
                    "La respuesta del servidor no es válida. " + UNCERTAIN, error);
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode value = json == null ? null : json.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank())
            throw new IllegalArgumentException("Falta un campo válido: " + field);
        return value.textValue();
    }

    private PunchException failure(HttpResponse<String> response) {
        int status = response.statusCode();
        return switch (status) {
            case 400 -> new PunchException(BAD_REQUEST,
                    "El servidor ha rechazado los datos. Revisa el empleado, el centro y el tipo de fichaje.");
            case 409 -> new PunchException(CONFLICT, conflictMessage(response.body()));
            case 404 -> new PunchException(BAD_REQUEST,
                    "No se encuentra la API de fichajes. Revisa la dirección del servidor.");
            case 401, 403 -> new PunchException(BAD_REQUEST,
                    "El servidor no permite registrar el fichaje con este acceso.");
            default -> new PunchException(status >= 500 ? SERVER_ERROR : INVALID_RESPONSE,
                    "El servidor ha respondido con HTTP " + status + ". " + UNCERTAIN);
        };
    }

    private String conflictMessage(String body) {
        String message;
        try {
            message = text(mapper.readTree(body), "message");
        } catch (IOException | IllegalArgumentException error) {
            message = "";
        }
        return switch (message) {
            case "Employee is already checked in" ->
                    "Ya hay una entrada abierta para este empleado. Registra una salida antes de volver a entrar.";
            case "Cannot check out without a previous entry" ->
                    "No hay una entrada abierta para este empleado. Registra primero una entrada.";
            default -> "El fichaje entra en conflicto con los registros anteriores. Consulta la secuencia de entradas y salidas.";
        };
    }

    private static URI endpoint(URI base) {
        if (base == null || base.getScheme() == null
                || !(base.getScheme().equalsIgnoreCase("http") || base.getScheme().equalsIgnoreCase("https"))
                || base.getHost() == null || base.getRawUserInfo() != null
                || base.getRawQuery() != null || base.getRawFragment() != null
                || base.getPort() == 0 || base.getPort() > 65535)
            throw new IllegalArgumentException("Usa una URL base HTTP o HTTPS sin credenciales, consulta ni fragmento.");
        String value = base.toString();
        value = base.getScheme().toLowerCase(Locale.ROOT) + value.substring(base.getScheme().length());
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return URI.create(value + "/api/v1/punches");
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
