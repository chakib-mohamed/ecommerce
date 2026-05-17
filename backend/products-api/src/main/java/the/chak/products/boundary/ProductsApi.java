package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.dto.ProductDto;
import chakmed.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

@RegisterRestClient
@Path("/products")
public interface ProductsApi {

    @GET
    @Path("/list")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductMongoEntityDto> getProductsList();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductDto> getProducts() ;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductDto> searchProducts(Map<String, Object> searchProductsRequest) ;

    @GET
    @Path("/{productID}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getProduct(@PathParam("productID") String productID);

    @POST
    Response createProduct(ProductDto saveProductDto) ;

    @PUT
    Response updateProduct(ProductDto saveProductDto) ;

    @DELETE
    @Path("/{productID}")
    Response deleteProduct(@PathParam("productID") String productID) ;

}
