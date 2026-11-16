package sg.hawkscc.platform.comms;

import org.springframework.http.HttpStatus;

/** A refusal with a message the user can act on, returned as a problem detail. */
public class CommsException extends RuntimeException {

    private final HttpStatus status;

    public CommsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static CommsException badRequest(String message) {
        return new CommsException(HttpStatus.BAD_REQUEST, message);
    }

    static CommsException forbidden(String message) {
        return new CommsException(HttpStatus.FORBIDDEN, message);
    }

    static CommsException notFound(String what) {
        return new CommsException(HttpStatus.NOT_FOUND, "No such " + what + ".");
    }
}
