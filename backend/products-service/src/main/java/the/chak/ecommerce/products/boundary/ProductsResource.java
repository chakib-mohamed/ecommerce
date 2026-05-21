package the.chak.ecommerce.products.boundary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Path;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.control.MinioService;
import the.chak.ecommerce.products.control.ProductService;
import the.chak.ecommerce.products.control.events.ProductUpdatedEvent;
import the.chak.ecommerce.products.entity.Product;

@Path("/products")
public class ProductsResource implements ProductsApi {

    @Inject
    ProductService productService;

    @Inject
    ProductMapper productMapper;

    @Inject
    MinioService minioService;

    @Inject
    Event<ProductUpdatedEvent> productUpdatedEvent;

    @Override
    public List<ProductDto> getProducts(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("10") int pageSize) {
        return Product.<Product>find(
                        "from Product p left join fetch p.promotions left join fetch p.categories")
                .page(pageIndex, pageSize).list()
                .stream().map(productMapper::toDto).toList();
    }

    @Override
    public List<ProductDto> searchProducts(Map<String, Criteria> searchProductsRequest,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("10") int pageSize) {
        return productService.findByCriteria(searchProductsRequest, pageIndex, pageSize).stream()
                .map(productMapper::toDto).collect(Collectors.toList());
    }

    @Override
    public Response getProduct(String uuid) {
        return Product.<Product>find(
                        "from Product p left join fetch p.promotions left join fetch p.categories where p.uuid = ?1",
                        UUID.fromString(uuid))
                .firstResultOptional()
                .map(productMapper::toDto).map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Override
    @Transactional
    public Response createProduct(ProductDto saveProductDto) {
        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();
        ProductDto createdDto = productMapper.toDto(productService.saveProduct(product, imageBytes));
        productUpdatedEvent.fire(new ProductUpdatedEvent(createdDto));
        return Response.status(Response.Status.CREATED).entity(createdDto).build();
    }

    @Override
    @Transactional
    public Response updateProduct(ProductDto saveProductDto) {
        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();
        ProductDto updatedDto = productMapper.toDto(productService.updateProduct(product, imageBytes));
        productUpdatedEvent.fire(new ProductUpdatedEvent(updatedDto));
        return Response.ok(updatedDto).build();
    }

    @Override
    public Response deleteProduct(String productUUID) {
        productService.deleteProduct(UUID.fromString(productUUID));
        return Response.ok().build();
    }

    @Override
    public Response getImage(String imageKey) {
        byte[] imageData = minioService.downloadImage(imageKey);
        return Response.ok(imageData).build();
    }
}
