package the.chak.ecommerce.authentication.control;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.control.exceptions.DuplicateEmailException;
import the.chak.ecommerce.authentication.entity.User;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
class UserServiceTest {

    @Inject
    UserService userService;

    @Test
    void addUser_newEmail_persistsUser() {
        // given
        String email = "test-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.email = email;
        user.password = "password123";
        user.roles = List.of("USER");

        // when
        userService.addUser(user);

        // then
        var found = userService.findUser(email);
        assertTrue(found.isPresent());
        assertEquals(email, found.get().email);
    }

    @Test
    void authenticateUser_correctPassword_returnsUser() {
        // given
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.email = email;
        user.password = "secretPassword";
        user.roles = List.of("USER");
        userService.addUser(user);

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("secretPassword");

        // when
        var result = userService.authenticateUser(request);

        // then
        assertTrue(result.isPresent());
        assertEquals(email, result.get().email);
    }

    @Test
    void authenticateUser_wrongPassword_returnsEmpty() {
        // given
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.email = email;
        user.password = "secretPassword";
        user.roles = List.of("USER");
        userService.addUser(user);

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("wrongPassword");

        // when
        var result = userService.authenticateUser(request);

        // then
        assertFalse(result.isPresent());
    }

    @Test
    void addUser_duplicateEmail_throwsDuplicateEmailException() {
        // given
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.email = email;
        user.password = "password123";
        user.roles = List.of("USER");
        userService.addUser(user);

        User duplicate = new User();
        duplicate.email = email;
        duplicate.password = "anotherPassword";
        duplicate.roles = List.of("USER");

        // when / then
        assertThrows(DuplicateEmailException.class, () -> userService.addUser(duplicate));
    }
}
