
package software.ulpgc.register.infrastructure.adapter.out.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import software.ulpgc.register.domain.model.schedule.ContinuousSchedule;
import software.ulpgc.register.domain.model.schedule.Schedule;
import software.ulpgc.register.domain.model.schedule.ShiftSchedule;
import software.ulpgc.register.domain.model.schedule.SplitSchedule;
import software.ulpgc.register.domain.model.schedule.TimeSlot;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScheduleJsonCodec {

    private final ObjectMapper mapper = new ObjectMapper();

    public ObjectNode encode(Schedule schedule) {
        ObjectNode json = mapper.createObjectNode();

        json.put("id", schedule.id());
        json.put("name", schedule.name());

        if (schedule instanceof ContinuousSchedule continuous) {
            json.put("type", "CONTINUOUS");
            json.set("slot", encodeSlot(continuous.slot()));
            json.set("workingDays",
                    encodeDays(continuous.workingDays()));

        } else if (schedule instanceof SplitSchedule split) {
            json.put("type", "SPLIT");

            ArrayNode slots = mapper.createArrayNode();

            split.slots().forEach(
                    slot -> slots.add(encodeSlot(slot))
            );

            json.set("slots", slots);
            json.set("workingDays",
                    encodeDays(split.workingDays()));

        } else if (schedule instanceof ShiftSchedule shift) {
            json.put("type", "SHIFT");

            ObjectNode weeklySlots = mapper.createObjectNode();

            shift.weeklySlots().forEach((day, slot) ->
                    weeklySlots.set(
                            day.name(), encodeSlot(slot)
                    )
            );

            json.set("weeklySlots", weeklySlots);

        } else {
            throw new IllegalArgumentException(
                    "Unsupported schedule type"
            );
        }

        return json;
    }

    public Schedule decode(JsonNode json) {
        String id = json.path("id").asText();
        String name = json.path("name").asText();

        return switch (json.path("type").asText()) {
            case "CONTINUOUS" -> new ContinuousSchedule(
                    id,
                    name,
                    decodeSlot(json.path("slot")),
                    decodeDays(json.path("workingDays"))
            );

            case "SPLIT" -> new SplitSchedule(
                    id,
                    name,
                    decodeSlots(json.path("slots")),
                    decodeDays(json.path("workingDays"))
            );

            case "SHIFT" -> new ShiftSchedule(
                    id,
                    name,
                    decodeWeeklySlots(json.path("weeklySlots"))
            );

            default -> throw new IllegalArgumentException(
                    "Unsupported schedule type in storage"
            );
        };
    }

    private ObjectNode encodeSlot(TimeSlot slot) {
        ObjectNode json = mapper.createObjectNode();

        json.put("start", slot.start().toString());
        json.put("end", slot.end().toString());

        return json;
    }

    private TimeSlot decodeSlot(JsonNode json) {
        return new TimeSlot(
                LocalTime.parse(json.path("start").asText()),
                LocalTime.parse(json.path("end").asText())
        );
    }

    private ArrayNode encodeDays(Set<DayOfWeek> days) {
        ArrayNode result = mapper.createArrayNode();

        days.stream()
                .sorted()
                .forEach(day -> result.add(day.name()));

        return result;
    }

    private Set<DayOfWeek> decodeDays(JsonNode json) {
        Set<DayOfWeek> result =
                EnumSet.noneOf(DayOfWeek.class);

        json.forEach(day ->
                result.add(DayOfWeek.valueOf(day.asText()))
        );

        return result;
    }

    private List<TimeSlot> decodeSlots(JsonNode json) {
        List<TimeSlot> result = new ArrayList<>();

        json.forEach(slot ->
                result.add(decodeSlot(slot))
        );

        return List.copyOf(result);
    }

    private Map<DayOfWeek, TimeSlot> decodeWeeklySlots(
            JsonNode json
    ) {
        Map<DayOfWeek, TimeSlot> result =
                new EnumMap<>(DayOfWeek.class);

        json.fields().forEachRemaining(entry ->
                result.put(
                        DayOfWeek.valueOf(entry.getKey()),
                        decodeSlot(entry.getValue())
                )
        );

        return result;
    }
}
