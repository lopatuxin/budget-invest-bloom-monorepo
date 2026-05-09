package pyc.lopatuxin.investment.migration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pyc.lopatuxin.investment.client.BudgetClient;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentBudgetMigrationRunner implements ApplicationRunner {

    private final TransactionRepository transactionRepository;
    private final BudgetClient budgetClient;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Transaction> orphans = transactionRepository.findAllByBudgetEntryIdIsNull();
            if (orphans.isEmpty()) {
                return;
            }
            log.info("Found {} transactions without budgetEntryId, syncing to budget...", orphans.size());
            int synced = 0;
            for (Transaction tx : orphans) {
                boolean ok = processOne(tx);
                if (ok) {
                    synced++;
                }
            }
            log.info("Migration done: synced={}/{}", synced, orphans.size());
        } catch (Exception e) {
            log.warn("Migration runner failed (will retry on next start): {}", e.getMessage());
        }
    }

    private boolean processOne(Transaction tx) {
        try {
            BigDecimal amount = tx.getQuantity().multiply(tx.getPrice());
            UUID entryId = budgetClient.createInvestmentEntry(
                    tx.getUserId(), tx.getType(), amount, tx.getExecutedAt());
            transactionTemplate.executeWithoutResult(status -> {
                tx.setBudgetEntryId(entryId);
                transactionRepository.save(tx);
            });
            return true;
        } catch (Exception e) {
            log.warn("Failed to sync transaction id={} to budget: {}", tx.getId(), e.getMessage());
            return false;
        }
    }
}
