package software.ulpgc.register.admin.adapter.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.register.admin.model.Forms.*;
import software.ulpgc.register.admin.port.AdministrationGateway;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class HttpAdministrationGateway implements AdministrationGateway {
    private final URI api;
    record Reply(int statusCode, String body) {}
    private final Function<HttpRequest, CompletableFuture<Reply>> exchange;
    private final ObjectMapper json = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);

    public HttpAdministrationGateway(URI base, HttpClient client) {
        this(base, request -> client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> new Reply(response.statusCode(), response.body())));
    }

    HttpAdministrationGateway(URI base, Function<HttpRequest, CompletableFuture<Reply>> exchange) {
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getQuery() != null
                || base.getFragment() != null || base.getPort() == 0 || base.getPort() > 65535) {
            throw new IllegalArgumentException("Introduce una URL HTTP o HTTPS válida.");
        }
        this.api = URI.create(base.toASCIIString().replaceAll("/+$", "") + "/api/v1/");
        this.exchange = exchange;
    }

    public CompletableFuture<Void> createEmployee(Employee employee) {
        return post("employees", employee, Map.of("id", employee.id(), "name", employee.name()));
    }

    public CompletableFuture<Void> createSchedule(Schedule schedule) {
        return post("schedules", schedule, Map.of("id", schedule.id(), "type", schedule.type()));
    }

    public CompletableFuture<Void> assign(Assignment assignment) {
        return post("assignments", assignment, Map.of("employeeId", assignment.employeeId(),
                "scheduleId", assignment.scheduleId(), "validFrom", assignment.validFrom(), "validThrough", assignment.validThrough()));
    }

    private CompletableFuture<Void> post(String resource, Object value, Map<String, String> expected) {
        String body;
        try {
            body = json.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No se pudo preparar el formulario.", error));
        }
        HttpRequest request = HttpRequest.newBuilder(api.resolve(resource))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json; charset=UTF-8").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return exchange.apply(request)
                .exceptionally(error -> { throw new IllegalStateException(
                        "Sin confirmación del servidor. Comprueba la conexión y el alta antes de repetirla.", error); })
                .thenAccept(reply -> {
                    if (reply.statusCode() != 201) throw new IllegalStateException(errorMessage(reply));
                    if (!confirms(reply.body(), expected)) throw new IllegalStateException(
                            "La respuesta no confirma los datos enviados. Comprueba el alta antes de repetirla.");
                });
    }

    private boolean confirms(String body, Map<String, String> expected) {
        try {
            var document = json.readTree(body);
            return document != null && expected.entrySet().stream().allMatch(field ->
                    field.getValue().equals(document.path(field.getKey()).textValue()));
        } catch (JsonProcessingException error) {
            return false;
        }
    }

    private String errorMessage(Reply reply) {
        String reason = "";
        try {
            var body = json.readTree(reply.body());
            if (body != null) reason = body.path("message").asText("");
        } catch (JsonProcessingException ignored) {
            // Los intermediarios también pueden devolver errores sin JSON.
        }
        String message = switch (reason) {
            case "Employee already exists" -> "Ya existe un empleado con ese código.";
            case "Schedule already exists" -> "Ya existe un horario con ese código.";
            case "Employee does not exist" -> "No existe el empleado indicado.";
            case "Schedule does not exist" -> "No existe el horario indicado.";
            case "Schedule assignments overlap" -> "El empleado ya tiene una asignación en ese período.";
            case "Split schedule slots must not overlap or touch" -> "Deja una pausa entre los tramos del horario partido.";
            case "Split schedule slots must be within one day" -> "Los tramos del horario partido deben terminar el mismo día.";
            default -> reason.isBlank() ? "El servidor no ha confirmado el alta." : reason;
        };
        return message + " (HTTP " + reply.statusCode() + ")";
    }
}
