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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;

class PunchControllerTest {
    private final Queue<Runnable> ui = new ArrayDeque<>();
    private final RecordingView view = new RecordingView();
    private final List<PunchData> requests = new ArrayList<>();
    private final PunchReceipt receipt = new PunchReceipt("EMP-1", "CENTRO-1", PunchType.IN,
            Instant.parse("2026-11-15T08:10:30Z"));

    @Test
    void blocksRepeatedSubmissionsUntilTheUiReceivesTheServerResult() {
        CompletableFuture<PunchReceipt> pending = new CompletableFuture<>();
        PunchController controller = new PunchController(data -> {
            requests.add(data);
            return pending;
        }, view, ui::add);

        submit(controller);
        submit(controller);
        assertThat(controller.isBusy()).isTrue();
        assertThat(view.busy).isTrue();
        assertThat(requests).hasSize(1);
        assertThat(ui).isEmpty();
        assertThat(pending.isDone()).isFalse();

        pending.complete(receipt);
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
    void presentsAnAlreadyCompletedFutureThroughTheUiExecutor() {
        PunchController controller = new PunchController(
                data -> CompletableFuture.completedFuture(receipt), view, ui::add);

        submit(controller);
        assertThat(view.receipt).isNull();
        assertThat(view.busy).isTrue();
        assertThat(controller.isBusy()).isTrue();
        assertThat(ui).hasSize(1);

        ui.remove().run();
        assertThat(view.receipt).isEqualTo(receipt);
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
    }

    @Test
    void validatesMissingFieldsBeforeCallingTheGateway() {
        PunchController controller = recordingController();
        controller.submit(" ", "Ana", "CENTRO-1", "Oficina", PunchType.IN);
        assertThat(controller.isBusy()).isFalse();
        assertThat(requests).isEmpty();
        assertThat(view.error).contains("identificador del empleado", "obligatorio");
    }

    @Test
    void rejectsControlCharactersBeforeCallingTheGateway() {
        PunchController controller = recordingController();
        controller.submit("EMP\t1", "Ana", "CENTRO-1", "Oficina", PunchType.IN);
        assertThat(controller.isBusy()).isFalse();
        assertThat(requests).isEmpty();
        assertThat(view.error).contains("caracteres de control");
    }

    @Test
    void displaysWrappedRemoteFailureRestoresTheViewAndDoesNotRetry() {
        CompletableFuture<PunchReceipt> pending = new CompletableFuture<>();
        PunchController controller = new PunchController(data -> {
            requests.add(data);
            return pending;
        }, view, ui::add);

        submit(controller);
        pending.completeExceptionally(new CompletionException(new PunchException(
                PunchException.Kind.CONFLICT, "Ya hay una entrada abierta.")));
        assertThat(view.error).isNull();
        assertThat(controller.isBusy()).isTrue();
        ui.remove().run();

        assertThat(view.error).isEqualTo("Ya hay una entrada abierta.");
        assertThat(view.receipt).isNull();
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
        assertThat(requests).hasSize(1);
        assertThat(ui).isEmpty();

        submit(controller);
        assertThat(requests).hasSize(2);
    }

    @Test
    void explainsUncertainOutcomeAfterUnexpectedAsyncFailureWithoutLeakingInternalDetails() {
        PunchController controller = new PunchController(data -> CompletableFuture.failedFuture(
                new IllegalStateException("internal adapter detail")), view, ui::add);

        submit(controller);
        ui.remove().run();

        assertThat(view.error).contains("No se pudo confirmar", "antes de volver")
                .doesNotContain("internal adapter detail");
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
    }

    @Test
    void restoresControlsIfTheGatewayThrowsBeforeReturningAFuture() {
        PunchController controller = new PunchController(data -> {
            throw new IllegalStateException("gateway unavailable");
        }, view, ui::add);
        submit(controller);

        assertThat(view.error).contains("No se pudo confirmar").doesNotContain("gateway unavailable");
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
        assertThat(ui).isEmpty();
    }

    @Test
    void preservesTypedFailureWhenTheGatewayThrowsSynchronously() {
        PunchController controller = new PunchController(data -> {
            throw new PunchException(PunchException.Kind.UNAVAILABLE, "No se ha recibido respuesta.");
        }, view, ui::add);
        submit(controller);

        assertThat(view.error).isEqualTo("No se ha recibido respuesta.");
        assertThat(view.busy).isFalse();
        assertThat(controller.isBusy()).isFalse();
    }

    private PunchController recordingController() {
        return new PunchController(data -> {
            requests.add(data);
            return CompletableFuture.completedFuture(receipt);
        }, view, ui::add);
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
