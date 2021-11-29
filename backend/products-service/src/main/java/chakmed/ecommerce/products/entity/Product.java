package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.Set;

@Getter
@Setter
@ToString
@Entity
public class Product extends PanacheEntity {

     String description;
     String image;
     Double price;
     String title;

    @OneToMany(mappedBy = "product")
    Set<Promotion> promotions;

    @ManyToOne
     Category category;
}
