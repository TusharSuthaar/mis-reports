package money.epsilon.brokerage.platform.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** On an empty user table, creates the first ADMIN from mis.bootstrap-admin.* (env MIS_BOOTSTRAP_ADMIN_*). */
@Component
class FirstAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminBootstrap.class);

    private final UserService users;
    private final AppUserRepository repository;
    private final String username;
    private final String password;

    FirstAdminBootstrap(UserService users, AppUserRepository repository,
                        @Value("${mis.bootstrap-admin.username:}") String username,
                        @Value("${mis.bootstrap-admin.password:}") String password) {
        this.users = users;
        this.repository = repository;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) return;
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            log.warn("No users exist. Set MIS_BOOTSTRAP_ADMIN_USERNAME and MIS_BOOTSTRAP_ADMIN_PASSWORD to create the first ADMIN.");
            return;
        }
        if (users.createFirstAdminIfNoUsers(username, password)) {
            log.info("Created first ADMIN user '{}'", username.trim().toLowerCase());
        }
    }
}
