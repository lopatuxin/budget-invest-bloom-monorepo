package pyc.lopatuxin.budget.entity.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Календарные месяцы с краткими русскими названиями.
 */
@Getter
@RequiredArgsConstructor
public enum Month {

    JANUARY(1, "Янв"),
    FEBRUARY(2, "Фев"),
    MARCH(3, "Мар"),
    APRIL(4, "Апр"),
    MAY(5, "Май"),
    JUNE(6, "Июн"),
    JULY(7, "Июл"),
    AUGUST(8, "Авг"),
    SEPTEMBER(9, "Сен"),
    OCTOBER(10, "Окт"),
    NOVEMBER(11, "Ноя"),
    DECEMBER(12, "Дек");

    /** Номер месяца (1-12) */
    private final int number;

    /** Краткое название на русском языке */
    private final String shortName;
}
