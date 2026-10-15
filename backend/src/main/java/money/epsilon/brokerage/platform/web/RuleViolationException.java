package money.epsilon.brokerage.platform.web;

/** The request is well-formed but breaks a business rule (duplicate, last admin, ...). Mapped to 409. */
public class RuleViolationException extends RuntimeException {

    public RuleViolationException(String message) {
        super(message);
    }
}
