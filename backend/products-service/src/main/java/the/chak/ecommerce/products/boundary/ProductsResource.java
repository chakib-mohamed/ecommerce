package the.chak.ecommerce.products.boundary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Path;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.control.ProductService;
import the.chak.ecommerce.products.control.StorageService;

@Path("/products")
public class ProductsResource implements ProductsApi {

    @Inject
    ProductService productService;

    @Inject
    ProductMapper productMapper;

    @Inject
    StorageService storageService;

    @Override
    public List<ProductDto> getProducts(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("10") int pageSize) {
        return productService.getProducts(pageIndex, pageSize)
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
        return productService.getProductWithAssociations(UUID.fromString(uuid))
                .map(p -> Response.ok(productMapper.toDto(p)).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Override
    public Response createProduct(ProductDto saveProductDto) {
        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();
        var saved = productService.saveProduct(product, imageBytes,
                saveProductDto.getCategoryId(), saveProductDto.getSubcategoryId());
        return Response.status(Response.Status.CREATED).entity(productMapper.toDto(saved)).build();
    }

    @Override
    public Response updateProduct(ProductDto saveProductDto) {
        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();
        var updated = productService.updateProduct(product, imageBytes,
                saveProductDto.getCategoryId(), saveProductDto.getSubcategoryId());
        return Response.ok(productMapper.toDto(updated)).build();
    }

    @Override
    public Response deleteProduct(String productUUID) {
        productService.deleteProduct(UUID.fromString(productUUID));
        return Response.ok().build();
    }

    @Override
    public Response getImage(String imageKey) {
        byte[] imageData = storageService.downloadImage(imageKey);
        return Response.ok(imageData).type(storageService.detectContentType(imageData)).build();
    }
}
