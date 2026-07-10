package pyc.lopatuxin.shared.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * In-process port for the investment → budget synchronization.
 * Implemented by the budget module, called synchronously by the investment module
 * inside the trade transaction (behavior identical to the former HTTP call: budget
 * failure rolls back the trade, and the returned id links the created budget entry).
 */
public interface InvestmentBudgetSync {

    /** Creates the budget income/expense mirroring a trade; returns the created budget entry id. */
    UUID createEntry(UUID userId, EntryType type, BigDecimal amount, Instant executedAt);

    /** Deletes the previously created budget entry when its trade is deleted. */
    void deleteEntry(UUID userId, UUID budgetEntryId, EntryType type);
}
