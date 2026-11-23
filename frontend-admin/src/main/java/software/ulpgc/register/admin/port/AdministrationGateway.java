package software.ulpgc.register.admin.port;

import software.ulpgc.register.admin.model.Forms.Assignment;
import software.ulpgc.register.admin.model.Forms.Employee;
import software.ulpgc.register.admin.model.Forms.Schedule;

import java.util.concurrent.CompletableFuture;

public interface AdministrationGateway {
    CompletableFuture<Void> createEmployee(Employee employee);
    CompletableFuture<Void> createSchedule(Schedule schedule);
    CompletableFuture<Void> assign(Assignment assignment);
}
