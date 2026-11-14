
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import software.ulpgc.register.application.port.out.EmployeeRepository;
import software.ulpgc.register.domain.model.Employee;

import java.nio.file.Path;
import java.util.Optional;

public final class FileEmployeeRepository
        implements EmployeeRepository {

    private final JsonlFile file;

    public FileEmployeeRepository(Path path) {
        this.file = new JsonlFile(path);
    }

    @Override
    public void save(Employee employee) {
        ObjectNode json = file.newObject();

        json.put("id", employee.id());
        json.put("name", employee.name());

        file.append(json);
    }

    @Override
    public Optional<Employee> findById(String employeeId) {
        return file.readAll().stream()
                .map(node -> new Employee(
                        node.path("id").asText(),
                        node.path("name").asText()
                ))
                .filter(employee ->
                        employee.id().equals(employeeId))
                .findFirst();
    }
}
