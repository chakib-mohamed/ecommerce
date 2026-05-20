package the.chak.ecommerce.authentication.control.exceptions;

import jakarta.ws.rs.core.Response;

public class DuplicateEmailException extends FunctionalException {

    public DuplicateEmailException(String email) {
        super("Email already registered: " + email, Response.Status.CONFLICT);
    }
}
