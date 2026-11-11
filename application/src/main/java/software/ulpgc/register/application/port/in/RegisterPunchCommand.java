package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.TrackingEvent;

public interface RegisterPunchCommand {
    void execute(TrackingEvent event);
}
