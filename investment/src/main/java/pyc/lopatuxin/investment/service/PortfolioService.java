package pyc.lopatuxin.investment.service;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pyc.lopatuxin.investment.dto.response.PositionResponseDto;
import pyc.lopatuxin.investment.entity.Position;
import pyc.lopatuxin.investment.mapper.PositionMapper;
import pyc.lopatuxin.investment.repository.PositionRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PortfolioService {

    private final PositionRepository positionRepository;
    private final PositionMapper positionMapper;

    @Transactional(readOnly = true)
    public List<PositionResponseDto> listPositions(UUID userId) {
        return positionMapper.toDtoList(positionRepository.findByUserId(userId));
    }

    @Transactional(readOnly = true)
    public PositionResponseDto getByTicker(UUID userId, String ticker) {
        Position position = positionRepository.findByUserIdAndSecurity_Ticker(userId, ticker.toUpperCase())
                .orElseThrow(() -> new EntityNotFoundException("Position not found: " + ticker));
        return positionMapper.toDto(position);
    }
}
