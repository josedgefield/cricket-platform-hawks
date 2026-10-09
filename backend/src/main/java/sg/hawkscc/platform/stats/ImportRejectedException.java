package sg.hawkscc.platform.stats;

import java.util.List;

/** The input was invalid; nothing was written apart from a failed sync run. */
public class ImportRejectedException extends RuntimeException {

    private final List<String> errors;

    public ImportRejectedException(List<String> errors) {
        super("Import rejected: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
