package the.chak.ecommerce.authentication.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.control.exceptions.DuplicateEmailException;
import the.chak.ecommerce.authentication.entity.User;
import the.chak.ecommerce.authentication.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    // A real registry so login/registration outcome counters are recorded and assertable.
    @Spy
    MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @InjectMocks
    UserService userService;

    @Test
    @DisplayName("Hashes the password and persists the user when the email is not yet registered")
    void addUser_newEmail_hashesPasswordAndPersists() {
        // given
        String email = "test@example.com";
        User user = new User();
        user.email = email;
        user.password = "password123";

        when(userRepository.countByEmail(email)).thenReturn(0L);

        // when
        User result = userService.addUser(user);

        // then
        assertNotNull(result);
        assertTrue(BCrypt.checkpw("password123", result.password));
        verify(userRepository).persistOrUpdate(user);
    }

    @Test
    @DisplayName("Throws DuplicateEmailException when the email is already registered")
    void addUser_duplicateEmail_throwsDuplicateEmailException() {
        // given
        String email = "dup@example.com";
        User user = new User();
        user.email = email;

        when(userRepository.countByEmail(email)).thenReturn(1L);

        // when & then
        assertThrows(DuplicateEmailException.class, () -> userService.addUser(user));
    }

    @Test
    @DisplayName("Counts a successful registration outcome when a new user is registered")
    void addUser_newEmail_recordsSuccessOutcome() {
        // given
        String email = "metric-reg@example.com";
        User user = new User();
        user.email = email;
        user.password = "password123";

        when(userRepository.countByEmail(email)).thenReturn(0L);

        // when
        userService.addUser(user);

        // then
        assertEquals(1.0,
                meterRegistry.get("auth.registrations").tag("outcome", "success").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Counts a failed registration outcome when the email is already registered")
    void addUser_duplicateEmail_recordsFailureOutcome() {
        // given
        String email = "metric-dup@example.com";
        User user = new User();
        user.email = email;

        when(userRepository.countByEmail(email)).thenReturn(1L);

        // when & then
        assertThrows(DuplicateEmailException.class, () -> userService.addUser(user));
        assertEquals(1.0,
                meterRegistry.get("auth.registrations").tag("outcome", "failure").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Returns the matching user when the password is correct")
    void authenticateUser_correctPassword_returnsUser() {
        // given
        String email = "auth@example.com";
        String password = "secretPassword";
        User user = new User();
        user.email = email;
        user.password = BCrypt.hashpw(password, BCrypt.gensalt());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        var result = userService.authenticateUser(request);

        // then
        assertTrue(result.isPresent());
        assertEquals(email, result.get().email);
    }

    @Test
    @DisplayName("Counts a successful login outcome when the password is correct")
    void authenticateUser_correctPassword_recordsSuccessOutcome() {
        // given
        String email = "metric-login@example.com";
        String password = "secretPassword";
        User user = new User();
        user.email = email;
        user.password = BCrypt.hashpw(password, BCrypt.gensalt());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword(password);

        // when
        userService.authenticateUser(request);

        // then
        assertEquals(1.0,
                meterRegistry.get("auth.logins").tag("outcome", "success").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Returns empty when the password does not match the stored hash")
    void authenticateUser_wrongPassword_returnsEmpty() {
        // given
        String email = "auth@example.com";
        User user = new User();
        user.email = email;
        user.password = BCrypt.hashpw("secretPassword", BCrypt.gensalt());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("wrongPassword");

        // when
        var result = userService.authenticateUser(request);

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Counts a failed login outcome when the password does not match")
    void authenticateUser_wrongPassword_recordsFailureOutcome() {
        // given
        String email = "metric-badlogin@example.com";
        User user = new User();
        user.email = email;
        user.password = BCrypt.hashpw("secretPassword", BCrypt.gensalt());

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("wrongPassword");

        // when
        userService.authenticateUser(request);

        // then
        assertEquals(1.0,
                meterRegistry.get("auth.logins").tag("outcome", "failure").counter().count(),
                0.001);
    }

    @Test
    @DisplayName("Returns the user when looking up an existing email")
    void findUser_existingEmail_returnsUser() {
        // given
        String email = "found@example.com";
        User user = new User();
        user.email = email;

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        // when
        var result = userService.findUser(email);

        // then
        assertTrue(result.isPresent());
        assertEquals(email, result.get().email);
    }
}
