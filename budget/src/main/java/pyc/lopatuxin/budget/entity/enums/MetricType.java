package pyc.lopatuxin.budget.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetricType {

    INCOME("Доходы"),
    EXPENSES("Расходы"),
    BALANCE("Баланс"),
    CAPITAL("Капитал"),
    INFLATION("Инфляция");

    private final String displayName;
}
