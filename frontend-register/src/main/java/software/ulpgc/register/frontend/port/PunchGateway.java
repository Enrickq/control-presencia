package software.ulpgc.register.frontend.port;

import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;

@FunctionalInterface
public interface PunchGateway {
    PunchReceipt register(PunchData punch);
}
