package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.LocalDate;

@Getter
@Setter
@Entity
public class Promotion extends PanacheEntity {

    @Column
    private String label;

    @Column
    private Double percentageOff;

    @Column
    private LocalDate activeFrom;

    @Column
    private LocalDate activeTo;

}
