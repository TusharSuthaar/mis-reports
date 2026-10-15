package money.epsilon.brokerage.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import money.epsilon.brokerage.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

class MigrationIT extends IntegrationTestBase {

    @Autowired
    JdbcClient jdbc;

    @Test
    void flywayCreatesPlatformTables() {
        var tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
                .query(String.class).list();
        assertThat(tables).contains("app_user", "audit_log", "job", "flyway_schema_history");
    }

    @Test
    void auditLogRejectsUpdateAndDelete() {
        long id = jdbc.sql("INSERT INTO audit_log (entity, action, by_user) VALUES ('TEST', 'CREATE', 'test') RETURNING id")
                .query(Long.class).single();

        assertThatThrownBy(() -> jdbc.sql("UPDATE audit_log SET action = 'X' WHERE id = ?").param(id).update())
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM audit_log WHERE id = ?").param(id).update())
                .hasMessageContaining("append-only");
    }

    @Test
    void usernamesAreUniqueIgnoringCase() {
        jdbc.sql("INSERT INTO app_user (username, display_name, password_hash, role) VALUES ('dup.user', 'x', 'x', 'RM')").update();
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO app_user (username, display_name, password_hash, role) VALUES ('DUP.USER', 'x', 'x', 'RM')").update())
                .hasMessageContaining("ux_app_user_username");
    }
}
