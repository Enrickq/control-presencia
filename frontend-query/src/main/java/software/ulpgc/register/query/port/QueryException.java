package software.ulpgc.register.query.port;

public final class QueryException extends RuntimeException {
    public QueryException(String message) {
        super(message);
    }

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
