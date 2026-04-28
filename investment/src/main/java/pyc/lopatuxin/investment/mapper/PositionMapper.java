package pyc.lopatuxin.investment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.entity.Position;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PositionMapper {

    @Mapping(source = "security.ticker", target = "ticker")
    @Mapping(source = "security.name", target = "securityName")
    @Mapping(source = "security.type", target = "securityType")
    @Mapping(source = "security.sector", target = "sector")
    PositionResponseDto toDto(Position position);

    List<PositionResponseDto> toDtoList(List<Position> positions);
}
