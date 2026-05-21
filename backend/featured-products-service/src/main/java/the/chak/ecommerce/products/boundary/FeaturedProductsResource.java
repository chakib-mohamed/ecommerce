package the.chak.ecommerce.products.boundary;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import the.chak.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.List;

@Path("/products/featured")
public class FeaturedProductsResource {

    @Inject
    ProductMapper productMapper;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProductMongoEntityDto> getFeaturedProducts(
            @QueryParam("pageIndex") @DefaultValue("0") int pageIndex,
            @QueryParam("pageSize") @DefaultValue("10") int pageSize) {

        return ProductMongoEntity.<ProductMongoEntity>findAll()
                .page(pageIndex, pageSize).list()
                .stream().map(productMapper::toDto).toList();
    }
}
