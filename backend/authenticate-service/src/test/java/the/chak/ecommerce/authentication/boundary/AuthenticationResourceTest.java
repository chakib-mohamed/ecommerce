package the.chak.ecommerce.authentication.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.boundary.dto.SignUpRequest;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
public class AuthenticationResourceTest {

    @Test
    public void testSignUpAndAuthenticate() {
        String email = "api-test-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setEmail(email);
        signUpRequest.setPassword(password);

        // Test SignUp
        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201).body("email", is(email));

        // Test Authenticate
        AuthenticateRequest authRequest = new AuthenticateRequest();
        authRequest.setEmail(email);
        authRequest.setPassword(password);

        given().contentType(ContentType.JSON).body(authRequest).when().post("/users/authenticate")
                .then().statusCode(200).body("accessToken", notNullValue())
                .cookie("Authorization", notNullValue());
    }

    @Test
    public void testGetUserByEmail() {
        String email = "get-test-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setEmail(email);
        signUpRequest.setPassword("pass");

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201);

        given().when().get("/users/{email}", email).then().statusCode(200).body("email", is(email));
    }
}
