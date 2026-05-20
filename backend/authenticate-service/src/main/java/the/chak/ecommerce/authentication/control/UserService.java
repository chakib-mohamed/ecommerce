package the.chak.ecommerce.authentication.control;

import java.util.Optional;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.control.exceptions.DuplicateEmailException;
import the.chak.ecommerce.authentication.entity.User;

@ApplicationScoped
public class UserService {

    private static final String DUMMY_HASH = BCrypt.hashpw("dummy", BCrypt.gensalt());

    public Optional<User> authenticateUser(AuthenticateRequest request) {
        User user = User.find("email", request.getEmail()).firstResult();
        String hash = user != null ? user.getPassword() : DUMMY_HASH;
        boolean matched = BCrypt.checkpw(request.getPassword(), hash);
        return (user != null && matched) ? Optional.of(user) : Optional.empty();
    }

    public Optional<User> findUser(String email) {
        return Optional.ofNullable(User.find("email", email).firstResult());
    }

    public User addUser(User user) {
        if (User.find("email", user.getEmail()).count() > 0) {
            throw new DuplicateEmailException(user.getEmail());
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        user.persist();
        return user;
    }
}
