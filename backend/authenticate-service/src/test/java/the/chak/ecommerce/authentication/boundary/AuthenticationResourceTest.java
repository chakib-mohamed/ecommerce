package the.chak.ecommerce.authentication.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.boundary.dto.SignUpRequest;

@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@Tag("integration")
class AuthenticationResourceTest {

    @Test
    @DisplayName("Returns 201 with the created user's email when the sign-up request is valid")
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
    @DisplayName("Returns 200 with an access token and Authorization cookie when credentials are valid")
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
                .body("access_token", notNullValue())
                .cookie("Authorization", notNullValue());
    }

    @Test
    @DisplayName("Returns the user's details when the request carries a valid authentication cookie")
    void getUser_authenticatedRequest_returnsUserDetails() {
        // given
        String email = "get-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword("pass1234");
        given().contentType(ContentType.JSON).body(signUp).when().post("/users");

        AuthenticateRequest auth = new AuthenticateRequest();
        auth.setEmail(email);
        auth.setPassword("pass1234");
        String cookie = given().contentType(ContentType.JSON).body(auth)
                .when().post("/users/authenticate").then().extract().cookie("Authorization");

        // when
        var response = given().cookie("Authorization", cookie)
                .when().get("/users/{email}", email);

        // then
        response.then().statusCode(200).body("email", is(email));
    }

    @Test
    @DisplayName("Returns 401 when the password does not match")
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
    @DisplayName("Returns 409 with EMAIL_ALREADY_EXISTS when the email is already registered")
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
        response.then().statusCode(409)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("Returns 400 with VALIDATION_ERROR when required sign-up fields are missing")
    void signUp_missingFields_returns400() {
        // given
        SignUpRequest request = new SignUpRequest();

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Returns 400 with VALIDATION_ERROR when the password is shorter than the minimum length")
    void signUp_passwordTooShort_returns400() {
        // given
        SignUpRequest request = new SignUpRequest();
        request.setEmail("valid-" + UUID.randomUUID() + "@example.com");
        request.setPassword("abc12");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Returns 400 with VALIDATION_ERROR when the email format is invalid")
    void signUp_invalidEmailFormat_returns400() {
        // given
        SignUpRequest request = new SignUpRequest();
        request.setEmail("not-an-email");
        request.setPassword("validpassword123");

        // when
        var response = given().contentType(ContentType.JSON).body(request)
                .when().post("/users");

        // then
        response.then().statusCode(400)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Returns 401 with INVALID_TOKEN when fetching a user without authentication")
    void getUser_unauthenticated_returns401() {
        // when
        var response = given().when().get("/users/anyone@example.com");

        // then
        response.then().statusCode(401)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("INVALID_TOKEN"));
    }
}
