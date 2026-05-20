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

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201).body("email", is(email));

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
        String password = "pass123";

        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setEmail(email);
        signUpRequest.setPassword(password);

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201);

        AuthenticateRequest authRequest = new AuthenticateRequest();
        authRequest.setEmail(email);
        authRequest.setPassword(password);

        String cookie = given().contentType(ContentType.JSON).body(authRequest)
                .when().post("/users/authenticate").then().statusCode(200)
                .extract().cookie("Authorization");

        given().cookie("Authorization", cookie).when().get("/users/{email}", email)
                .then().statusCode(200).body("email", is(email));
    }

    @Test
    public void testWrongPasswordReturns401() {
        String email = "wrong-pass-" + UUID.randomUUID() + "@example.com";

        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setEmail(email);
        signUpRequest.setPassword("correctPassword");

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201);

        AuthenticateRequest authRequest = new AuthenticateRequest();
        authRequest.setEmail(email);
        authRequest.setPassword("wrongPassword");

        given().contentType(ContentType.JSON).body(authRequest)
                .when().post("/users/authenticate").then().statusCode(401);
    }

    @Test
    public void testDuplicateEmailReturns409() {
        String email = "dup-" + UUID.randomUUID() + "@example.com";

        SignUpRequest signUpRequest = new SignUpRequest();
        signUpRequest.setEmail(email);
        signUpRequest.setPassword("password123");

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(201);

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(409);
    }

    @Test
    public void testInvalidInputReturns400() {
        SignUpRequest signUpRequest = new SignUpRequest();

        given().contentType(ContentType.JSON).body(signUpRequest).when().post("/users").then()
                .statusCode(400);
    }

    @Test
    public void testGetUserWithoutAuthReturns401() {
        given().when().get("/users/anyone@example.com").then().statusCode(401);
    }
}
