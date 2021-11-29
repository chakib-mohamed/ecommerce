package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import java.time.LocalDate;

@Getter
@Setter
@Entity
public class Promotion extends PanacheEntity {

    public String label;
    public Double percentageOff;
    public LocalDate activeFrom;
    public LocalDate activeTo;

    @ManyToOne
    public Product product;
}
