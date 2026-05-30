package the.chak.ecommerce.authentication.control;

import java.util.Optional;
import org.jboss.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.control.exceptions.DuplicateEmailException;
import the.chak.ecommerce.authentication.entity.User;
import the.chak.ecommerce.authentication.repository.UserRepository;

@ApplicationScoped
public class UserService {

    private static final Logger LOG = Logger.getLogger(UserService.class);

    private static final String DUMMY_HASH = BCrypt.hashpw("dummy", BCrypt.gensalt());

    @Inject
    UserRepository userRepository;

    public Optional<User> authenticateUser(AuthenticateRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        String hash = user.isPresent() ? user.get().getPassword() : DUMMY_HASH;
        boolean matched = BCrypt.checkpw(request.getPassword(), hash);
        if (user.isPresent() && matched) {
            LOG.infof("Login successful email=%s", request.getEmail());
            return user;
        }
        LOG.warnf("Login failed email=%s", request.getEmail());
        return Optional.empty();
    }

    public Optional<User> findUser(String email) {
        return userRepository.findByEmail(email);
    }

    public User addUser(User user) {
        if (userRepository.countByEmail(user.getEmail()) > 0) {
            throw new DuplicateEmailException(user.getEmail());
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        userRepository.persistOrUpdate(user);
        LOG.infof("User registered email=%s", user.getEmail());
        return user;
    }
}
