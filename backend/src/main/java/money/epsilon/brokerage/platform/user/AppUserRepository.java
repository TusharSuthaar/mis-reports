package money.epsilon.brokerage.platform.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    /** Clears the sign-in limit (R-101): wrong-password count, wait and lock. */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "UPDATE app_user SET failed_logins = 0, locked_until = NULL, locked = FALSE WHERE id = :id",
            nativeQuery = true)
    int clearSignInLimit(@org.springframework.data.repository.query.Param("id") long id);

    boolean existsByUsernameIgnoreCase(String username);

    long countByRoleAndActiveTrue(Role role);
}
