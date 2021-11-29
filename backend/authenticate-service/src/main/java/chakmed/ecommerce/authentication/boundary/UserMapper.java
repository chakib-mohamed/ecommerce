package chakmed.ecommerce.authentication.boundary;

import chakmed.ecommerce.authentication.entity.User;
import org.bson.types.ObjectId;
import org.mapstruct.Mapper;

@Mapper(componentModel = "cdi")
public interface UserMapper {
    User toUser(SignUpRequest signUpRequest);
    UserResponse toUserResponse(User user);
    default String objectIDtoUserID(ObjectId objectId) {
        return objectId != null ? objectId.toString() : null;
    }
}
