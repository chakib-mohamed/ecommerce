package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.units.qual.C;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Category extends PanacheEntity {

    @Column(unique = true)
    private String label;

    private Long parentId;

    @OneToMany(mappedBy = "parentId")
    private List<Category> subCategories;

    public List<Category> getSubCategories() {
        if(subCategories == null){
            subCategories = new ArrayList<>();
        }

        return subCategories;
    }
}
