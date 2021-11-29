package chakmed.ecommerce.authentication.boundary;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import javax.inject.Inject;
import javax.ws.rs.CookieParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;

import chakmed.ecommerce.authentication.control.JwtConfig;
import chakmed.ecommerce.authentication.control.TokenUtils;
import chakmed.ecommerce.authentication.control.UserService;
import chakmed.ecommerce.authentication.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

@Path("/users")
public class UserResource {

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
    @Produces(MediaType.APPLICATION_JSON)
    public Response authenticate(AuthenticateRequest authenticateRequest) {
        var user = userService.authenticateUser(authenticateRequest);
        if(user.isPresent()) {
            var authResponse = this.createAccessToken(user.get());
            return Response.ok(authResponse).cookie(this.buildAuthCookie(authResponse.getAccessToken())).build();
        }

        return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    @GET
    @Path("/{email}")
    public Response getUser(@PathParam("email") String email) {
        var user = userService.findUser(email);
        
        if(user.isPresent()) {
            return Response.ok(userMapper.toUserResponse(user.get())).build();
        }

        return Response.status(Response.Status.NOT_FOUND).build();
    }

    @GET
    @Path("/current")
    public Response getAuthenticatedUser(@CookieParam(HttpHeaders.AUTHORIZATION) Cookie cookie) {
        String email = this.resolveUserLogin(cookie);
        var user = userService.findUser(email);

        if(user.isPresent()) {
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

    private AuthenticateResponse createAccessToken(User user) {
        String token = Jwts.builder()
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(Date.from(LocalDateTime.now().plusMinutes(jwtConfig.getExpiration()).atZone(ZoneId.systemDefault()).toInstant()))
                .signWith(SignatureAlgorithm.HS512, jwtConfig.getSecret().getBytes())
                .compact();

        return new AuthenticateResponse(token);
    }

    private NewCookie buildAuthCookie(String token) {
        var cookie = new Cookie(HttpHeaders.AUTHORIZATION, token, "/", null);

        return new NewCookie(cookie);

    }

    private String resolveUserLogin(Cookie cookie) {
        String token = cookie.getValue();
        if(token.startsWith(jwtConfig.getPrefix())){
            token = token.replace(jwtConfig.getPrefix(), "");
        }

        return tokenUtils.getUsername(token);
    }

}