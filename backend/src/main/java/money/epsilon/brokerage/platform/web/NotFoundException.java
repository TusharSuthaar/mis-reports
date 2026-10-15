package money.epsilon.brokerage.platform.web;

/** The requested record does not exist, or the caller may not see it. Mapped to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
