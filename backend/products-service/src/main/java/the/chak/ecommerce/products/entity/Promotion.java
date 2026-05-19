package the.chak.ecommerce.products.entity;

import java.time.LocalDate;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
public class Promotion extends PanacheEntity {

    @Column
    private String label;

    @Column(name = "percentage_off")
    private Double percentageOff;

    @Column(name = "active_from")
    private LocalDate activeFrom;

    @Column(name = "active_to")
    private LocalDate activeTo;

}
