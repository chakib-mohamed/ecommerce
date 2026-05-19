package the.chak.ecommerce.products.boundary;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import the.chak.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.entity.ProductMongoEntity;

import java.util.List;
import java.util.stream.Collectors;

@Path("/products/featured")
public class FeaturedProductsResource {

    @Inject
    ProductMapper productMapper;

    @ConfigProperty(name = "products.default.page.index", defaultValue = "0")
    int defaultPageIndex;

    @ConfigProperty(name = "products.default.page.size", defaultValue = "10")
    int defaultPageSize;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProductMongoEntityDto> getFeaturedProducts(
            @jakarta.ws.rs.QueryParam("pageIndex") @jakarta.ws.rs.DefaultValue("0") int pageIndex,
            @jakarta.ws.rs.QueryParam("pageSize") @jakarta.ws.rs.DefaultValue("10") int pageSize) {

        List<ProductMongoEntityDto> dtos = ProductMongoEntity.findAll().page(pageIndex, pageSize)
                .stream().map(ProductMongoEntity.class::cast).map(p -> {
                    ProductMongoEntityDto dto = productMapper.toDto(p);
                    return dto;
                }).collect(Collectors.toList());

        return dtos;
    }

}
