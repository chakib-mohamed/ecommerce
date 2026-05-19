package the.chak.ecommerce.authentication.boundary.mapper;

import org.bson.types.ObjectId;
import org.mapstruct.Mapper;
import the.chak.ecommerce.authentication.boundary.dto.SignUpRequest;
import the.chak.ecommerce.authentication.boundary.dto.UserResponse;
import the.chak.ecommerce.authentication.entity.User;

@Mapper(componentModel = "cdi")
public interface UserMapper {
    User toUser(SignUpRequest signUpRequest);

    UserResponse toUserResponse(User user);

    default String objectIDtoUserID(ObjectId objectId) {
        return objectId != null ? objectId.toString() : null;
    }
}
