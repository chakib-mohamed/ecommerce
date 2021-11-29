package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.command.CreateProductCommand;
import chakmed.ecommerce.products.entity.ProductDTO;
import chakmed.ecommerce.products.entity.ProductMongoEntityDTO;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;

@RegisterRestClient
@Path("/products")
public interface ProductsApi {

    @GET
    @Path("/snapshot")
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductMongoEntityDTO> getProductsSnapshot();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductDTO> getProducts() ;

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    List<ProductDTO> searchProducts(Map<String, Object> searchProductsRequest) ;

    @GET
    @Path("/{productID}")
    @Produces(MediaType.APPLICATION_JSON)
    Response getProduct(@PathParam("productID") String productID);

    @POST
    Response createProduct(CreateProductCommand createProductCommand) ;

    @PUT
    Response updateProduct(CreateProductCommand createProductCommand) ;

    @DELETE
    @Path("/{productID}")
    Response deleteProduct(@PathParam("productID") String productID) ;

}