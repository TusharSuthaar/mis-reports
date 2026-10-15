package money.epsilon.brokerage.platform.security;

import java.util.Collection;
import java.util.List;
import money.epsilon.brokerage.platform.user.AppUser;
import money.epsilon.brokerage.platform.user.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** The signed-in user as held in the session. Refreshed from the database on every request. */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer {

    private final long id;
    private final String username;
    private final String displayName;
    private final Role role;
    private final boolean active;
    private String passwordHash;

    AppUserPrincipal(AppUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.displayName = user.getDisplayName();
        this.role = user.getRole();
        this.active = user.isActive();
        this.passwordHash = user.getPasswordHash();
    }

    public long id() { return id; }
    public String displayName() { return displayName; }
    public Role role() { return role; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return active; }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }

    boolean sameAccessAs(AppUser user) {
        return user.isActive() == active && user.getRole() == role
                && user.getUsername().equals(username) && user.getDisplayName().equals(displayName);
    }
}
