package software.ulpgc.register.admin.adapter.swing;

import software.ulpgc.register.admin.model.Forms.*;
import software.ulpgc.register.admin.port.AdministrationGateway;
import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

public final class AdminPanel extends JPanel {
    private final JTabbedPane tabs = new JTabbedPane();
    private final JTextArea status = new JTextArea(2, 42);
    private boolean pending;

    public boolean canClose() {
        if (pending) status.setText("Hay un alta pendiente. Espera la confirmación antes de cerrar.");
        return !pending;
    }

    public AdminPanel(AdministrationGateway gateway) {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JTextField employeeId = new JTextField(16);
        JTextField employeeName = new JTextField(24);
        Fields employee = new Fields();
        employee.row("Código de empleado", employeeId);
        employee.row("Nombre del empleado", employeeName);
        addTab("Empleados", employee, "Crear empleado", () -> {
            Employee value = new Employee(employeeId.getText(), employeeName.getText());
            return gateway.createEmployee(value).thenApply(done -> "Empleado creado: " + value.id());
        });

        ScheduleEditor schedule = new ScheduleEditor();
        addTab("Horarios", schedule, "Crear horario", () -> {
            Schedule value = schedule.read();
            return gateway.createSchedule(value).thenApply(done -> "Horario creado: " + value.id());
        });

        JTextField assignedEmployee = new JTextField(16);
        JTextField assignedSchedule = new JTextField(16);
        JTextField from = new JTextField(LocalDate.now(ZoneOffset.UTC).toString());
        JTextField through = new JTextField();
        Fields assignment = new Fields();
        assignment.row("Código de empleado", assignedEmployee);
        assignment.row("Código de horario", assignedSchedule);
        assignment.row("Desde (AAAA-MM-DD)", from);
        assignment.row("Hasta (AAAA-MM-DD)", through);
        addTab("Asignaciones", assignment, "Asignar horario", () -> {
            Assignment value = new Assignment(assignedEmployee.getText(), assignedSchedule.getText(), from.getText(), through.getText());
            return gateway.assign(value).thenApply(done -> "Horario " + value.scheduleId() + " asignado a " + value.employeeId()
                    + " del " + value.validFrom() + " al " + value.validThrough() + ".");
        });

        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(UIManager.getFont("Label.font"));
        status.getAccessibleContext().setAccessibleName("Resultado de la operación");
        add(tabs, BorderLayout.CENTER);
        add(new JScrollPane(status), BorderLayout.SOUTH);
    }

    private void addTab(String title, JComponent editor, String action, Supplier<CompletableFuture<String>> operation) {
        JPanel tab = new JPanel(new BorderLayout());
        tab.add(editor, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
        JButton button = new JButton(action);
        button.addActionListener(e -> submit(operation));
        buttons.add(button);
        tab.add(buttons, BorderLayout.SOUTH);
        tabs.addTab(title, tab);
    }

    private void submit(Supplier<CompletableFuture<String>> operation) {
        if (pending) return;
        try {
            var future = operation.get();
            pending = true;
            enabled(tabs, false);
            status.setText("Esperando confirmación del servidor…");
            future.whenCompleteAsync((message, error) -> {
                pending = false;
                enabled(tabs, true);
                Throwable cause = error;
                while (cause instanceof CompletionException && cause.getCause() != null) cause = cause.getCause();
                status.setText(cause == null ? message : cause.getMessage());
            }, SwingUtilities::invokeLater);
        } catch (RuntimeException error) {
            status.setText(error.getMessage());
        }
    }

    private static void enabled(Component c, boolean enabled) {
        c.setEnabled(enabled);
        if (c instanceof Container group) for (Component child : group.getComponents()) enabled(child, enabled);
    }
}
