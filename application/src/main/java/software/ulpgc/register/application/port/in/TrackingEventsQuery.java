
package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.StoredTrackingEvent;

import java.time.LocalDate;
import java.util.List;

public interface TrackingEventsQuery {

    List<StoredTrackingEvent> execute(
            LocalDate date,
            String employeeId
    );
}
