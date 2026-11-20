package software.ulpgc.register.query.model;

import java.util.List;
import java.util.Optional;

public record DailyIndicators(long expectedMinutes, long workedMinutes, long lateMinutes, long balanceMinutes,
                              String status, Optional<List<String>> incidents) {
    public DailyIndicators {
        incidents = incidents.map(List::copyOf);
    }
}
