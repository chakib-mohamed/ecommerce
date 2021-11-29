package chakmed.ecommerce.orders.entity;

import lombok.Data;

@Data
    public class ProductVO {
        private String productID;
        private String title;
        private Integer qty;
        private Double price;
        private Double percentageOff;
    }