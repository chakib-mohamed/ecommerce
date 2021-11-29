package chakmed.ecommerce.authentication.control;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;

import chakmed.ecommerce.authentication.boundary.AuthenticateRequest;
import chakmed.ecommerce.authentication.entity.User;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;

@ApplicationScoped
public class UserService {

    public Optional<User> authenticateUser(AuthenticateRequest authenticateRequest) {

        final Document document = new Document();
        document.put("email", authenticateRequest.getEmail());
        User user = User.find(document).firstResult();
        var passwordMatched = Optional.ofNullable(user)
                .map(u -> BCrypt.checkpw(authenticateRequest.getPassword(), user.getPassword())).orElse(false);

        return passwordMatched ? Optional.of(user) : Optional.empty();

    }

    public Optional<User> findUser(String email) {

        final Document document = new Document();
        document.put("email", email);
        User user = User.find(document).firstResult();

        return Optional.ofNullable(user);

    }

    public User addUser(User user) {
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        user.persist();

        return user;
    }

}
