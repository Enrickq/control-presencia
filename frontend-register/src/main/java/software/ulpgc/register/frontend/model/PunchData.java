package software.ulpgc.register.frontend.model;

public record PunchData(String employeeId, String employeeName,
                        String placeId, String placeName, PunchType type) {
    public PunchData {
        employeeId = required(employeeId, "El identificador del empleado");
        employeeName = required(employeeName, "El nombre del empleado");
        placeId = required(placeId, "El identificador del centro");
        placeName = required(placeName, "El nombre del centro");
        if (type == null) throw new IllegalArgumentException("Selecciona entrada o salida.");
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(label + " es obligatorio.");
        if (value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(label + " no puede contener tabuladores ni caracteres de control.");
        return value.strip();
    }
}
