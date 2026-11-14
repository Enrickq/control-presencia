
package software.ulpgc.register.application.port.out;

import software.ulpgc.register.domain.model.Employee;

import java.util.Optional;

public interface EmployeeRepository {

    void save(Employee employee);

    Optional<Employee> findById(String employeeId);
}
