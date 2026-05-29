package the.chak.ecommerce.authentication.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.control.exceptions.DuplicateEmailException;
import the.chak.ecommerce.authentication.entity.User;
import the.chak.ecommerce.authentication.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    UserService userService;

    @Test
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
