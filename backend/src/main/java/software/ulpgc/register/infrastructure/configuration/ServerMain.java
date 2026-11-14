
package software.ulpgc.register.infrastructure.configuration;

import io.javalin.Javalin;

import software.ulpgc.register.application.usecase.AssignScheduleToEmployee;
import software.ulpgc.register.application.usecase.ListTrackingEvents;
import software.ulpgc.register.application.usecase.ReadAdministrationData;
import software.ulpgc.register.application.usecase.RegisterEmployee;
import software.ulpgc.register.application.usecase.RegisterSchedule;
import software.ulpgc.register.application.usecase.RegisterTrackingEvent;

import software.ulpgc.register.infrastructure.adapter.in.http.AdministrationHandlers;
import software.ulpgc.register.infrastructure.adapter.in.http.ApiServer;

import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEmployeeRepository;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileEventStore;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileScheduleAssignmentRepository;
import software.ulpgc.register.infrastructure.adapter.out.persistence.FileScheduleRepository;

import java.nio.file.Path;

public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(
                System.getenv().getOrDefault(
                        "PORT", "7070"
                )
        );

        Path dataDirectory = Path.of(
                System.getenv().getOrDefault(
                        "DATA_DIR", "data/events"
                )
        );

        FileEventStore events =
                new FileEventStore(dataDirectory.toFile());

        FileEmployeeRepository employees =
                new FileEmployeeRepository(
                        dataDirectory.resolve("employees.jsonl")
                );

        FileScheduleRepository schedules =
                new FileScheduleRepository(
                        dataDirectory.resolve("schedules.jsonl")
                );

        FileScheduleAssignmentRepository assignments =
                new FileScheduleAssignmentRepository(
                        dataDirectory.resolve("assignments.jsonl")
                );

        AdministrationHandlers administration =
                new AdministrationHandlers(
                        new RegisterEmployee(employees),
                        new RegisterSchedule(schedules),
                        new AssignScheduleToEmployee(
                                employees,
                                schedules,
                                assignments
                        ),
                        new ReadAdministrationData(
                                employees,
                                schedules,
                                assignments
                        )
                );

        Javalin server = ApiServer.create(
                new RegisterTrackingEvent(events),
                new ListTrackingEvents(events),
                administration
        );

        server.start("0.0.0.0", port);
    }
}
