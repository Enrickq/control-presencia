package software.ulpgc.register.admin.adapter.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.ulpgc.register.admin.model.Forms.Employee;
import software.ulpgc.register.infrastructure.configuration.ServerMain;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;

class AdminLoopbackTest {
    @Test void employeeCreationUsesTheProductionClient(@TempDir Path dir) throws Exception {
        var server = ServerMain.createServer(dir).start("127.0.0.1", 0);
        try (var client = HttpClient.newHttpClient()) {
            var gateway = new HttpAdministrationGateway(URI.create("http://127.0.0.1:" + server.port()), client);
            var demo = new Employee("TCP", "Empleado de prueba");
            gateway.createEmployee(demo).get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> gateway.createEmployee(demo).get(5, TimeUnit.SECONDS))
                    .cause().hasMessageContaining("409");
        } finally { server.stop(); }
    }
}
