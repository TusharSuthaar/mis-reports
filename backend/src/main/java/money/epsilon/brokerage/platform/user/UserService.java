package money.epsilon.brokerage.platform.user;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import money.epsilon.brokerage.platform.audit.AuditService;
import money.epsilon.brokerage.platform.security.CurrentUser;
import money.epsilon.brokerage.platform.web.NotFoundException;
import money.epsilon.brokerage.platform.web.RuleViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final String AUDIT_ENTITY = "USER";
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,49}$");

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    UserService(AppUserRepository repository, PasswordEncoder passwordEncoder, AuditService audit) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserView> list() {
        return repository.findAll(Sort.by("username")).stream().map(UserView::of).toList();
    }

    @Transactional
    public UserView create(String username, String displayName, Role role, String password) {
        String name = normaliseUsername(username);
        if (repository.existsByUsernameIgnoreCase(name)) {
            throw new RuleViolationException("Username '" + name + "' is already taken");
        }
        PasswordPolicy.check(password, name);
        AppUser user = repository.save(new AppUser(name, displayName.trim(), passwordEncoder.encode(password), role));
        UserView view = UserView.of(user);
        audit.record(AUDIT_ENTITY, user.getId(), "CREATE", null, view);
        return view;
    }

    /** Null arguments mean "leave unchanged". */
    @Transactional
    public UserView update(long id, String displayName, Role role, Boolean active) {
        AppUser user = find(id);
        boolean changesAccess = (role != null && role != user.getRole()) || (active != null && active != user.isActive());
        if (changesAccess && user.getUsername().equalsIgnoreCase(CurrentUser.username())) {
            throw new RuleViolationException("You cannot change your own role or deactivate yourself");
        }
        UserView before = UserView.of(user);
        if (displayName != null) user.setDisplayName(displayName.trim());
        if (role != null) user.setRole(role);
        if (active != null) user.setActive(active);
        repository.flush();
        if (repository.countByRoleAndActiveTrue(Role.ADMIN) == 0) {
            throw new RuleViolationException("At least one active ADMIN must remain");
        }
        UserView after = UserView.of(user);
        audit.record(AUDIT_ENTITY, id, "UPDATE", before, after);
        return after;
    }

    @Transactional
    public void resetPassword(long id, String password) {
        AppUser user = find(id);
        PasswordPolicy.check(password, user.getUsername());
        user.setPasswordHash(passwordEncoder.encode(password));
        audit.record(AUDIT_ENTITY, id, "PASSWORD_RESET", null, null);
    }

    /** Creates the first ADMIN; does nothing if any user already exists. */
    @Transactional
    public boolean createFirstAdminIfNoUsers(String username, String password) {
        if (repository.count() > 0) return false;
        create(username, username, Role.ADMIN, password);
        return true;
    }

    private AppUser find(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("User " + id + " not found"));
    }

    private static String normaliseUsername(String username) {
        String name = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-50 characters: letters, digits, '.', '_' or '-', starting with a letter or digit");
        }
        return name;
    }

    /** Safe to return and to audit: never contains the password hash. */
    public record UserView(Long id, String username, String displayName, Role role, Long employeeId,
                           boolean active, Instant createdAt, Instant updatedAt) {

        static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getRole(), u.getEmployeeId(),
                    u.isActive(), u.getCreatedAt(), u.getUpdatedAt());
        }
    }
}
