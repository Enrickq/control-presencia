
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import software.ulpgc.register.application.port.out.ScheduleRepository;
import software.ulpgc.register.domain.model.schedule.Schedule;

import java.nio.file.Path;
import java.util.Optional;

public final class FileScheduleRepository
        implements ScheduleRepository {

    private final JsonlFile file;
    private final ScheduleJsonCodec codec;

    public FileScheduleRepository(Path path) {
        this.file = new JsonlFile(path);
        this.codec = new ScheduleJsonCodec();
    }

    @Override
    public void save(Schedule schedule) {
        file.append(codec.encode(schedule));
    }

    @Override
    public Optional<Schedule> findById(String scheduleId) {
        return file.readAll().stream()
                .map(codec::decode)
                .filter(schedule ->
                        schedule.id().equals(scheduleId))
                .findFirst();
    }
}
