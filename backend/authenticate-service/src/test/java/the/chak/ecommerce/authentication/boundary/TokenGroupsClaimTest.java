package the.chak.ecommerce.authentication.boundary;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.StringReader;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import the.chak.ecommerce.authentication.MongoDbTestResource;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.boundary.dto.SignUpRequest;
import the.chak.ecommerce.authentication.repository.UserRepository;

/**
 * The access token has to carry the user's roles, or no service can enforce one.
 *
 * <p>Roles are stored on the user and checked at login, then dropped: the token names a subject and
 * nothing else. That is why every resource in the platform is {@code @Authenticated} and none is
 * {@code @RolesAllowed} - a role check would reject the administrator too, because the token never
 * claimed they were one.
 *
 * <p>The claim has to be called {@code groups} specifically. That is the name MicroProfile JWT maps
 * to the container's roles, so a differently-named claim parses cleanly, appears in the token, and
 * authorizes nothing - the most expensive kind of wrong, because it looks right.
 *
 * <p>Asserted against the token on the wire rather than against {@code TokenUtils}, because what
 * matters is what a verifying service receives.
 */
@QuarkusTest
@QuarkusTestResource(MongoDbTestResource.class)
@Tag("integration")
class TokenGroupsClaimTest {

    @Inject
    UserRepository userRepository;

    private static final String PASSWORD = "password123";

    /** Signs a user up and returns their email. */
    private String signUp() {
        String email = "groups-" + UUID.randomUUID() + "@example.com";
        SignUpRequest signUp = new SignUpRequest();
        signUp.setEmail(email);
        signUp.setPassword(PASSWORD);
        given().contentType(ContentType.JSON).body(signUp).when().post("/users")
                .then().statusCode(201);
        return email;
    }

    private void giveRoles(String email, List<String> roles) {
        var user = userRepository.findByEmail(email).orElseThrow();
        user.setRoles(roles);
        userRepository.update(user);
    }

    private String authenticate(String email) {
        AuthenticateRequest request = new AuthenticateRequest();
        request.setEmail(email);
        request.setPassword(PASSWORD);
        return given().contentType(ContentType.JSON).body(request)
                .when().post("/users/authenticate")
                .then().statusCode(200)
                .extract().path("access_token");
    }

    /**
     * The token's payload, decoded without verifying it.
     *
     * <p>Deliberately not parsed with the service's own key: this asserts what is in the token, not
     * whether the service agrees with itself about it.
     */
    private JsonObject payloadOf(String token) {
        String payload = token.split("\\.")[1];
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }

    @Test
    @DisplayName("Puts the user's roles in the token, under the claim name that authorizes anything")
    void authenticate_userWithRoles_tokenCarriesGroupsClaim() {
        // given
        String email = signUp();
        giveRoles(email, List.of("admin"));

        // when
        JsonObject payload = payloadOf(authenticate(email));

        // then - "groups", not "roles": only this name reaches @RolesAllowed
        assertTrue(payload.containsKey("groups"),
                "token should carry a groups claim, got: " + payload.keySet());
        assertEquals(List.of("admin"),
                payload.getJsonArray("groups").getValuesAs(jakarta.json.JsonString.class).stream()
                        .map(jakarta.json.JsonString::getString).toList());
    }

    @Test
    @DisplayName("Carries every role the user has, not just the first")
    void authenticate_userWithSeveralRoles_tokenCarriesAllOfThem() {
        String email = signUp();
        giveRoles(email, List.of("admin", "customer"));

        JsonObject payload = payloadOf(authenticate(email));

        assertEquals(List.of("admin", "customer"),
                payload.getJsonArray("groups").getValuesAs(jakarta.json.JsonString.class).stream()
                        .map(jakarta.json.JsonString::getString).toList());
    }

    @Test
    @DisplayName("Omits the claim entirely for a user who has no roles")
    void authenticate_userWithoutRoles_omitsTheClaim() {
        // given - a plain sign-up assigns nothing
        String email = signUp();

        // when
        JsonObject payload = payloadOf(authenticate(email));

        // then - absent rather than an empty array. Both authorize nothing, but an empty array
        // invites the reader to think roles were looked up and came back empty, which is not what
        // happened, and it contradicts the platform's rule that null fields are omitted.
        assertFalse(payload.containsKey("groups"),
                "a user with no roles should mint no groups claim, got: " + payload);
    }

    @Test
    @DisplayName("Still names the user, because the subject is what every service reads")
    void authenticate_tokenStillCarriesTheSubject() {
        // given - the roles are an addition, and the existing claim has to survive it: every
        // service identifies the caller by sub, and an order looks up its owner by that value
        String email = signUp();
        giveRoles(email, List.of("admin"));

        JsonObject payload = payloadOf(authenticate(email));

        assertEquals(email, payload.getString("sub"));
    }
}
