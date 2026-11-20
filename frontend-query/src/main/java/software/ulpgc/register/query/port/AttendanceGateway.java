package software.ulpgc.register.query.port;

import software.ulpgc.register.query.model.DailyIndicators;
import software.ulpgc.register.query.model.DayQuery;
import software.ulpgc.register.query.model.Punch;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AttendanceGateway {
    CompletableFuture<DailyIndicators> attendance(DayQuery query);
    CompletableFuture<List<Punch>> punches(DayQuery query);
}
