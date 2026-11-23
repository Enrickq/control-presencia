package software.ulpgc.register.admin.adapter.swing;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.admin.model.Forms.*;
import software.ulpgc.register.admin.port.AdministrationGateway;
import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;

class AdminPanelTest {
    static final class Gateway implements AdministrationGateway {
        int employees;
        Schedule schedule;
        Assignment assignment;
        final CompletableFuture<Void> pending = new CompletableFuture<>();
        public CompletableFuture<Void> createEmployee(Employee value) { employees++; return pending; }
        public CompletableFuture<Void> createSchedule(Schedule value) { schedule = value; return CompletableFuture.completedFuture(null); }
        public CompletableFuture<Void> assign(Assignment value) { assignment = value; return CompletableFuture.completedFuture(null); }
    }

    @Test void keepsTheEdtResponsiveAndPreventsASecondWriteUntilCompletion() throws Exception {
        var gateway = new Gateway();
        var panel = new AdminPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new AdminPanel(gateway);
            var form = first(panel[0], JTabbedPane.class).getComponentAt(0);
            var button = first(form, JButton.class);
            button.doClick();
            assertThat(gateway.employees).isZero();
            text(form, "Código de empleado").setText("E");
            text(form, "Nombre del empleado").setText("Persona de prueba");
            button.doClick();
            button.doClick();
            assertThat(button.isEnabled()).isFalse();
            assertThat(panel[0].canClose()).isFalse();
            assertThat(gateway.employees).isOne();
        });
        gateway.pending.completeExceptionally(new IllegalStateException("Conflicto de prueba"));
        SwingUtilities.invokeAndWait(() -> {
            assertThat(first(panel[0], JButton.class).isEnabled()).isTrue();
            assertThat(panel[0].canClose()).isTrue();
            assertThat(first(panel[0], JTextArea.class).getText()).isEqualTo("Conflicto de prueba");
        });
    }

    @Test void scheduleAndAssignmentButtonsSendTheValuesShownInTheForm() throws Exception {
        var gateway = new Gateway();
        var panel = new AdminPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            panel[0] = new AdminPanel(gateway);
            var tabs = first(panel[0], JTabbedPane.class);
            tabs.setSelectedIndex(1);
            Component form = tabs.getSelectedComponent();
            text(form, "Código de horario").setText("H");
            text(form, "Nombre del horario").setText("Mañana");
            button(form, "Crear horario").doClick();
            assertThat(gateway.schedule.slot()).isEqualTo(new Slot("08:00", "16:00"));
        });
        SwingUtilities.invokeAndWait(() -> {
            var tabs = first(panel[0], JTabbedPane.class);
            tabs.setSelectedIndex(2);
            Component form = tabs.getSelectedComponent();
            text(form, "Código de empleado").setText("E");
            text(form, "Código de horario").setText("H");
            text(form, "Desde (AAAA-MM-DD)").setText("2026-11-23");
            text(form, "Hasta (AAAA-MM-DD)").setText("2026-11-22");
            button(form, "Asignar horario").doClick();
            assertThat(gateway.assignment).isNull();
            text(form, "Hasta (AAAA-MM-DD)").setText("2026-11-30");
            button(form, "Asignar horario").doClick();
            assertThat(gateway.assignment).isEqualTo(new Assignment("E","H","2026-11-23","2026-11-30"));
        });
    }

    private static JButton button(Component form, String label) {
        return tree(form).filter(JButton.class::isInstance).map(JButton.class::cast)
                .filter(b -> label.equals(b.getText())).findFirst().orElseThrow();
    }

    private static JTextField text(Component form, String label) {
        return (JTextField) tree(form).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                .filter(l -> label.equals(l.getText())).findFirst().orElseThrow().getLabelFor();
    }

    private static <T> T first(Component root, Class<T> type) {
        return tree(root).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static Stream<Component> tree(Component c) {
        if (!(c instanceof Container group)) return Stream.of(c);
        return Stream.concat(Stream.of(c), Stream.of(group.getComponents()).flatMap(AdminPanelTest::tree));
    }
}
