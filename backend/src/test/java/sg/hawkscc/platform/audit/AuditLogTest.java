package sg.hawkscc.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AuditLogTest {

    @Test
    void detailsAreEscapedJson() {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("name", "Ann \"AJ\" Lee\n");
        d.put("phone", null);
        assertThat(AuditLog.json(d)).isEqualTo("{\"name\":\"Ann \\\"AJ\\\" Lee\\n\",\"phone\":null}");
        assertThat(AuditLog.json(Map.of())).isEqualTo("{}");
    }
}
