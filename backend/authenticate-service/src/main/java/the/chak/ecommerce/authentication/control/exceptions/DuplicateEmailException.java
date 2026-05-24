package the.chak.ecommerce.authentication.control.exceptions;

import jakarta.ws.rs.core.Response;

public class DuplicateEmailException extends FunctionalException {

    public DuplicateEmailException(String email) {
        super(Response.Status.CONFLICT, "EMAIL_ALREADY_EXISTS", "Email already registered: " + email);
    }
}
