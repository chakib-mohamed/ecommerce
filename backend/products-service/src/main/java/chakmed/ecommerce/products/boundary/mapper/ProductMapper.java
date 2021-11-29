package chakmed.ecommerce.products.boundary.mapper;

import chakmed.ecommerce.products.boundary.command.CreateProductCommand;
import chakmed.ecommerce.products.entity.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface ProductMapper extends BaseMapper {

    default Product mapCreateProductCommandToProduct(CreateProductCommand createProductCommand) {

        Product product = new Product();
        product.id = createProductCommand.getId();
        product.setCategory(Category.findById(createProductCommand.getCategory()));
        product.setDescription(createProductCommand.getDescription());
        product.setImage(createProductCommand.getImage());
        product.setTitle(createProductCommand.getTitle());
        product.setPrice(createProductCommand.getPrice());

        return product;
    }

    ProductDTO mapProductToProductDto(Product product);

    ProductMongoEntityDTO toProductMongoEntityDto(ProductMongoEntity productMongoEntity);

}