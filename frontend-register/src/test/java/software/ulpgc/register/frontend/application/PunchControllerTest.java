package software.ulpgc.register.frontend.application;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

class PunchControllerTest {
    private final Queue<Runnable> worker = new ArrayDeque<>();
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final RecordingView view = new RecordingView();
    private final List<PunchData> requests = new ArrayList<>();
    private final PunchReceipt receipt = new PunchReceipt("EMP-1", "CENTRO-1", PunchType.IN,
            Instant.parse("2026-11-15T08:10:30Z"));

    @Test
    void blocksRepeatedSubmissionsUntilTheUiReceivesTheServerResult() {
        PunchController controller = new PunchController(data -> {
            requests.add(data);
            return receipt;
        }, view, worker::add, ui::add);

        submit(controller);
        submit(controller);
        assertThat(controller.isBusy()).isTrue();
        assertThat(view.busy).isTrue();
        assertThat(worker).hasSize(1);
        assertThat(requests).isEmpty();

        worker.remove().run();
        submit(controller);
        assertThat(requests).hasSize(1);
        assertThat(view.receipt).isNull();
        assertThat(controller.isBusy()).isTrue();

        ui.remove().run();
        assertThat(controller.isBusy()).isFalse();
        assertThat(view.busy).isFalse();
        assertThat(view.receipt).isEqualTo(receipt);
        assertThat(requests.getFirst().employeeName()).isEqualTo("Ana García");
    }

    @Test
    void validatesMissingFieldsBeforeDispatchingAnyWork() {
        PunchController controller = new PunchController(data -> receipt, view, worker::add, ui::add);
        controller.submit(" ", "Ana", "CENTRO-1", "Oficina", PunchType.IN);
        assertThat(controller.isBusy()).isFalse();
        assertThat(worker).isEmpty();
        assertThat(view.error).contains("identificador del empleado", "obligatorio");
    }

    @Test
    void rejectsControlCharactersBeforeDispatchingAnyWork() {
        PunchController controller = new PunchController(data -> receipt, view, worker::add, ui::add);
        controller.submit("EMP\t1", "Ana", "CENTRO-1", "Oficina", PunchType.IN);
        assertThat(controller.isBusy()).isFalse();
        assertThat(worker).isEmpty();
        assertThat(view.error).contains("caracteres de control");
    }

    @Test
    void displaysRemoteFailureRestoresTheViewAndDoesNotRetry() {
        PunchController controller = new PunchController(data -> {
            requests.add(data);
            throw new PunchException(PunchException.Kind.CONFLICT, "Ya hay una entrada abierta.");
        }, view, worker::add, ui::add);

        submit(controller);
        worker.remove().run();
        ui.remove().run();

        assertThat(view.error).isEqualTo("Ya hay una entrada abierta.");
        assertThat(view.receipt).isNull();
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
        assertThat(requests).hasSize(1);
        assertThat(worker).isEmpty();

        submit(controller);
        assertThat(worker).hasSize(1);
    }

    @Test
    void explainsUncertainOutcomeAfterUnexpectedFailureWithoutLeakingInternalDetails() {
        PunchController controller = new PunchController(data -> {
            throw new IllegalStateException("internal adapter detail");
        }, view, worker::add, ui::add);

        submit(controller);
        worker.remove().run();
        ui.remove().run();

        assertThat(view.error).contains("No se pudo confirmar", "antes de volver")
                .doesNotContain("internal adapter detail");
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
    }

    @Test
    void restoresControlsWhenTheWorkerRejectsTheSubmission() {
        PunchController controller = new PunchController(data -> receipt, view,
                task -> { throw new RejectedExecutionException(); }, ui::add);
        submit(controller);

        assertThat(view.error).contains("no se ha enviado");
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
        assertThat(ui).isEmpty();
    }

    private void submit(PunchController controller) {
        controller.submit(" EMP-1 ", " Ana García ", " CENTRO-1 ", " Oficina ", PunchType.IN);
    }

    private static final class RecordingView implements PunchController.View {
        boolean busy;
        PunchReceipt receipt;
        String error;

        @Override public void setBusy(boolean busy) { this.busy = busy; }
        @Override public void showReceipt(PunchReceipt receipt) { this.receipt = receipt; }
        @Override public void showError(String message) { this.error = message; }
    }
}
