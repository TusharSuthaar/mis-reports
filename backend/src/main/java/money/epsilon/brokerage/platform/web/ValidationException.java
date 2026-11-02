package money.epsilon.brokerage.platform.web;

/**
 * Invalid input, in words written for the person who typed it ("Enter the deposit date", "Row 12: 'abc' is not an
 * amount"): a 400 with this message on the screen (audit M-02). Any other {@link IllegalArgumentException} — Java's,
 * Spring's, POI's — is unexpected: logged, and the screen gets only the reference.
 */
public class ValidationException extends IllegalArgumentException {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
