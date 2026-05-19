package the.chak.ecommerce.authentication.control;

import java.util.Optional;
import org.bson.Document;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.entity.User;

@ApplicationScoped
public class UserService {

    public Optional<User> authenticateUser(AuthenticateRequest authenticateRequest) {

        final Document document = new Document();
        document.put("email", authenticateRequest.getEmail());
        User user = User.find(document).firstResult();
        boolean passwordMatched = user != null
                && BCrypt.checkpw(authenticateRequest.getPassword(), user.getPassword());

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
