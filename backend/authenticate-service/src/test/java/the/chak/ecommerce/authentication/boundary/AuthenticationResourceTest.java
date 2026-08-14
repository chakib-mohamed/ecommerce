package the.chak.ecommerce.authentication.boundary;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import jakarta.inject.Inject;
import the.chak.ecommerce.authentication.repository.UserRepository;
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

    @Inject
    UserRepository userRepository;

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

    @Test
    @DisplayName("Returns 404 when the requested email has no account")
    void getUser_unknownEmail_returns404() {
        // given - a valid session, so the request gets past authentication
        String cookie = signedInCookie();

        // when
        var response = given().cookie("Authorization", cookie)
                .when().get("/users/{email}", "no-such-user-" + UUID.randomUUID() + "@example.com");

        // then
        response.then().statusCode(404);
    }

    @Test
    @DisplayName("Returns the signed-in user's details for the current-user request")
    void getAuthenticatedUser_validCookie_returnsOwnDetails() {
        // given
        String email = "current-" + UUID.randomUUID() + "@example.com";
        String cookie = signedInCookie(email);

        // when
        var response = given().cookie("Authorization", cookie).when().get("/users/current");

        // then
        response.then().statusCode(200).body("email", is(email));
    }

    @Test
    @DisplayName("Returns 401 with INVALID_TOKEN for the current-user request without authentication")
    void getAuthenticatedUser_noCookie_returns401() {
        // when
        var response = given().when().get("/users/current");

        // then
        response.then().statusCode(401)
                .body("type", is("FUNCTIONAL"))
                .body("error_code", is("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("Accepts an authentication cookie whose token carries the scheme prefix")
    void getUser_tokenWithSchemePrefix_isAccepted() {
        // given - the cookie is minted as a bare token; a client that sends it back with the
        // scheme prefix must resolve to the same user
        String email = "prefixed-" + UUID.randomUUID() + "@example.com";
        String prefixed = "Bearer " + signedInCookie(email);

        // when
        var response = given().cookie("Authorization", prefixed)
                .when().get("/users/{email}", email);

        // then
        response.then().statusCode(200).body("email", is(email));
    }

    @Test
    @DisplayName("Returns 404 for the current-user request when the account no longer exists")
    void getAuthenticatedUser_accountRemoved_returns404() {
        // given - a valid session whose account is then removed, so the token outlives the user
        String email = "vanished-" + UUID.randomUUID() + "@example.com";
        String cookie = signedInCookie(email);
        userRepository.delete("email", email);

        // when
        var response = given().cookie("Authorization", cookie).when().get("/users/current");

        // then
        response.then().statusCode(404);
    }

    private String signedInCookie() {
        return signedInCookie("cookie-" + UUID.randomUUID() + "@example.com");
    }

    private String signedInCookie(String email) {
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword("pass1234");
        given().contentType(ContentType.JSON).body(signUp).when().post("/users");

        AuthenticateRequest auth = new AuthenticateRequest();
        auth.setEmail(email);
        auth.setPassword("pass1234");
        return given().contentType(ContentType.JSON).body(auth)
                .when().post("/users/authenticate").then().extract().cookie("Authorization");
    }
}
