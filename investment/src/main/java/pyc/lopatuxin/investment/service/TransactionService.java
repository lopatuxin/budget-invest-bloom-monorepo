package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.request.CreateTransactionDto;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.entity.Security;
import pyc.lopatuxin.investment.entity.Transaction;
import pyc.lopatuxin.investment.entity.enums.TransactionType;
import pyc.lopatuxin.investment.mapper.TransactionMapper;
import pyc.lopatuxin.investment.repository.PositionRepository;
import pyc.lopatuxin.investment.repository.SecurityRepository;
import pyc.lopatuxin.investment.repository.TransactionRepository;
import pyc.lopatuxin.investment.service.market.MarketDataService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final PositionRepository positionRepository;
    private final SecurityRepository securityRepository;
    private final TransactionMapper transactionMapper;
    private final MarketDataService marketDataService;

    @Transactional
    public TransactionResponseDto create(UUID userId, CreateTransactionDto dto) {
        Security security = marketDataService.ensureSecurity(dto.getTicker(), dto.getSecurityType());

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .security(security)
                .type(dto.getType())
                .quantity(dto.getQuantity())
                .price(dto.getPrice())
                .executedAt(dto.getExecutedAt())
                .build();
        Transaction saved = transactionRepository.save(transaction);

        recalculatePosition(userId, security.getTicker());

        log.info("Transaction created: userId={}, ticker={}, type={}", userId, security.getTicker(), dto.getType());
        return transactionMapper.toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponseDto> list(UUID userId, String ticker) {
        List<Transaction> transactions;
        if (ticker != null && !ticker.isBlank()) {
            transactions = transactionRepository.findByUserIdAndSecurity_Ticker(userId, ticker.toUpperCase());
        } else {
            transactions = transactionRepository.findByUserId(userId);
        }
        transactions.sort(Comparator.comparing(Transaction::getExecutedAt).reversed());
        return transactionMapper.toDtoList(transactions);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found: " + id));
        if (!tx.getUserId().equals(userId)) {
            throw new EntityNotFoundException("Transaction not found: " + id);
        }
        String ticker = tx.getSecurity().getTicker();
        transactionRepository.delete(tx);
        transactionRepository.flush();
        recalculatePosition(userId, ticker);
        log.info("Transaction deleted: userId={}, id={}", userId, id);
    }

    private void recalculatePosition(UUID userId, String ticker) {
        List<Transaction> txns = new ArrayList<>(transactionRepository
                .findByUserIdAndSecurity_Ticker(userId, ticker));
        txns.sort(Comparator.comparing(Transaction::getExecutedAt)
                .thenComparing(Transaction::getCreatedAt));

        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (Transaction t : txns) {
            if (t.getType() == TransactionType.BUY) {
                totalCost = totalCost.add(t.getQuantity().multiply(t.getPrice()));
                qty = qty.add(t.getQuantity());
            } else {
                if (qty.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                            "Insufficient shares for SELL: ticker=" + ticker + ", available=0");
                }
                BigDecimal avgCost = totalCost.divide(qty, 8, RoundingMode.HALF_UP);
                BigDecimal costToRemove = avgCost.multiply(t.getQuantity());
                totalCost = totalCost.subtract(costToRemove);
                qty = qty.subtract(t.getQuantity());
                if (qty.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalStateException(
                            "Insufficient shares for SELL: ticker=" + ticker +
                            ", tried to sell " + t.getQuantity() + " but only " +
                            qty.add(t.getQuantity()) + " available");
                }
            }
        }

        Optional<Position> existingOpt = positionRepository
                .findByUserIdAndSecurity_Ticker(userId, ticker);

        if (qty.compareTo(BigDecimal.ZERO) == 0) {
            existingOpt.ifPresent(positionRepository::delete);
            return;
        }

        Security security = securityRepository.findById(ticker).orElseThrow();
        BigDecimal avgPrice = totalCost.divide(qty, 2, RoundingMode.HALF_UP);

        Position position = existingOpt.orElseGet(() -> Position.builder()
                .userId(userId)
                .security(security)
                .build());
        position.setQuantity(qty);
        position.setTotalCost(totalCost.setScale(2, RoundingMode.HALF_UP));
        position.setAveragePrice(avgPrice);
        positionRepository.save(position);
    }
}
