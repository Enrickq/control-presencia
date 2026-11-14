
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.node.ObjectNode;
import software.ulpgc.register.application.port.out.ScheduleAssignmentRepository;
import software.ulpgc.register.domain.model.schedule.ScheduleAssignment;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public final class FileScheduleAssignmentRepository
        implements ScheduleAssignmentRepository {

    private final JsonlFile file;

    public FileScheduleAssignmentRepository(Path path) {
        this.file = new JsonlFile(path);
    }

    @Override
    public void save(ScheduleAssignment assignment) {
        ObjectNode json = file.newObject();

        json.put("employeeId", assignment.employeeId());
        json.put("scheduleId", assignment.scheduleId());
        json.put("validFrom", assignment.validFrom().toString());
        json.put("validThrough", assignment.validThrough().toString());

        file.append(json);
    }

    @Override
    public List<ScheduleAssignment> findByEmployeeId(
            String employeeId
    ) {
        return file.readAll().stream()
                .map(node -> new ScheduleAssignment(
                        node.path("employeeId").asText(),
                        node.path("scheduleId").asText(),
                        LocalDate.parse(
                                node.path("validFrom").asText()
                        ),
                        LocalDate.parse(
                                node.path("validThrough").asText()
                        )
                ))
                .filter(assignment ->
                        assignment.employeeId().equals(employeeId))
                .toList();
    }
}
