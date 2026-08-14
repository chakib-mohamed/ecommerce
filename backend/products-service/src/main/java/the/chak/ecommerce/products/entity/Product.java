package the.chak.ecommerce.products.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    private UUID uuid;
    private String description;
    private String imageKey;
    private BigDecimal price;
    private String title;
    private Integer stock;

    /** Average star rating (1-5) across the product's reviews, maintained by ReviewService. */
    private Double rating;

    /** Number of reviews for this product, maintained by ReviewService. */
    private Integer reviewCount;

    @Getter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_promotion", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "promotion_id"))
    List<Promotion> promotions;

    @Getter(AccessLevel.NONE)
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "product_category", joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    List<Category> categories;

    @PrePersist
    public void prePersist() {
        uuid = UUID.randomUUID();
    }

    public List<Promotion> getPromotions() {
        if (promotions == null) {
            promotions = new ArrayList<>();
        }
        return promotions;
    }

    public List<Category> getCategories() {
        if (categories == null) {
            categories = new ArrayList<>();
        }
        return categories;
    }
}
