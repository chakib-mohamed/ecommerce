package the.chak.ecommerce.authentication.control;

import io.micrometer.core.instrument.MeterRegistry;
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

    @Inject
    MeterRegistry meterRegistry;

    public Optional<User> authenticateUser(AuthenticateRequest request) {
        Optional<User> user = userRepository.findByEmail(request.getEmail());
        String hash = user.isPresent() ? user.get().getPassword() : DUMMY_HASH;
        boolean matched = BCrypt.checkpw(request.getPassword(), hash);
        if (user.isPresent() && matched) {
            recordLogin(MetricNames.OUTCOME_SUCCESS);
            LOG.infof("Login successful email=%s", request.getEmail());
            return user;
        }
        recordLogin(MetricNames.OUTCOME_FAILURE);
        LOG.warnf("Login failed email=%s", request.getEmail());
        return Optional.empty();
    }

    public Optional<User> findUser(String email) {
        return userRepository.findByEmail(email);
    }

    public User addUser(User user) {
        if (userRepository.countByEmail(user.getEmail()) > 0) {
            recordRegistration(MetricNames.OUTCOME_FAILURE);
            throw new DuplicateEmailException(user.getEmail());
        }
        user.setPassword(BCrypt.hashpw(user.getPassword(), BCrypt.gensalt()));
        userRepository.persistOrUpdate(user);
        recordRegistration(MetricNames.OUTCOME_SUCCESS);
        LOG.infof("User registered email=%s", user.getEmail());
        return user;
    }

    private void recordLogin(String outcome) {
        meterRegistry.counter(MetricNames.AUTH_LOGINS, MetricNames.TAG_OUTCOME, outcome).increment();
    }

    private void recordRegistration(String outcome) {
        meterRegistry.counter(MetricNames.AUTH_REGISTRATIONS, MetricNames.TAG_OUTCOME, outcome)
                .increment();
    }
}
