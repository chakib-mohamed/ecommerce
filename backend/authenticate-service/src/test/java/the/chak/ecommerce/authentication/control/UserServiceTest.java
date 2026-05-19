package the.chak.ecommerce.authentication.control;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.entity.User;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
public class UserServiceTest {

    @Inject
    UserService userService;

    @Test
    public void testAddAndFindUser() {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        User user = new User();
        user.email = email;
        user.password = "password123";
        user.roles = List.of("USER");

        userService.addUser(user);

        var foundUser = userService.findUser(email);
        Assertions.assertTrue(foundUser.isPresent());
        Assertions.assertEquals(email, foundUser.get().email);
    }

    @Test
    public void testAuthenticateUser() {
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        String password = "secretPassword";
        User user = new User();
        user.email = email;
        user.password = password;
        user.roles = List.of("USER");

        userService.addUser(user);

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword(password);

        var authenticatedUser = userService.authenticateUser(request);
        Assertions.assertTrue(authenticatedUser.isPresent());
        Assertions.assertEquals(email, authenticatedUser.get().email);

        // Test wrong password
        request.setPassword("wrongPassword");
        var failedAuth = userService.authenticateUser(request);
        Assertions.assertFalse(failedAuth.isPresent());
    }
}
