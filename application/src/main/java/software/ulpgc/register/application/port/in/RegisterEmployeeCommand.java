package software.ulpgc.register.application.port.in;

import software.ulpgc.register.domain.model.Employee;

public interface RegisterEmployeeCommand {
    Employee execute(String id, String name);
}