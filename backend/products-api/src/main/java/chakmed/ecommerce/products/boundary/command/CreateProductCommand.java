package chakmed.ecommerce.products.boundary.command;

import lombok.Data;

@Data
public class CreateProductCommand {

    private Long id;
    private String title;
    private String description;
    private Long category;
    private String image;
    private Double price;
}
