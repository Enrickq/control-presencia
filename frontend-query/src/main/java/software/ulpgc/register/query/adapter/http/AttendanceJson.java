package software.ulpgc.register.query.adapter.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.ulpgc.register.query.model.*;
import software.ulpgc.register.query.port.QueryException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class AttendanceJson {
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    DailyIndicators indicators(String body, DayQuery query) {
        JsonNode row = document(body);
        if (!query.employeeId().equals(text(row, "employeeId")) || !query.date().toString().equals(text(row, "date")))
            throw invalid();
        Optional<List<String>> incidents = Optional.empty();
        if (row.has("incidents")) {
            List<String> codes = new ArrayList<>();
            for (JsonNode code : array(row.get("incidents"))) codes.add(string(code));
            incidents = Optional.of(codes);
        }
        return new DailyIndicators(minutes(row, "expectedMinutes"), minutes(row, "workedMinutes"),
                minutes(row, "lateMinutes"), number(row, "balanceMinutes"), text(row, "status"), incidents);
    }

    List<Punch> punches(String body, DayQuery query) {
        List<Punch> result = new ArrayList<>();
        for (JsonNode row : array(document(body))) {
            try {
                Instant timestamp = Instant.parse(text(row, "timestamp"));
                if (!query.employeeId().equals(text(row, "employeeId"))
                        || !query.date().equals(timestamp.atZone(ZoneOffset.UTC).toLocalDate())) throw invalid();
                result.add(new Punch(timestamp, text(row, "placeId"), Punch.Type.valueOf(text(row, "type"))));
            } catch (DateTimeException | IllegalArgumentException error) {
                throw invalid();
            }
        }
        return List.copyOf(result);
    }

    QueryException httpError(int status, String body) {
        if (status == 400) {
            try {
                return new QueryException(switch (document(body).path("message").asText("")) {
                    case "Employee does not exist" -> "El empleado no está registrado.";
                    case "No active schedule assignment" -> "El empleado no tiene horario asignado para esa fecha.";
                    case "Expected YYYY-MM-DD" -> "El servidor requiere una fecha en formato AAAA-MM-DD.";
                    default -> "El servidor rechazó los datos de la consulta (HTTP 400).";
                });
            } catch (QueryException ignored) {
                return new QueryException("El servidor rechazó la consulta (HTTP 400).");
            }
        }
        return new QueryException(switch (status) {
            case 404 -> "No se encuentra la ruta de consulta. Revisa la URL y la versión del servidor (HTTP 404).";
            case 409 -> "Hay un conflicto en los datos de asistencia (HTTP 409).";
            default -> "El servidor respondió con HTTP " + status + ".";
        });
    }

    private JsonNode document(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || node.isMissingNode()) throw invalid();
            return node;
        } catch (JsonProcessingException error) {
            throw invalid();
        }
    }

    private static JsonNode array(JsonNode node) {
        if (!node.isArray()) throw invalid();
        return node;
    }

    private static String text(JsonNode row, String key) {
        return string(row.path(key));
    }

    private static String string(JsonNode node) {
        if (!node.isTextual() || node.textValue().isBlank()) throw invalid();
        return node.textValue();
    }

    private static long minutes(JsonNode row, String key) {
        long value = number(row, key);
        if (value < 0) throw invalid();
        return value;
    }

    private static long number(JsonNode row, String key) {
        JsonNode field = row.path(key);
        if (!field.isIntegralNumber() || !field.canConvertToLong()) throw invalid();
        return field.longValue();
    }

    private static QueryException invalid() {
        return new QueryException("La respuesta del servidor no coincide con la consulta o su formato es incorrecto.");
    }
}
