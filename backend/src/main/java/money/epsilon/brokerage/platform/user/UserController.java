package money.epsilon.brokerage.platform.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import money.epsilon.brokerage.platform.user.UserService.UserView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (see SecurityConfig). */
@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserService service;

    UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    List<UserView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    UserView create(@Valid @RequestBody CreateUserRequest req) {
        return service.create(req.username(), req.displayName(), req.role(), req.password());
    }

    @PatchMapping("/{id}")
    UserView update(@PathVariable long id, @Valid @RequestBody UpdateUserRequest req) {
        return service.update(id, req.displayName(), req.role(), req.active());
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(@PathVariable long id, @Valid @RequestBody ResetPasswordRequest req) {
        service.resetPassword(id, req.password());
    }

    record CreateUserRequest(@NotBlank String username,
                             @NotBlank @Size(max = 200) String displayName,
                             @NotNull Role role,
                             @NotBlank String password) {
    }

    record UpdateUserRequest(@Size(min = 1, max = 200) String displayName, Role role, Boolean active) {
    }

    record ResetPasswordRequest(@NotBlank String password) {
    }
}
