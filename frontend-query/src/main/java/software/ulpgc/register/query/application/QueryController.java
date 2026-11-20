package software.ulpgc.register.query.application;

import software.ulpgc.register.query.model.DailyIndicators;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.model.Punch;
import software.ulpgc.register.query.port.AttendanceGateway;
import software.ulpgc.register.query.port.QueryException;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class QueryController {
    public interface View {
        void begin(DayQuery query);
        void invalidInput(String message);
        void indicators(DailyIndicators indicators);
        void attendanceError(String message);
        void punches(List<Punch> punches);
        void punchesError(String message);
        void finish();
    }

    private final AttendanceGateway gateway;
    private final View view;
    private final Executor ui;
    private boolean busy;

    public QueryController(AttendanceGateway gateway, View view, Executor ui) {
        this.gateway = gateway;
        this.view = view;
        this.ui = ui;
    }

    /** La vista llama a este método desde su hilo de eventos. */
    public void search(String employee, String date) {
        if (busy) return;
        DayQuery query;
        try {
            query = DayQuery.parse(employee, date);
        } catch (IllegalArgumentException error) {
            view.invalidInput(error.getMessage());
            return;
        }
        busy = true;
        view.begin(query);
        var attendance = display(() -> gateway.attendance(query), view::indicators, view::attendanceError);
        var punches = display(() -> gateway.punches(query), view::punches, view::punchesError);
        CompletableFuture.allOf(attendance, punches).whenCompleteAsync((ignored, error) -> {
            busy = false;
            view.finish();
        }, ui);
    }

    private <T> CompletableFuture<Void> display(Supplier<CompletableFuture<T>> request,
                                                 Consumer<T> success, Consumer<String> failure) {
        CompletableFuture<T> pending;
        try {
            pending = request.get();
        } catch (RuntimeException error) {
            pending = CompletableFuture.failedFuture(error);
        }
        return pending.handleAsync((value, error) -> {
            if (error == null) success.accept(value);
            else failure.accept(message(error));
            return null;
        }, ui);
    }

    private static String message(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
        return error instanceof QueryException ? error.getMessage() : "No se pudo completar la consulta.";
    }
}
