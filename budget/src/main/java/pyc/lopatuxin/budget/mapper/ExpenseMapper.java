package pyc.lopatuxin.budget.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.entity.Expense;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    ExpenseResponseDto toDto(Expense expense);

    List<ExpenseResponseDto> toDtoList(List<Expense> expenses);
}
