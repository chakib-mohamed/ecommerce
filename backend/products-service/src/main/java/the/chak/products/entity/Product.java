package chakmed.ecommerce.products.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Product extends PanacheEntity {

    private UUID uuid;
    private String description;
    private String image;
    private Double price;
    private String title;

    @ManyToMany
    @JoinTable(
        name = "product_promotion",
        joinColumns = @JoinColumn(name = "id"),
        inverseJoinColumns = @JoinColumn(name = "id"))
    List<Promotion> promotions;

    @ManyToMany
    @JoinTable(
        name = "product_category",
        joinColumns = @JoinColumn(name = "id"),
        inverseJoinColumns = @JoinColumn(name = "id"))
    List<Category> categories;

    @PrePersist
    public void prePersist() {
        uuid = UUID.randomUUID();
    }

    public List<Promotion> getPromotions() {
        if(promotions == null) {
            promotions = new ArrayList<> ();
        }
        return promotions;
    }

    public List<Category> getCategories() {
        if(categories == null) {
            categories = new ArrayList<> ();
        }
        return categories;
    }
}
