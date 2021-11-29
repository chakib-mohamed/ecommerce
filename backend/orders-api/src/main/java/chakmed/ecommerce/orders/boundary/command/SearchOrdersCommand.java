package chakmed.ecommerce.orders.boundary.command;

import lombok.Data;

@Data
public class SearchOrdersCommand {

    String userID;
    Integer offset;
    Integer limit;
    String sortBy;
}
