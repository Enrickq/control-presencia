package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.EventStore;
import software.ulpgc.register.model.StoredTrackingEvent;
import software.ulpgc.register.model.TrackingType;
import software.ulpgc.register.model.attendance.AttendanceStatus;
import software.ulpgc.register.model.attendance.AttendanceSummary;
import software.ulpgc.register.model.schedule.Schedule;
import software.ulpgc.register.model.schedule.ScheduleAssignment;
import software.ulpgc.register.model.schedule.TimeSlot;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class CalculateDailyAttendance {

    private final EventStore eventStore;

    public CalculateDailyAttendance(EventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore);
    }

    public AttendanceSummary execute(
            String employeeId,
            LocalDate date,
            Schedule schedule,
            ScheduleAssignment assignment
    ) {
        Objects.requireNonNull(date);
        Objects.requireNonNull(schedule);
        Objects.requireNonNull(assignment);

        validateAssignment(employeeId, date, schedule, assignment);

        List<StoredTrackingEvent> events = eventsFor(employeeId, date);

        ParsingResult result = pairEvents(events, date);

        long expectedMinutes = schedule.expectedMinutesOn(date);

        long workedMinutes = result.periods().stream()
                .mapToLong(period ->
                        Duration.between(
                                period.start(),
                                period.end()
                        ).toMinutes()
                )
                .sum();

        long lateMinutes = calculateLateMinutes(
                date,
                schedule.slotsOn(date),
                result.periods()
        );

        AttendanceStatus status = determineStatus(
                expectedMinutes,
                result
        );

        return new AttendanceSummary(
                employeeId,
                date,
                expectedMinutes,
                workedMinutes,
                lateMinutes,
                status
        );
    }

    private void validateAssignment(
            String employeeId,
            LocalDate date,
            Schedule schedule,
            ScheduleAssignment assignment
    ) {
        if (employeeId == null || employeeId.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        if (!assignment.employeeId().equals(employeeId)
                || !assignment.scheduleId().equals(schedule.id())
                || !assignment.isActiveOn(date)) {
            throw new IllegalArgumentException(
                    "No matching active schedule assignment"
            );
        }
    }

    private List<StoredTrackingEvent> eventsFor(
            String employeeId,
            LocalDate date
    ) {
        return Stream.concat(
                        eventStore.read(date).stream(),
                        eventStore.read(date.plusDays(1)).stream()
                )
                .filter(event ->
                        event.employeeId().equals(employeeId)
                )
                .sorted(Comparator.comparing(
                        StoredTrackingEvent::ts
                ))
                .toList();
    }

    private ParsingResult pairEvents(
            List<StoredTrackingEvent> events,
            LocalDate date
    ) {
        Instant nextDayStart = date.plusDays(1)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);

        List<WorkPeriod> periods = new ArrayList<>();

        Instant entry = null;
        boolean incomplete = false;

        for (StoredTrackingEvent event : events) {

            if (event.type() == TrackingType.In) {

                if (entry != null) {
                    incomplete = true;
                }

                entry = event.ts().isBefore(nextDayStart)
                        ? event.ts()
                        : null;

            } else if (event.type() == TrackingType.Out
                    && entry != null) {

                if (event.ts().isAfter(entry)) {
                    periods.add(new WorkPeriod(
                            entry,
                            event.ts()
                    ));
                } else {
                    incomplete = true;
                }

                entry = null;
            }
        }

        return new ParsingResult(
                List.copyOf(periods),
                incomplete || entry != null
        );
    }

    private long calculateLateMinutes(
            LocalDate date,
            List<TimeSlot> slots,
            List<WorkPeriod> periods
    ) {
        return slots.stream()
                .mapToLong(slot ->
                        lateMinutesForSlot(date, slot, periods)
                )
                .sum();
    }

    private long lateMinutesForSlot(
            LocalDate date,
            TimeSlot slot,
            List<WorkPeriod> periods
    ) {
        Instant scheduledStart = date
                .atTime(slot.start())
                .toInstant(ZoneOffset.UTC);

        LocalDate endDate = slot.crossesMidnight()
                ? date.plusDays(1)
                : date;

        Instant scheduledEnd = endDate
                .atTime(slot.end())
                .toInstant(ZoneOffset.UTC);

        Optional<Instant> firstPresence = periods.stream()
                .filter(period ->
                        period.end().isAfter(scheduledStart)
                                && period.start().isBefore(scheduledEnd)
                )
                .map(period ->
                        period.start().isAfter(scheduledStart)
                                ? period.start()
                                : scheduledStart
                )
                .min(Comparator.naturalOrder());

        return firstPresence
                .map(start ->
                        Duration.between(
                                scheduledStart,
                                start
                        ).toMinutes()
                )
                .orElse(0L);
    }

    private AttendanceStatus determineStatus(
            long expectedMinutes,
            ParsingResult result
    ) {
        if (result.incomplete()) {
            return AttendanceStatus.INCOMPLETE;
        }

        if (expectedMinutes == 0) {
            return result.periods().isEmpty()
                    ? AttendanceStatus.OFF
                    : AttendanceStatus.UNSCHEDULED;
        }

        return result.periods().isEmpty()
                ? AttendanceStatus.ABSENT
                : AttendanceStatus.PRESENT;
    }

    private record WorkPeriod(
            Instant start,
            Instant end
    ) {
    }

    private record ParsingResult(
            List<WorkPeriod> periods,
            boolean incomplete
    ) {
    }
}