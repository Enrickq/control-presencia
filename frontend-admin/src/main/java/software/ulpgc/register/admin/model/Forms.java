package software.ulpgc.register.admin.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Forms {
    private Forms() {}

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Completa el campo " + label + ".");
        return value.strip();
    }

    public record Employee(String id, String name) {
        public Employee {
            id = required(id, "código de empleado");
            name = required(name, "nombre del empleado");
        }
    }

    public record Slot(String start, String end) {
        public static Slot parse(String text) {
            String[] hours = text.strip().split("-", -1);
            if (hours.length != 2) throw new IllegalArgumentException("Escribe cada tramo con el formato 08:00-16:00.");
            try {
                LocalTime from = LocalTime.parse(hours[0].strip());
                LocalTime to = LocalTime.parse(hours[1].strip());
                if (from.equals(to)) throw new IllegalArgumentException("El inicio y el fin del tramo deben ser distintos.");
                return new Slot(from.toString(), to.toString());
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("Revisa las horas de los tramos. Ejemplo: 08:00-16:00.", error);
            }
        }
    }

    public record Schedule(String type, String id, String name, Slot slot, List<Slot> slots,
                           Set<DayOfWeek> workingDays, Map<DayOfWeek, Slot> weeklySlots) {
        public Schedule {
            id = required(id, "código de horario");
            name = required(name, "nombre del horario");
        }

        private static Set<DayOfWeek> days(Set<DayOfWeek> days) {
            if (days.isEmpty()) throw new IllegalArgumentException("Selecciona al menos un día de trabajo.");
            return Set.copyOf(days);
        }

        public static Schedule continuous(String id, String name, Slot slot, Set<DayOfWeek> days) {
            return new Schedule("CONTINUOUS", id, name, slot, null, days(days), null);
        }

        public static Schedule split(String id, String name, List<Slot> slots, Set<DayOfWeek> days) {
            if (slots.size() < 2) throw new IllegalArgumentException("Introduce al menos dos tramos separados por comas.");
            return new Schedule("SPLIT", id, name, null, List.copyOf(slots), days(days), null);
        }

        public static Schedule shift(String id, String name, Map<DayOfWeek, Slot> slots) {
            if (slots.isEmpty()) throw new IllegalArgumentException("Introduce el turno de al menos un día.");
            return new Schedule("SHIFT", id, name, null, null, null, Map.copyOf(slots));
        }
    }

    public record Assignment(String employeeId, String scheduleId, String validFrom, String validThrough) {
        public Assignment {
            employeeId = required(employeeId, "código de empleado");
            scheduleId = required(scheduleId, "código de horario");
            try {
                LocalDate from = LocalDate.parse(required(validFrom, "desde"));
                LocalDate through = LocalDate.parse(required(validThrough, "hasta"));
                if (through.isBefore(from)) throw new IllegalArgumentException("La fecha final debe ser igual o posterior a la inicial.");
                validFrom = from.toString();
                validThrough = through.toString();
            } catch (DateTimeParseException error) {
                throw new IllegalArgumentException("Introduce fechas válidas con el formato AAAA-MM-DD.", error);
            }
        }
    }
}
