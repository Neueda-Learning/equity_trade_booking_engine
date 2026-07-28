package com.equitytrade.booking.account.infrastructure.persistence;

import com.equitytrade.booking.account.domain.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "accounts")
class AccountJpaEntity {

    @Id
    @Column(name = "id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String id;

    @Column(name = "name", nullable = false, length = 100, unique = true)
    private String name;

    @Column(name = "broker", nullable = false, length = 100)
    private String broker;

    @Column(name = "account_number_last4", length = 4)
    private String accountNumberLast4;

    @Column(name = "base_currency", nullable = false, length = 3)
    private String baseCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime updatedAt;

    protected AccountJpaEntity() {
    }

    AccountJpaEntity(
            String id,
            String name,
            String broker,
            String accountNumberLast4,
            String baseCurrency,
            AccountStatus status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.broker = broker;
        this.accountNumberLast4 = accountNumberLast4;
        this.baseCurrency = baseCurrency;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    String getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getBroker() {
        return broker;
    }

    String getAccountNumberLast4() {
        return accountNumberLast4;
    }

    String getBaseCurrency() {
        return baseCurrency;
    }

    AccountStatus getStatus() {
        return status;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
