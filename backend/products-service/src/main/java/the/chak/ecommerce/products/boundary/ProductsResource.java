package the.chak.ecommerce.products.boundary;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.Path;
import the.chak.ecommerce.products.boundary.dto.Criteria;
import the.chak.ecommerce.products.boundary.dto.ProductDto;
import the.chak.ecommerce.products.boundary.mapper.ProductMapper;
import the.chak.ecommerce.products.control.MinioService;
import the.chak.ecommerce.products.control.ProductService;
import the.chak.ecommerce.products.entity.Product;

@Path("/products")
public class ProductsResource implements ProductsApi {

    @Inject
    ProductService productService;

    @Inject
    ProductMapper productMapper;

    @Inject
    MinioService minioService;

    @ConfigProperty(name = "products.default.page.index", defaultValue = "0")
    int defaultPageIndex;

    @ConfigProperty(name = "products.default.page.size", defaultValue = "10")
    int defaultPageSize;


    public List<ProductDto> getProducts(int pageIndex, int pageSize) {
        int effectivePageIndex = (pageIndex == 0) ? defaultPageIndex : pageIndex;
        int effectivePageSize = (pageSize == 0) ? defaultPageSize : pageSize;

        return Product.<Product>findAll().page(effectivePageIndex, effectivePageSize).list()
                .stream().map(p -> productMapper.toDto(p)).toList();
    }

    @Override
    public List<ProductDto> searchProducts(Map<String, Criteria> searchProductsRequest,
            int pageIndex, int pageSize) {
        int effectivePageIndex = (pageIndex == 0) ? defaultPageIndex : pageIndex;
        int effectivePageSize = (pageSize == 0) ? defaultPageSize : pageSize;
        return productService.findByCriteria(searchProductsRequest, effectivePageIndex, effectivePageSize).stream()
                .map(product -> productMapper.toDto(product)).collect(Collectors.toList());
    }

    @Override
    public Response getProduct(String uuid) {

        return Product.<Product>find("uuid", UUID.fromString(uuid)).firstResultOptional()
                .map(productMapper::toDto).map(p -> Response.ok(p).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @Override
    public Response createProduct(ProductDto saveProductDto) {

        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();

        var created = productMapper.toDto(productService.saveProduct(product, imageBytes));

        return Response.ok(created).status(201).build();
    }

    @Override
    public Response updateProduct(ProductDto saveProductDto) {

        var product = productMapper.toEntity(saveProductDto);
        byte[] imageBytes = saveProductDto.getImage();

        productService.updateProduct(product, imageBytes);

        return Response.ok(productMapper.toDto(product)).status(200).build();
    }

    @Override
    public Response deleteProduct(String productUUID) {

        productService.deleteProduct(UUID.fromString(productUUID));

        return Response.ok().status(200).build();
    }

    @Override
    public Response getImage(String imageKey) {
        byte[] imageData = minioService.downloadImage(imageKey);
        return Response.ok(imageData).build();
    }

}
