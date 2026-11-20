package software.ulpgc.register.query.adapter.swing;

import software.ulpgc.register.query.application.QueryController;
import software.ulpgc.register.query.model.DailyIndicators;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.model.Punch;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public final class QueryPanel extends JPanel implements QueryController.View {
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.UTC);
    private final JTextField employee = new JTextField(14);
    private final JTextField date = new JTextField(LocalDate.now(ZoneOffset.UTC).toString(), 10);
    private final JButton search = new JButton("Consultar");
    private final JLabel context = new JLabel("Introduce el empleado y la fecha de la consulta.");
    private final JTextArea summary = readOnlyText(4);
    private final JTextArea incidents = readOnlyText(2);
    private final JLabel punchStatus = new JLabel(" ");
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Fecha y hora (UTC)", "Tipo", "Lugar"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };

    public QueryPanel() {
        super(new BorderLayout(8, 12));
        setBorder(new EmptyBorder(14, 16, 14, 16));
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 4));
        addField(form, "Empleado", employee);
        addField(form, "Fecha (AAAA-MM-DD)", date);
        form.add(search);
        JPanel header = new JPanel(new GridLayout(0, 1));
        header.add(form);
        header.add(context);
        add(header, BorderLayout.NORTH);

        JPanel indicators = new JPanel(new GridLayout(1, 2, 8, 0));
        indicators.add(section("Indicadores de asistencia", new JScrollPane(summary)));
        indicators.add(section("Incidencias", new JScrollPane(incidents)));
        JTable table = new JTable(rows);
        table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName("Fichajes del día UTC");
        JPanel punches = section("Fichajes del día UTC", new JScrollPane(table));
        punches.add(punchStatus, BorderLayout.SOUTH);
        JPanel results = new JPanel(new BorderLayout(0, 10));
        results.add(indicators, BorderLayout.NORTH);
        results.add(punches, BorderLayout.CENTER);
        add(results, BorderLayout.CENTER);
        setPreferredSize(new Dimension(730, 460));
    }

    private static JPanel section(String title, JComponent component) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(component, BorderLayout.CENTER);
        return panel;
    }

    private static JTextArea readOnlyText(int lines) {
        JTextArea area = new JTextArea(lines, 20);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font"));
        return area;
    }

    private static void addField(JPanel form, String text, JTextField field) {
        JLabel label = new JLabel(text);
        label.setLabelFor(field);
        form.add(label);
        form.add(field);
    }

    public void bind(QueryController controller) {
        search.addActionListener(event -> controller.search(employee.getText(), date.getText()));
        employee.addActionListener(event -> search.doClick());
        date.addActionListener(event -> search.doClick());
    }

    @Override public void begin(DayQuery query) {
        setFormEnabled(false);
        context.setText("Empleado " + query.employeeId() + "   ·   " + query.date() + " (UTC)");
        rows.setRowCount(0);
        summary.setText("Consultando indicadores…");
        incidents.setText("Consultando incidencias…");
        punchStatus.setText("Consultando fichajes…");
    }

    @Override public void invalidInput(String message) {
        rows.setRowCount(0);
        summary.setText("");
        incidents.setText("");
        punchStatus.setText(" ");
        context.setText(message);
    }

    @Override public void indicators(DailyIndicators value) {
        summary.setText("Estado: " + statusLabel(value.status())
                + "\nPrevistos: " + value.expectedMinutes() + " min   ·   Trabajados: " + value.workedMinutes() + " min"
                + "\nRetraso: " + value.lateMinutes() + " min   ·   Balance: " + value.balanceMinutes() + " min");
        incidents.setText(value.incidents().map(codes -> codes.isEmpty() ? "Sin incidencias."
                        : codes.stream().map(QueryPanel::incidentLabel).collect(Collectors.joining("\n")))
                .orElse("Esta versión del servidor no detalla incidencias."));
    }

    private static String statusLabel(String code) {
        return switch (code) {
            case "PRESENT" -> "Presente";
            case "ABSENT" -> "Ausente";
            case "OFF" -> "Día libre";
            case "UNSCHEDULED" -> "Trabajo fuera de horario";
            case "INCOMPLETE" -> "Fichaje incompleto";
            default -> code;
        };
    }

    private static String incidentLabel(String code) {
        return switch (code) {
            case "ABSENCE" -> "Ausencia en jornada prevista";
            case "INCOMPLETE_PUNCHES" -> "Secuencia de fichajes incompleta";
            case "UNSCHEDULED_WORK" -> "Trabajo en día sin jornada prevista";
            case "LATE_ARRIVAL" -> "Llegada con retraso";
            default -> code;
        };
    }

    @Override public void attendanceError(String message) {
        summary.setText(message);
        incidents.setText("Incidencias no disponibles.");
    }

    @Override public void punches(List<Punch> events) {
        rows.setRowCount(0);
        for (Punch event : events)
            rows.addRow(new Object[]{UTC.format(event.timestamp()), event.type() == Punch.Type.IN ? "Entrada" : "Salida", event.placeId()});
        punchStatus.setText(events.isEmpty() ? "Sin fichajes registrados en esta fecha." : "Fichajes: " + events.size());
    }

    @Override public void punchesError(String message) {
        rows.setRowCount(0);
        punchStatus.setText(message);
    }

    @Override public void finish() {
        setFormEnabled(true);
    }

    private void setFormEnabled(boolean enabled) {
        employee.setEnabled(enabled);
        date.setEnabled(enabled);
        search.setEnabled(enabled);
    }
}
