package the.chak.ecommerce.orders.boundary.dto;

import lombok.Data;

@Data
public class SearchOrdersCommand {

    String userID;
    Integer offset;
    Integer limit;
    String sortBy;
}
