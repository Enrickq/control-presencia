package software.ulpgc.register.model.attendance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import software.ulpgc.register.domain.model.attendance.*;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AttendanceIncidentTest {
    @ParameterizedTest
    @CsvSource({"ABSENT,ABSENCE", "INCOMPLETE,INCOMPLETE_PUNCHES", "UNSCHEDULED,UNSCHEDULED_WORK"})
    void describesTheRecordedStatus(AttendanceStatus status, AttendanceIncident incident) {
        assertThat(summary(status, 0).incidents()).containsExactly(incident);
    }

    @Test
    void reportsLatenessEvenWhenTheSequenceIsIncomplete() {
        assertThat(summary(AttendanceStatus.INCOMPLETE, 5).incidents())
                .containsExactly(AttendanceIncident.INCOMPLETE_PUNCHES, AttendanceIncident.LATE_ARRIVAL);
    }

    @Test
    void ordinaryPresenceAndDaysOffHaveNoIncident() {
        assertThat(summary(AttendanceStatus.PRESENT, 0).incidents()).isEmpty();
        assertThat(summary(AttendanceStatus.OFF, 0).incidents()).isEmpty();
    }

    private AttendanceSummary summary(AttendanceStatus status, long late) {
        return new AttendanceSummary("DEMO", LocalDate.of(2026, 11, 20), 0, 0, late, status);
    }
}
