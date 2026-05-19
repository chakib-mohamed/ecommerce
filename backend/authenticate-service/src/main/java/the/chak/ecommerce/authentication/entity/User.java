package the.chak.ecommerce.authentication.entity;

import java.util.List;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
@MongoEntity(collection = "user")
public class User extends PanacheMongoEntity {
    public String email;
    public String password;
    public List<String> roles;
}
