package pyc.lopatuxin.budget.exception;

public class CategoryHasExpensesException extends RuntimeException {
    private final int expenseCount;

    public CategoryHasExpensesException(int expenseCount) {
        super("Невозможно удалить категорию: есть связанные расходы (" + expenseCount + ")");
        this.expenseCount = expenseCount;
    }

    public int getExpenseCount() {
        return expenseCount;
    }
}
