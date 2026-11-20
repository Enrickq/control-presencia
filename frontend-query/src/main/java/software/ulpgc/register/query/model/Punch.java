package software.ulpgc.register.query.model;

import java.time.Instant;

public record Punch(Instant timestamp, String placeId, Type type) {
    public enum Type { IN, OUT }
}
