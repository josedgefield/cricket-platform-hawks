package sg.hawkscc.platform.identity;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
class IdentityConfig {

    /** bcrypt today; the {id} prefix lets the algorithm change later without resetting passwords. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    MailGateway mailGateway(@Value("${hawks.mail.transport:smtp}") String transport,
                            @Value("${hawks.mail.from}") String from, ObjectProvider<JavaMailSender> sender) {
        return switch (transport) {
            case "smtp" -> new SmtpMailGateway(sender, from);
            case "outbox" -> new OutboxMailGateway();
            default -> throw new IllegalStateException("hawks.mail.transport must be smtp or outbox, not " + transport);
        };
    }

    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }
}
