
package software.ulpgc.register.application.usecase;

import software.ulpgc.register.application.port.in.RegisterEmployeeCommand;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.domain.model.Employee;

import java.util.Objects;

public record RegisterEmployee(
        EmployeeRepository employees
) implements RegisterEmployeeCommand {

    public RegisterEmployee {
        Objects.requireNonNull(employees);
    }

    @Override
    public Employee execute(String id, String name) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee id is required"
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Employee name is required"
            );
        }

        if (employees.findById(id).isPresent()) {
            throw new IllegalStateException(
                    "Employee already exists"
            );
        }

        Employee employee = new Employee(id, name);
        employees.save(employee);

        return employee;
    }
}
