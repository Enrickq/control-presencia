package software.ulpgc.register.query.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.ulpgc.register.query.model.*;
import software.ulpgc.register.query.port.*;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class QueryControllerTest {
    final StubGateway gateway = new StubGateway();
    final RecordingView view = new RecordingView();
    final QueryController controller = new QueryController(gateway, view, SwingUtilities::invokeLater);

    @Test
    void keepsTheEdtResponsiveAndDisplaysPunchesIfTheSummaryFails() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            controller.search(" DEMO ", "2026-11-20");
            controller.search("OTRO", "2026-11-21");
        });
        SwingUtilities.invokeAndWait(() -> assertThat(view.events).containsExactly("inicio DEMO"));
        assertThat(gateway.reads).isEqualTo(2);
        gateway.punches.complete(List.of());
        SwingUtilities.invokeAndWait(() -> assertThat(view.events).containsExactly("inicio DEMO", "fichajes"));
        gateway.attendance.completeExceptionally(new QueryException("Sin horario"));
        assertThat(view.finished.await(5, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
            assertThat(view.events).containsExactly("inicio DEMO", "fichajes", "Sin horario", "fin");
            assertThat(view.allOnEdt).isTrue();
            controller.search("OTRO", "2026-11-21");
        });
        assertThat(gateway.reads).isEqualTo(4);
    }

    @Test
    void keepsIndicatorsWhenPunchesFail() throws Exception {
        SwingUtilities.invokeAndWait(() -> controller.search("DEMO", "2026-11-20"));
        gateway.attendance.complete(new DailyIndicators(0, 0, 0, 0, "OFF", Optional.of(List.of())));
        gateway.punches.completeExceptionally(new QueryException("Fichajes no disponibles"));
        assertThat(view.finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(view.events).containsExactly("inicio DEMO", "indicadores", "Fichajes no disponibles", "fin");
        assertThat(view.allOnEdt).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-02-29", "20/11/2026", "", "2026-2-2"})
    void rejectsInvalidDatesBeforeSendingRequests(String date) throws Exception {
        SwingUtilities.invokeAndWait(() -> controller.search("DEMO", date));
        assertThat(gateway.reads).isZero();
        assertThat(view.events).containsExactly("inválido");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "DEMO\t", "A\nB"})
    void rejectsBlankIdentifiersAndControls(String id) throws Exception {
        SwingUtilities.invokeAndWait(() -> controller.search(id, "2026-11-20"));
        assertThat(gateway.reads).isZero();
        assertThat(view.events).containsExactly("inválido");
    }

    static class StubGateway implements AttendanceGateway {
        final CompletableFuture<DailyIndicators> attendance = new CompletableFuture<>();
        final CompletableFuture<List<Punch>> punches = new CompletableFuture<>();
        int reads;
        public CompletableFuture<DailyIndicators> attendance(DayQuery query) { reads++; return attendance; }
        public CompletableFuture<List<Punch>> punches(DayQuery query) { reads++; return punches; }
    }

    static class RecordingView implements QueryController.View {
        final List<String> events = new ArrayList<>();
        final CountDownLatch finished = new CountDownLatch(1);
        boolean allOnEdt = true;
        void record(String value) { allOnEdt &= SwingUtilities.isEventDispatchThread(); events.add(value); }
        public void begin(DayQuery query) { record("inicio " + query.employeeId()); }
        public void indicators(DailyIndicators value) { record("indicadores"); }
        public void punches(List<Punch> value) { record("fichajes"); }
        public void punchesError(String error) { record(error); }
        public void attendanceError(String error) { record(error); }
        public void invalidInput(String error) { record("inválido"); }
        public void finish() { record("fin"); finished.countDown(); }
    }
}
