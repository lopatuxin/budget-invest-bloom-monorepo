package pyc.lopatuxin.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.auth.dto.request.RegisterRequest;
import pyc.lopatuxin.auth.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "passwordHash", ignore = true)
    User toUser(RegisterRequest request);
}
