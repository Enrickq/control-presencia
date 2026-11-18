package software.ulpgc.register.frontend.adapter.swing;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.frontend.application.PunchController;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PunchPanelTest {
    @Test
    void preferredLayoutKeepsTheFormActionsAndResultVisible() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PunchPanel panel = new PunchPanel();
            panel.setSize(panel.getPreferredSize());
            layout(panel);
            for (String name : new String[]{"employeeId", "employeeName", "placeId", "placeName", "entry", "exit", "status"}) {
                Component child = component(panel, name, Component.class);
                Rectangle visible = SwingUtilities.convertRectangle(child.getParent(), child.getBounds(), panel);
                assertThat(visible.width).as(name).isPositive();
                assertThat(visible.height).as(name).isPositive();
                assertThat(new Rectangle(panel.getSize()).contains(visible)).as(name + " dentro del formulario").isTrue();
            }
            JButton action = component(panel, "entry", JButton.class);
            JTextArea status = component(panel, "status", JTextArea.class);
            Rectangle actionArea = SwingUtilities.convertRectangle(action.getParent(), action.getBounds(), panel);
            Rectangle statusArea = SwingUtilities.convertRectangle(status.getParent(), status.getBounds(), panel);
            assertThat(actionArea.getMaxY()).isLessThan(statusArea.getMinY());
        });
    }

    @Test
    void buttonStartsWithoutWaitingAndPresentsBackgroundCompletionOnTheEdt() throws Exception {
        CompletableFuture<PunchReceipt> pending = new CompletableFuture<>();
        AtomicReference<PunchPanel> ref = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();

        SwingUtilities.invokeAndWait(() -> {
            PunchPanel panel = filledPanel();
            ref.set(panel);
            panel.bind(new PunchController(data -> {
                assertThat(data.type()).isEqualTo(PunchType.IN);
                assertThat(data.employeeName()).isEqualTo("Ana García");
                calls.incrementAndGet();
                return pending;
            }, edtView(panel), SwingUtilities::invokeLater));
            component(panel, "entry", JButton.class).doClick();
            assertThat(component(panel, "entry", JButton.class).isEnabled()).isFalse();
            assertThat(component(panel, "exit", JButton.class).isEnabled()).isFalse();
            assertThat(component(panel, "employeeId", JTextField.class).isEnabled()).isFalse();
            assertThat(component(panel, "status", JTextArea.class).getText()).contains("Enviando");
        });

        assertThat(calls.get()).isOne();
        assertThat(pending.isDone()).isFalse();
        SwingUtilities.invokeAndWait(() ->
                assertThat(component(ref.get(), "status", JTextArea.class).getText()).contains("Enviando"));
        assertThat(SwingUtilities.isEventDispatchThread()).isFalse();
        pending.complete(new PunchReceipt("EMP-1", "CENTRO-1", PunchType.IN,
                Instant.parse("2026-11-15T08:10:30Z")));
        SwingUtilities.invokeAndWait(() -> {
            PunchPanel panel = ref.get();
            assertThat(component(panel, "entry", JButton.class).isEnabled()).isTrue();
            assertThat(component(panel, "exit", JButton.class).isEnabled()).isTrue();
            assertThat(component(panel, "status", JTextArea.class).getText())
                    .contains("Entrada registrada correctamente", "15/11/2026 08:10:30 UTC", "EMP-1");
        });
        assertThat(calls.get()).isOne();
    }

    @Test
    void failedExitKeepsFormValuesAndAllowsAnotherExplicitAction() throws Exception {
        CompletableFuture<PunchReceipt> pending = new CompletableFuture<>();
        AtomicReference<PunchPanel> ref = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            PunchPanel panel = filledPanel();
            ref.set(panel);
            panel.bind(new PunchController(data -> {
                assertThat(data.type()).isEqualTo(PunchType.OUT);
                calls.incrementAndGet();
                return pending;
            }, edtView(panel), SwingUtilities::invokeLater));
            component(panel, "exit", JButton.class).doClick();
        });

        pending.completeExceptionally(new PunchException(PunchException.Kind.CONFLICT, "No hay una entrada previa."));
        SwingUtilities.invokeAndWait(() -> {
            PunchPanel panel = ref.get();
            assertThat(component(panel, "employeeId", JTextField.class).getText()).isEqualTo("EMP-1");
            assertThat(component(panel, "employeeName", JTextField.class).getText()).isEqualTo("Ana García");
            assertThat(component(panel, "placeId", JTextField.class).getText()).isEqualTo("CENTRO-1");
            assertThat(component(panel, "placeName", JTextField.class).getText()).isEqualTo("Oficina central");
            assertThat(component(panel, "status", JTextArea.class).getText()).isEqualTo("No hay una entrada previa.");
            assertThat(component(panel, "exit", JButton.class).isEnabled()).isTrue();
            assertThat(component(panel, "employeeId", JTextField.class).isEnabled()).isTrue();
        });
        assertThat(calls.get()).isOne();
    }

    private static PunchPanel filledPanel() {
        PunchPanel panel = new PunchPanel();
        component(panel, "employeeId", JTextField.class).setText("EMP-1");
        component(panel, "employeeName", JTextField.class).setText("Ana García");
        component(panel, "placeId", JTextField.class).setText("CENTRO-1");
        component(panel, "placeName", JTextField.class).setText("Oficina central");
        return panel;
    }

    private static PunchController.View edtView(PunchPanel panel) {
        return new PunchController.View() {
            @Override public void setBusy(boolean busy) {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                panel.setBusy(busy);
            }
            @Override public void showReceipt(PunchReceipt receipt) {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                panel.showReceipt(receipt);
            }
            @Override public void showError(String message) {
                assertThat(SwingUtilities.isEventDispatchThread()).isTrue();
                panel.showError(message);
            }
        };
    }

    private static <T extends Component> T component(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                T result = find(nested, name, type);
                if (result != null) return result;
            }
        }
        throw new AssertionError("Componente no encontrado: " + name);
    }

    private static <T extends Component> T find(Container root, String name, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container nested) {
                T result = find(nested, name, type);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents())
            if (child instanceof Container nested) layout(nested);
    }
}
