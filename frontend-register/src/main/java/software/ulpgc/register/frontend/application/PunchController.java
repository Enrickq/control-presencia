package software.ulpgc.register.frontend.application;

import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;
import software.ulpgc.register.frontend.port.PunchGateway;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Submit is invoked on the UI thread; network work runs on the supplied worker. */
public final class PunchController {
    public interface View {
        void setBusy(boolean busy);
        void showReceipt(PunchReceipt receipt);
        void showError(String message);
    }

    private final PunchGateway gateway;
    private final View view;
    private final Executor worker;
    private final Executor ui;
    private final AtomicBoolean busy = new AtomicBoolean();

    public PunchController(PunchGateway gateway, View view, Executor worker, Executor ui) {
        this.gateway = Objects.requireNonNull(gateway);
        this.view = Objects.requireNonNull(view);
        this.worker = Objects.requireNonNull(worker);
        this.ui = Objects.requireNonNull(ui);
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void submit(String employeeId, String employeeName, String placeId,
                       String placeName, PunchType type) {
        if (!busy.compareAndSet(false, true)) return;
        final PunchData data;
        try {
            data = new PunchData(employeeId, employeeName, placeId, placeName, type);
        } catch (IllegalArgumentException error) {
            busy.set(false);
            view.showError(error.getMessage());
            return;
        }
        view.setBusy(true);
        try {
            worker.execute(() -> register(data));
        } catch (RejectedExecutionException error) {
            finish(() -> view.showError("No se pudo iniciar el envío. El fichaje no se ha enviado."));
        }
    }

    private void register(PunchData data) {
        Runnable outcome;
        try {
            PunchReceipt receipt = Objects.requireNonNull(gateway.register(data));
            outcome = () -> view.showReceipt(receipt);
        } catch (PunchException error) {
            outcome = () -> view.showError(error.getMessage());
        } catch (RuntimeException error) {
            outcome = () -> view.showError("No se pudo confirmar el fichaje. "
                    + "Comprueba los registros antes de volver a intentarlo.");
        }
        Runnable result = outcome;
        ui.execute(() -> finish(result));
    }

    private void finish(Runnable outcome) {
        busy.set(false);
        view.setBusy(false);
        outcome.run();
    }
}
