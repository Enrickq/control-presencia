package software.ulpgc.register.frontend.port;

public final class PunchException extends RuntimeException {
    public enum Kind { BAD_REQUEST, CONFLICT, UNAVAILABLE, INVALID_RESPONSE, SERVER_ERROR }

    private final Kind kind;

    public PunchException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public PunchException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
