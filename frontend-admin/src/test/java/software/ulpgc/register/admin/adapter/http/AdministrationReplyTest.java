package software.ulpgc.register.admin.adapter.http;

import org.junit.jupiter.api.Test;
import software.ulpgc.register.admin.model.Forms.Employee;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class AdministrationReplyTest {
    private static final URI SERVER = URI.create("http://localhost:7070");
    private static final Employee EMPLOYEE = new Employee("E", "Empleado de prueba");

    @Test void mismatchedConfirmationDoesNotShowSuccess() {
        var gateway = new HttpAdministrationGateway(SERVER, request ->
                CompletableFuture.completedFuture(new HttpAdministrationGateway.Reply(201, """
                    {"id":"OTRO","name":"Empleado de prueba"}""")));
        assertThatThrownBy(() -> gateway.createEmployee(EMPLOYEE).join())
                .cause().hasMessageContaining("no confirma los datos enviados");
    }

    @Test void connectionFailureDoesNotRepeatTheWrite() {
        var requests = new AtomicInteger();
        var gateway = new HttpAdministrationGateway(SERVER, request -> {
            requests.incrementAndGet();
            return CompletableFuture.failedFuture(new IOException("Conexión interrumpida"));
        });
        assertThatThrownBy(() -> gateway.createEmployee(EMPLOYEE).join())
                .cause().hasMessageContaining("Sin confirmación");
        assertThat(requests.get()).isOne();
    }
}
