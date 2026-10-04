package sg.hawkscc.platform.stats.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import sg.hawkscc.platform.stats.ImportRejectedException;
import sg.hawkscc.platform.stats.NotFoundException;

/** Errors as RFC 9457 problem details, with messages a stats admin can act on. */
@RestControllerAdvice(basePackageClasses = StatsErrorHandler.class)
class StatsErrorHandler {

    @ExceptionHandler(ImportRejectedException.class)
    ProblemDetail rejected(ImportRejectedException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT,
                "The file was not imported. Fix the problems listed and try again.");
        p.setTitle("Import rejected");
        p.setProperty("errors", e.errors());
        return p;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }
}
