package chakmed.ecommerce.authentication.entity;

import io.quarkus.mongodb.panache.MongoEntity;
import io.quarkus.mongodb.panache.PanacheMongoEntity;
import lombok.Data;
import org.bson.types.ObjectId;

import java.util.List;

@Data
@MongoEntity(collection = "user")
public class User extends PanacheMongoEntity {
    private ObjectId id;
    private String email;
    private String password;
    private List<String> roles;
}
