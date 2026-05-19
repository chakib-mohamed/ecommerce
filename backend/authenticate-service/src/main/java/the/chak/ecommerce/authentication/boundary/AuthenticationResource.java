package the.chak.ecommerce.authentication.boundary;

import java.util.Date;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.Jwts;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateRequest;
import the.chak.ecommerce.authentication.boundary.dto.AuthenticateResponse;
import the.chak.ecommerce.authentication.boundary.dto.SignUpRequest;
import the.chak.ecommerce.authentication.boundary.mapper.UserMapper;
import the.chak.ecommerce.authentication.control.JwtConfig;
import the.chak.ecommerce.authentication.control.RsaKeyProvider;
import the.chak.ecommerce.authentication.control.TokenUtils;
import the.chak.ecommerce.authentication.control.UserService;

@Path("/users")
public class AuthenticationResource {

    @Inject
    JwtConfig jwtConfig;

    @Inject
    RsaKeyProvider rsaKeyProvider;

    @Inject
    TokenUtils tokenUtils;

    @Inject
    UserService userService;


    @Inject
    UserMapper userMapper;

    @POST
    @Path("/authenticate")
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(AuthenticateRequest authenticateRequest) {
        var user = userService.authenticateUser(authenticateRequest);
        if (user.isPresent()) {
            String token = this.createAccessToken(user.get().getEmail());
            var authResponse = new AuthenticateResponse(token);
            return Response.ok(authResponse)
                    .cookie(this.createAuthCookie(authResponse.getAccessToken())).build();
        }

        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @GET
    @Path("/{email}")
    public Response getUser(@PathParam("email") String email) {
        var user = userService.findUser(email);

        if (user.isPresent()) {
            return Response.ok(userMapper.toUserResponse(user.get())).build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/current")
    public Response getAuthenticatedUser(@CookieParam(HttpHeaders.AUTHORIZATION) Cookie cookie) {
        String email = this.resolveUserLogin(cookie);
        var user = userService.findUser(email);

        if (user.isPresent()) {
            return Response.ok(userMapper.toUserResponse(user.get())).build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    public Response signUp(SignUpRequest signUpRequest) {

        var user = this.userMapper.toUser(signUpRequest);
        userService.addUser(user);

        return Response.ok(this.userMapper.toUserResponse(user)).status(201).build();
    }

    private NewCookie createAuthCookie(String token) {
        return new NewCookie.Builder("Authorization").value(token).path("/").httpOnly(true).build();
    }

    private String createAccessToken(String subject) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + jwtConfig.getExpiration() * 60 * 1000))
                .signWith(rsaKeyProvider.getPrivateKey(), SignatureAlgorithm.RS256)
                .compact();
    }

    private String resolveUserLogin(Cookie cookie) {
        String token = cookie.getValue();
        if (token.startsWith(jwtConfig.getPrefix())) {
            token = token.replace(jwtConfig.getPrefix(), "");
        }

        return tokenUtils.getUsername(token);
    }

}
