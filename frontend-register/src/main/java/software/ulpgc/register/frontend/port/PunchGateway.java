package software.ulpgc.register.frontend.port;

import software.ulpgc.register.frontend.model.PunchData;
import software.ulpgc.register.frontend.model.PunchReceipt;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface PunchGateway {
    CompletableFuture<PunchReceipt> register(PunchData punch);
}
