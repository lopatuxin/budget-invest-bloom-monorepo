package pyc.lopatuxin.budget.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import pyc.lopatuxin.budget.dto.response.IncomeResponseDto;
import pyc.lopatuxin.budget.entity.Income;
import pyc.lopatuxin.budget.entity.enums.IncomeSource;

@Mapper(componentModel = "spring")
public interface IncomeMapper {

    @Mapping(source = "source", target = "sourceName", qualifiedByName = "toDisplayName")
    IncomeResponseDto toDto(Income income);

    @Named("toDisplayName")
    default String toDisplayName(IncomeSource source) {
        return source != null ? source.getDisplayName() : null;
    }
}
