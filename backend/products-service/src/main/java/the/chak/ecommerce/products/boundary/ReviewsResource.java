package the.chak.ecommerce.products.boundary;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import java.util.List;
import java.util.stream.Collectors;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import the.chak.ecommerce.products.boundary.dto.ReviewDto;
import the.chak.ecommerce.products.boundary.dto.ReviewRequest;
import the.chak.ecommerce.products.boundary.mapper.ReviewMapper;
import the.chak.ecommerce.products.control.ReviewService;

@Path("/reviews")
public class ReviewsResource {

    @Inject
    ReviewService reviewService;

    @Inject
    ReviewMapper reviewMapper;

    @Context
    SecurityContext securityContext;

    @GET
    @Produces(APPLICATION_JSON)
    public List<ReviewDto> getReviews(@QueryParam("product_id") String productId,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("10") int pageSize) {
        return reviewService.listReviews(productId, pageIndex, pageSize).stream()
                .map(reviewMapper::toDto).collect(Collectors.toList());
    }

    @POST
    @Authenticated
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    public ReviewDto submitReview(@Valid ReviewRequest reviewRequest) {
        String reviewer = securityContext.getUserPrincipal().getName();
        return reviewMapper.toDto(reviewService.submitReview(reviewer, reviewRequest));
    }

    @DELETE
    @Path("/{reviewID}")
    @Authenticated
    public Response deleteReview(@PathParam("reviewID") String reviewID) {
        String reviewer = securityContext.getUserPrincipal().getName();
        reviewService.deleteReview(reviewer, reviewID);
        return Response.ok().build();
    }
}
