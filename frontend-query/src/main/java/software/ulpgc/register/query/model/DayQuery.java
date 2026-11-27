package software.ulpgc.register.query.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public record DayQuery(String employeeId, LocalDate date) {
    public DayQuery {
        if (employeeId == null || employeeId.isBlank() || employeeId.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Introduce un identificador de empleado válido.");
        employeeId = employeeId.strip();
        Objects.requireNonNull(date);
    }

    public static DayQuery parse(String employee, String date) {
        return new DayQuery(employee, parseDate(date));
    }

    public static LocalDate parseDate(String date) {
        if (date == null || !date.strip().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("Introduce la fecha en formato AAAA-MM-DD.");
        try {
            return LocalDate.parse(date.strip());
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("La fecha indicada no existe.");
        }
    }
}
