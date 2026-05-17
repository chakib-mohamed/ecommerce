package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.dto.ProductDto;
import chakmed.ecommerce.products.boundary.dto.ProductMongoEntityDto;
import chakmed.ecommerce.products.boundary.mapper.ProductMapper;
import chakmed.ecommerce.products.control.ProductService;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductsResource implements ProductsApi {

    @Inject
    ProductService productService;

    @Inject
    ProductMapper productMapper;

    public List<ProductMongoEntityDto> getProductsList() {

        return ProductMongoEntity.listAll().stream().map(ProductMongoEntity.class::cast)
                .map(p -> productMapper.toDto(p))
                .collect(Collectors.toList());
    }

    public List<ProductDto> getProducts() {

        return Product.listAll().stream().map(Product.class::cast)
                .map(p -> productMapper.toDto(p))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductDto> searchProducts(Map<String, Object> searchProductsRequest) {
        return productService.findByCriteria(searchProductsRequest).stream().map(product -> productMapper.toDto(product)).collect(Collectors.toList());
    }

    @Override
    public Response getProduct(String productID) {

        return Product.findByIdOptional(Long.valueOf(productID)).map(Product.class::cast).map(productMapper::toDto).map(p -> Response.ok(p).build()).orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    public Response createProduct(ProductDto saveProductDto) {

        var product = productMapper.toEntity(saveProductDto);

        productService.saveProduct(product);

        return Response.ok(product).status(201).build();
    }

    public Response updateProduct(ProductDto saveProductDto) {

        var product = productMapper.toEntity(saveProductDto);

        productService.updateProduct(product);

        return Response.ok(product).status(200).build();
    }

    public Response deleteProduct(String productID) {

        productService.deleteProduct(Long.valueOf(productID));

        return Response.ok().status(200).build();
    }


}