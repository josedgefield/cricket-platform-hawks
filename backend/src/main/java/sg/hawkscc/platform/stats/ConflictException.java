package sg.hawkscc.platform.stats;

/** The request contradicts data already stored; nothing was changed. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
