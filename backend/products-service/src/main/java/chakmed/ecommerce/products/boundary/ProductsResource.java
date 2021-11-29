package chakmed.ecommerce.products.boundary;

import chakmed.ecommerce.products.boundary.command.CreateProductCommand;
import chakmed.ecommerce.products.boundary.mapper.ProductMapper;
import chakmed.ecommerce.products.control.ProductService;
import chakmed.ecommerce.products.entity.Product;
import chakmed.ecommerce.products.entity.ProductDTO;
import chakmed.ecommerce.products.entity.ProductMongoEntity;
import chakmed.ecommerce.products.entity.ProductMongoEntityDTO;

import javax.inject.Inject;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ProductsResource implements ProductsApi {

    @Inject
    ProductService productService;

    @Inject
    ProductMapper productMapper;

    public List<ProductMongoEntityDTO> getProductsSnapshot() {

        return ProductMongoEntity.listAll().stream().map(ProductMongoEntity.class::cast)
                .map(p -> productMapper.toProductMongoEntityDto(p))
                .collect(Collectors.toList());
    }

    public List<ProductDTO> getProducts() {

        return Product.listAll().stream().map(Product.class::cast)
                .map(p -> productMapper.mapProductToProductDto(p))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProductDTO> searchProducts(Map<String, Object> searchProductsRequest) {
        return productService.findByCriteria(searchProductsRequest).stream().map(product -> productMapper.mapProductToProductDto(product)).collect(Collectors.toList());
    }

    public Response getProduct(String productID) {

        return Product.findByIdOptional(Long.valueOf(productID)).map(Product.class::cast).map(productMapper::mapProductToProductDto).map(p -> Response.ok(p).build()).orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    public Response createProduct(CreateProductCommand createProductCommand) {

        var product = productMapper.mapCreateProductCommandToProduct(createProductCommand);

        productService.saveProduct(product);

        return Response.ok(product).status(201).build();
    }

    public Response updateProduct(CreateProductCommand createProductCommand) {

        var product = productMapper.mapCreateProductCommandToProduct(createProductCommand);

        productService.updateProduct(product);

        return Response.ok(product).status(200).build();
    }

    public Response deleteProduct(String productID) {

        productService.deleteProduct(Long.valueOf(productID));

        return Response.ok().status(200).build();
    }


}