package the.chak.ecommerce.products.repository;

/**
 * Neutral, repository-owned filter for a single field: the comparison operator and the value to
 * match. Keeps the persistence layer free of boundary DTOs - the control layer maps the inbound
 * request criteria onto this type before handing it to a repository. The operator carries its own
 * SQL/JPQL fragment so query assembly stays inside the repository.
 */
public record QueryCriteria(Operator operator, String value) {

    public enum Operator {
        EQUALS(" = "),
        LIKE(" LIKE "),
        GREATER_THAN(" > "),
        LESS_THAN(" < ");

        private final String sql;

        Operator(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }
}
