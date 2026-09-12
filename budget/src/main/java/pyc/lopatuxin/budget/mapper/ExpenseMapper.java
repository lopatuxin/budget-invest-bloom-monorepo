package pyc.lopatuxin.budget.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.budget.dto.response.ExpenseResponseDto;
import pyc.lopatuxin.budget.dto.response.OperationDto;
import pyc.lopatuxin.budget.entity.Expense;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ExpenseMapper {

    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    ExpenseResponseDto toDto(Expense expense);

    List<ExpenseResponseDto> toDtoList(List<Expense> expenses);

    /**
     * Maps a non-transfer expense to an operation-feed row, shared by the budget page's
     * operations feed and the category page's operations list (kind is always {@code EXPENSE} —
     * an expense never surfaces as income; source/sourceName are income-only and left unset).
     */
    @Mapping(target = "kind", constant = "EXPENSE")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "category.name", target = "categoryName")
    @Mapping(source = "category.emoji", target = "categoryEmoji")
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "sourceName", ignore = true)
    OperationDto toOperationDto(Expense expense);
}
