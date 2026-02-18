package pyc.lopatuxin.budget.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum IncomeSource {

    SALARY("Зарплата"),
    FREELANCE("Фриланс"),
    INVESTMENTS("Инвестиции"),
    GIFTS("Подарки"),
    OTHER("Прочее");

    private final String displayName;
}
