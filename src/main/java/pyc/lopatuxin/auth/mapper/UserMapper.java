package pyc.lopatuxin.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.dto.response.UserDto;
import pyc.lopatuxin.auth.entity.User;

import java.util.List;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "passwordHash", ignore = true)
    User toUser(RegisterRequest request);

    @Mapping(target = "userId", expression = "java(user.getId().toString())")
    @Mapping(target = "roles", expression = "java(mapRoles(user))")
    UserDto toUserDto(User user);

    default List<String> mapRoles(User user) {
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return List.of();
        }
        return user.getRoles().stream()
                .map(role -> role.getRoleName().name())
                .toList();
    }
}
