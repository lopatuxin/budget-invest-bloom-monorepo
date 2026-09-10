package pyc.lopatuxin.investment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import pyc.lopatuxin.investment.dto.response.TransactionResponseDto;
import pyc.lopatuxin.investment.entity.Transaction;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(source = "security.ticker", target = "ticker")
    @Mapping(source = "security.name", target = "securityName")
    @Mapping(target = "amount", expression = "java(transaction.getQuantity().multiply(transaction.getPrice())" +
            ".setScale(2, java.math.RoundingMode.HALF_UP))")
    TransactionResponseDto toDto(Transaction transaction);

    List<TransactionResponseDto> toDtoList(List<Transaction> transactions);
}
