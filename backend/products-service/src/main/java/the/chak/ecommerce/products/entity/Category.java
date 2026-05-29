package the.chak.ecommerce.products.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true)
    private String label;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "parent")
    private List<Category> subCategories;

    public List<Category> getSubCategories() {
        if (subCategories == null) {
            subCategories = new ArrayList<>();
        }

        return subCategories;
    }
}
