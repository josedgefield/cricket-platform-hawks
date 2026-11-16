package sg.hawkscc.platform.identity;

import org.springframework.http.HttpStatus;

/** A refusal with a message the user can act on, returned as a problem detail. */
public class IdentityException extends RuntimeException {

    private final HttpStatus status;

    public IdentityException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static IdentityException forbidden(String message) {
        return new IdentityException(HttpStatus.FORBIDDEN, message);
    }

    static IdentityException conflict(String message) {
        return new IdentityException(HttpStatus.CONFLICT, message);
    }

    static IdentityException badRequest(String message) {
        return new IdentityException(HttpStatus.BAD_REQUEST, message);
    }

    static IdentityException notFound() {
        return new IdentityException(HttpStatus.NOT_FOUND, "No such member.");
    }
}
