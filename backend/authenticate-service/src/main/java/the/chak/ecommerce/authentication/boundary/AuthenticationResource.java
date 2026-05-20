package the.chak.ecommerce.authentication.boundary;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
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
import the.chak.ecommerce.authentication.control.TokenUtils;
import the.chak.ecommerce.authentication.control.UserService;
import the.chak.ecommerce.authentication.control.exceptions.InvalidTokenException;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuthenticationResource {

    @Inject
    JwtConfig jwtConfig;

    @Inject
    TokenUtils tokenUtils;

    @Inject
    UserService userService;

    @Inject
    UserMapper userMapper;

    @POST
    @Path("/authenticate")
    public Response authenticate(@Valid AuthenticateRequest authenticateRequest) {
        var user = userService.authenticateUser(authenticateRequest);
        if (user.isPresent()) {
            String token = tokenUtils.generateToken(user.get().getEmail());
            var authResponse = new AuthenticateResponse(token);
            return Response.ok(authResponse)
                    .cookie(createAuthCookie(authResponse.getAccessToken()))
                    .build();
        }
        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @GET
    @Path("/{email}")
    public Response getUser(@PathParam("email") String email,
                            @CookieParam(HttpHeaders.AUTHORIZATION) Cookie cookie) {
        resolveUserLogin(cookie);
        var user = userService.findUser(email);
        if (user.isPresent()) {
            return Response.ok(userMapper.toUserResponse(user.get())).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/current")
    public Response getAuthenticatedUser(@CookieParam(HttpHeaders.AUTHORIZATION) Cookie cookie) {
        String email = resolveUserLogin(cookie);
        var user = userService.findUser(email);
        if (user.isPresent()) {
            return Response.ok(userMapper.toUserResponse(user.get())).build();
        }
        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @POST
    public Response signUp(@Valid SignUpRequest signUpRequest) {
        var user = userMapper.toUser(signUpRequest);
        userService.addUser(user);
        return Response.status(Response.Status.CREATED)
                .entity(userMapper.toUserResponse(user))
                .build();
    }

    private NewCookie createAuthCookie(String token) {
        return new NewCookie.Builder("Authorization")
                .value(token)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite(NewCookie.SameSite.STRICT)
                .build();
    }

    private String resolveUserLogin(Cookie cookie) {
        if (cookie == null) {
            throw new InvalidTokenException();
        }
        String token = cookie.getValue();
        if (token.startsWith(jwtConfig.getPrefix())) {
            token = token.substring(jwtConfig.getPrefix().length());
        }
        return tokenUtils.getUsername(token);
    }
}
