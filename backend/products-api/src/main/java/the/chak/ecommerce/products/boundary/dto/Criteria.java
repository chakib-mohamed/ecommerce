package the.chak.ecommerce.products.boundary.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Criteria {
    private Operator operator;
    private String value;

    @Getter
    @AllArgsConstructor
    public enum Operator {
        EQUALS(" = "),
        LIKE(" LIKE "),
        GREATER_THAN(" > "),
        LESS_THAN(" < ");

        private final String value;
    }
}
