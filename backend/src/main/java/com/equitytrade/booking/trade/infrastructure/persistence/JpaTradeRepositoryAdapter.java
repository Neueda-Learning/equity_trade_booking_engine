package com.equitytrade.booking.trade.infrastructure.persistence;

import com.equitytrade.booking.trade.domain.Trade;
import com.equitytrade.booking.trade.domain.TradePage;
import com.equitytrade.booking.trade.domain.TradeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.UUID;

@Repository
public class JpaTradeRepositoryAdapter implements TradeRepository {

    private final SpringDataTradeRepository repository;

    public JpaTradeRepositoryAdapter(SpringDataTradeRepository repository) {
        this.repository = repository;
    }

    @Override
    public Trade save(Trade trade) {
        return toDomain(repository.saveAndFlush(toEntity(trade)));
    }

    @Override
    public TradePage findAll(UUID accountId, int page, int size) {
        Sort sort = Sort.by(Sort.Direction.DESC, "executedAt")
                .and(Sort.by(Sort.Direction.DESC, "createdAt"))
                .and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageRequest = PageRequest.of(page, size, sort);
        Page<TradeJpaEntity> result = accountId == null
                ? repository.findAll(pageRequest)
                : repository.findByAccountId(accountId.toString(), pageRequest);
        return new TradePage(
                result.getContent().stream().map(this::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private TradeJpaEntity toEntity(Trade trade) {
        return new TradeJpaEntity(
                trade.id().toString(),
                trade.accountId().toString(),
                trade.ticker(),
                trade.side(),
                trade.quantity(),
                trade.tradePrice(),
                trade.executedAt().atOffset(ZoneOffset.UTC).toLocalDateTime(),
                trade.status(),
                trade.createdAt().atOffset(ZoneOffset.UTC).toLocalDateTime());
    }

    private Trade toDomain(TradeJpaEntity entity) {
        return new Trade(
                UUID.fromString(entity.getId()),
                UUID.fromString(entity.getAccountId()),
                entity.getTicker(),
                entity.getSide(),
                entity.getQuantity(),
                entity.getTradePrice(),
                entity.getExecutedAt().toInstant(ZoneOffset.UTC),
                entity.getStatus(),
                entity.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
