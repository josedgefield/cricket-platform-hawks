package sg.hawkscc.platform.finance;

import org.springframework.http.HttpStatus;

/** A refusal with a message the admin can act on, returned as a problem detail. */
public class FinanceException extends RuntimeException {

    private final HttpStatus status;

    public FinanceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    static FinanceException badRequest(String message) {
        return new FinanceException(HttpStatus.BAD_REQUEST, message);
    }

    static FinanceException conflict(String message) {
        return new FinanceException(HttpStatus.CONFLICT, message);
    }

    static FinanceException notFound(String what) {
        return new FinanceException(HttpStatus.NOT_FOUND, "No such " + what + ".");
    }
}
