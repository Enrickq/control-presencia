
package software.ulpgc.register.infrastructure.adapter.in.http;

import io.javalin.Javalin;
import software.ulpgc.register.application.port.in.RegisterPunchCommand;
import software.ulpgc.register.application.port.in.TrackingEventsQuery;
import software.ulpgc.register.domain.model.Employee;
import software.ulpgc.register.domain.model.Place;
import software.ulpgc.register.domain.model.StoredTrackingEvent;
import software.ulpgc.register.domain.model.TrackingEvent;
import software.ulpgc.register.domain.model.TrackingType;
import io.javalin.http.NotFoundResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class ApiServer {

    private ApiServer() {
    }

    public static Javalin create(
            RegisterPunchCommand register,
            TrackingEventsQuery query,
            AdministrationHandlers administration
    ) {
        Objects.requireNonNull(register);
        Objects.requireNonNull(query);
        Objects.requireNonNull(administration);

        return Javalin.create(config -> {

            config.routes.get("/api/v1/health", ctx ->
                    ctx.json(Map.of("status", "UP"))
            );

            config.routes.post("/api/v1/punches", ctx -> {
                PunchRequest request =
                        ctx.bodyAsClass(PunchRequest.class);

                if (request == null) {
                    throw new IllegalArgumentException(
                            "Request body is required"
                    );
                }

                String employeeId = required(
                        request.employeeId(), "employeeId"
                );
                String employeeName = required(
                        request.employeeName(), "employeeName"
                );
                String placeId = required(
                        request.placeId(), "placeId"
                );
                String placeName = required(
                        request.placeName(), "placeName"
                );

                TrackingType type = parseType(request.type());

                TrackingEvent event = new TrackingEvent(
                        Instant.now(),
                        new Employee(employeeId, employeeName),
                        new Place(placeId, placeName),
                        type
                );

                register.execute(event);

                ctx.status(201);
                ctx.json(new PunchResponse(
                        employeeId,
                        placeId,
                        type.name().toUpperCase(Locale.ROOT),
                        event.ts().toString()
                ));
            });

            config.routes.get("/api/v1/punches", ctx -> {
                String employeeId = required(
                        ctx.queryParam("employeeId"),
                        "employeeId"
                );

                String dateText = required(
                        ctx.queryParam("date"),
                        "date"
                );

                LocalDate date = LocalDate.parse(dateText);

                ctx.json(
                        query.execute(date, employeeId)
                                .stream()
                                .map(ApiServer::toResponse)
                                .toList()
                );
            });


            config.routes.post(
                    "/api/v1/employees",
                    administration::createEmployee
            );

            config.routes.get(
                    "/api/v1/employees/{id}",
                    administration::getEmployee
            );

            config.routes.post(
                    "/api/v1/schedules",
                    administration::createSchedule
            );

            config.routes.get(
                    "/api/v1/schedules/{id}",
                    administration::getSchedule
            );

            config.routes.post(
                    "/api/v1/assignments",
                    administration::createAssignment
            );

            config.routes.get(
                    "/api/v1/employees/{id}/assignments",
                    administration::getAssignments
            );


            config.routes.exception(
                    IllegalArgumentException.class,
                    (error, ctx) -> {
                        ctx.status(400);
                        ctx.json(Map.of(
                                "error", "BAD_REQUEST",
                                "message", error.getMessage()
                        ));
                    }
            );

            config.routes.exception(
                    DateTimeParseException.class,
                    (error, ctx) -> {
                        ctx.status(400);
                        ctx.json(Map.of(
                                "error", "INVALID_DATE",
                                "message", "Expected YYYY-MM-DD"
                        ));
                    }
            );

            config.routes.exception(
                    IllegalStateException.class,
                    (error, ctx) -> {
                        ctx.status(409);
                        ctx.json(Map.of(
                                "error", "CONFLICT",
                                "message", error.getMessage()
                        ));
                    }
            );

            config.routes.exception(
                    NotFoundResponse.class,
                    (error, ctx) -> {
                        ctx.status(404);
                        ctx.json(Map.of(
                                "error", "NOT_FOUND",
                                "message", error.getMessage()
                        ));
                    }
            );
        });
    }

    private static String required(
            String value,
            String field
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    private static TrackingType parseType(String value) {
        return switch (
                required(value, "type")
                        .toUpperCase(Locale.ROOT)
                ) {
            case "IN" -> TrackingType.In;
            case "OUT" -> TrackingType.Out;
            default -> throw new IllegalArgumentException(
                    "type must be IN or OUT"
            );
        };
    }

    private static PunchResponse toResponse(
            StoredTrackingEvent event
    ) {
        return new PunchResponse(
                event.employeeId(),
                event.placeId(),
                event.type().name().toUpperCase(Locale.ROOT),
                event.ts().toString()
        );
    }
}
