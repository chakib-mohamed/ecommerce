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
class AuthenticationResourceTest {

    @Test
    void signUp_validRequest_returns201WithEmail() {
        // given
        String email = "signup-" + UUID.randomUUID() + "@example.com";
        SignUpRequest request = new SignUpRequest();
        request.setEmail(email);
        request.setPassword("password123");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(201).body("email", is(email));
    }

    @Test
    void authenticate_validCredentials_returns200WithTokenAndCookie() {
        // given
        String email = "auth-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword("password123");
        given().contentType(ContentType.JSON).body(signUp).when().post("/users");

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("password123");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users/authenticate");

        // then
        response.then().statusCode(200)
                .body("accessToken", notNullValue())
                .cookie("Authorization", notNullValue());
    }

    @Test
    void getUser_authenticatedRequest_returnsUserDetails() {
        // given
        String email = "get-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword("pass123");
        given().contentType(ContentType.JSON).body(signUp).when().post("/users");

        AuthenticateRequest auth = new AuthenticateRequest();
        auth.setEmail(email);
        auth.setPassword("pass123");
        String cookie = given().contentType(ContentType.JSON).body(auth)
                .when().post("/users/authenticate").then().extract().cookie("Authorization");

        // when
        var response = given().cookie("Authorization", cookie)
                .when().get("/users/{email}", email);

        // then
        response.then().statusCode(200).body("email", is(email));
    }

    @Test
    void authenticate_wrongPassword_returns401() {
        // given
        String email = "wrong-pass-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword("correctPassword");
        given().contentType(ContentType.JSON).body(signUp).when().post("/users");

        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword("wrongPassword");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users/authenticate");

        // then
        response.then().statusCode(401);
    }

    @Test
    void signUp_duplicateEmail_returns409() {
        // given
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        SignUpRequest request = new SignUpRequest();
        request.setEmail(email);
        request.setPassword("password123");
        given().contentType(ContentType.JSON).body(request).when().post("/users");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(409);
    }

    @Test
    void signUp_missingFields_returns400() {
        // given
        SignUpRequest request = new SignUpRequest();

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(400);
    }

    @Test
    void getUser_unauthenticated_returns401() {
        // when
        var response = given().when().get("/users/anyone@example.com");

        // then
        response.then().statusCode(401);
    }
}
