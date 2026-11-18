package software.ulpgc.register.frontend.application;

import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;
import software.ulpgc.register.frontend.port.PunchException;
import software.ulpgc.register.frontend.port.PunchGateway;

import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Submit is invoked on the UI thread; remote completion is presented through the UI executor. */
public final class PunchController {
    public interface View {
        void setBusy(boolean busy);
        void showReceipt(PunchReceipt receipt);
        void showError(String message);
    }

    private final PunchGateway gateway;
    private final View view;
    private final Executor ui;
    private final AtomicBoolean busy = new AtomicBoolean();

    public PunchController(PunchGateway gateway, View view, Executor ui) {
        this.gateway = Objects.requireNonNull(gateway);
        this.view = Objects.requireNonNull(view);
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
            gateway.register(data).whenComplete((receipt, error) ->
                    ui.execute(() -> finish(receipt, error)));
        } catch (RuntimeException error) {
            finish(null, error);
        }
    }

    private void finish(PunchReceipt receipt, Throwable error) {
        busy.set(false);
        view.setBusy(false);
        if (error == null && receipt != null) view.showReceipt(receipt);
        else view.showError(message(error));
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        if (error instanceof PunchException failure) return failure.getMessage();
        return "No se pudo confirmar el fichaje. "
                + "Comprueba los registros antes de volver a intentarlo.";
    }
}
