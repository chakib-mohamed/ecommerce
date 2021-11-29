package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;

@Getter
@Setter
@Entity
public class Category extends PanacheEntity {

    @Column(unique = true)
    private String value;

    @Column(unique = true)
    private String label;

}
