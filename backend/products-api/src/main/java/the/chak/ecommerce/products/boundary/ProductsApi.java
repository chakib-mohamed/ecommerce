package the.chak.ecommerce.products.boundary;

import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.ProductDto;

@RegisterRestClient
@Path("/products")
public interface ProductsApi {

        @GET
        @Produces(MediaType.APPLICATION_JSON)
        List<ProductDto> getProducts(@QueryParam("page") int pageIndex,
                        @QueryParam("size") int pageSize);

        @POST
        @Path("/search")
        @Produces(MediaType.APPLICATION_JSON)
        List<ProductDto> searchProducts(Map<String, Criteria> searchProductsRequest,
                        @QueryParam("page") int pageIndex, @QueryParam("size") int pageSize);

        @GET
        @Path("/{productID}")
        @Produces(MediaType.APPLICATION_JSON)
        Response getProduct(@PathParam("productID") String productID);

        @POST
        Response createProduct(@Valid ProductDto saveProductDto);

        @PUT
        Response updateProduct(@Valid ProductDto saveProductDto);

        @DELETE
        @Path("/{productID}")
        Response deleteProduct(@PathParam("productID") String productID);

        @GET
        @Path("/images/{imageKey}")
        @Produces("image/jpeg")
        Response getImage(@PathParam("imageKey") String imageKey);

}
