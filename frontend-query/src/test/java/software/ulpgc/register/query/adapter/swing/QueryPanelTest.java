package software.ulpgc.register.query.adapter.swing;

import org.junit.jupiter.api.Test;

import software.ulpgc.register.query.model.*;

import java.awt.Component;
import java.awt.Container;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.*;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPanelTest {
    @Test void paintsResultsInSpanishAndClearsThePreviousEmployeeBeforeTheNextRequest() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var view = new QueryPanel();
            var query = DayQuery.parse("DEMO", "2026-11-20");
            view.begin(query);
            var button = first(view, JButton.class);
            assertThat(button.isEnabled()).isFalse();
            view.indicators(new DailyIndicators(480, 465, 10, -15, "PRESENT", Optional.of(List.of("LATE_ARRIVAL"))));
            view.punches(List.of(new Punch(Instant.parse("2026-11-20T08:10:00Z"), "SALA", Punch.Type.IN)));
            var table = first(view, JTable.class);
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getValueAt(0, 0)).isEqualTo("2026-11-20 08:10:00");
            assertThat(table.getValueAt(0, 1)).isEqualTo("Entrada");
            assertThat(table.getValueAt(0, 2)).isEqualTo("SALA");
            String text = components(view).filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                    .map(JTextArea::getText).reduce("", String::concat);
            assertThat(text).contains("Presente", "480", "465", "10", "-15", "Llegada con retraso");
            view.finish();
            assertThat(button.isEnabled()).isTrue();
            view.begin(DayQuery.parse("OTRO", "2026-11-21"));
            assertThat(table.getRowCount()).isZero();
            view.attendanceError("Sin horario");
            view.punches(List.of());
            assertThat(components(view).filter(JTextArea.class::isInstance).map(JTextArea.class::cast)
                    .map(JTextArea::getText)).contains("Sin horario", "Incidencias no disponibles.");
            assertThat(components(view).filter(JLabel.class::isInstance).map(JLabel.class::cast)
                    .map(JLabel::getText)).contains("Sin fichajes en las fechas consultadas.");
        });
    }

    private static <T> T first(Component root, Class<T> type) {
        return components(root).filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static Stream<Component> components(Component node) {
        if (!(node instanceof Container parent)) return Stream.of(node);
        return Stream.concat(Stream.of(node), Stream.of(parent.getComponents()).flatMap(QueryPanelTest::components));
    }
}
