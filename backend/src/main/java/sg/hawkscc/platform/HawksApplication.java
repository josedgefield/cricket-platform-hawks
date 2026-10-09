package sg.hawkscc.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Hawks CC platform: one deployable app, split into modules (club, stats, security; later
 * identity, finance, comms). Module boundaries are checked by ModularityTests.
 */
@SpringBootApplication
public class HawksApplication {

    public static void main(String[] args) {
        SpringApplication.run(HawksApplication.class, args);
    }
}
