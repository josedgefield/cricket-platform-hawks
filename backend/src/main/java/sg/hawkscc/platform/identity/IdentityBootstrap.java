package sg.hawkscc.platform.identity;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import sg.hawkscc.platform.audit.AuditLog;
import sg.hawkscc.platform.club.ClubContext;

/** How the first accounts come to exist, since nobody can sign themselves up. */
final class IdentityBootstrap {

    private IdentityBootstrap() {
    }

    /**
     * On a server: if no superuser exists yet and HAWKS_BOOTSTRAP_SUPERUSER_EMAIL is set, invite
     * that person as the first superuser. They then invite everyone else from the app.
     */
    @Component
    @Order(2)
    static class FirstSuperuser implements ApplicationRunner {

        private final MemberService members;
        private final String email;
        private final String name;

        FirstSuperuser(MemberService members,
                       @Value("${hawks.identity.bootstrap-superuser.email:}") String email,
                       @Value("${hawks.identity.bootstrap-superuser.name:Club support}") String name) {
            this.members = members;
            this.email = email;
            this.name = name;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (!email.isBlank()) {
                members.bootstrapSuperuser(email, name);
            }
        }
    }

    /**
     * Local development and tests only: an active superuser with a known password, so you can
     * sign in straight away. Never active on a server (the profile isn't set there).
     */
    @Component
    @Profile({"dev", "test"})
    @Order(1)
    static class DevSuperuser implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(DevSuperuser.class);

        private final IdentityRepository repo;
        private final PasswordEncoder encoder;
        private final ClubContext club;
        private final AuditLog audit;
        private final String email;
        private final String password;
        private final String name;

        DevSuperuser(IdentityRepository repo, PasswordEncoder encoder, ClubContext club, AuditLog audit,
                     @Value("${hawks.identity.dev-superuser.email}") String email,
                     @Value("${hawks.identity.dev-superuser.password}") String password,
                     @Value("${hawks.identity.dev-superuser.name:Dev Support}") String name) {
            this.repo = repo;
            this.encoder = encoder;
            this.club = club;
            this.audit = audit;
            this.email = Tokens.normaliseEmail(email);
            this.password = password;
            this.name = name;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (repo.findMemberByEmail(club.clubId(), email).isPresent()) {
                return;
            }
            var id = repo.insertMember(club.clubId(), email, name, null, Role.SUPERUSER, MemberStatus.ACTIVE,
                    encoder.encode(password), null);
            audit.record(club.clubId(), null, "member.dev_superuser", "member", id, Map.of("email", email));
            log.info("Dev superuser {} created (password in application-dev.yml)", email);
        }
    }
}
