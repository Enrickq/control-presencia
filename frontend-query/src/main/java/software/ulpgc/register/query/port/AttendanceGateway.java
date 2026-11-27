package software.ulpgc.register.query.port;

import software.ulpgc.register.query.model.DailyIndicators;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.model.Punch;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AttendanceGateway {
    CompletableFuture<DailyIndicators> attendance(DayQuery query);
    default CompletableFuture<List<Punch>> punches(DayQuery query) {
        return punches(query, query.date());
    }
    CompletableFuture<List<Punch>> punches(DayQuery first, LocalDate through);
}
