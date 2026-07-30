package com.equitytrade.booking.account.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Account(
        UUID id,
        String name,
        String broker,
        String accountNumberLast4,
        String baseCurrency,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt) {

    private static final Pattern LAST4_PATTERN = Pattern.compile("[0-9]{4}");

    public Account {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(broker);
        Objects.requireNonNull(baseCurrency);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
    }

    public static Account create(
            String rawName,
            String rawBroker,
            String rawLast4,
            Instant now) {
        AccountFields fields = validate(rawName, rawBroker, rawLast4);
        Instant timestamp = now.truncatedTo(ChronoUnit.MICROS);
        return new Account(
                UUID.randomUUID(),
                fields.name(),
                fields.broker(),
                fields.last4(),
                "USD",
                AccountStatus.ACTIVE,
                timestamp,
                timestamp);
    }

    public Account update(
            String rawName,
            String rawBroker,
            String rawLast4,
            Instant now) {
        AccountFields fields = validate(rawName, rawBroker, rawLast4);
        return new Account(
                id,
                fields.name(),
                fields.broker(),
                fields.last4(),
                baseCurrency,
                status,
                createdAt,
                now.truncatedTo(ChronoUnit.MICROS));
    }

    public Account deactivate(Instant now) {
        if (status == AccountStatus.INACTIVE) {
            return this;
        }
        return new Account(
                id,
                name,
                broker,
                accountNumberLast4,
                baseCurrency,
                AccountStatus.INACTIVE,
                createdAt,
                now.truncatedTo(ChronoUnit.MICROS));
    }

    public Account activate(Instant now) {
        if (status == AccountStatus.ACTIVE) {
            return this;
        }
        return new Account(
                id,
                name,
                broker,
                accountNumberLast4,
                baseCurrency,
                AccountStatus.ACTIVE,
                createdAt,
                now.truncatedTo(ChronoUnit.MICROS));
    }

    private static AccountFields validate(
            String rawName,
            String rawBroker,
            String rawLast4) {
        List<AccountFieldViolation> violations = new ArrayList<>();
        String name = requiredText("name", rawName, violations);
        String broker = requiredText("broker", rawBroker, violations);
        String last4 = normalizeLast4(rawLast4, violations);
        if (!violations.isEmpty()) {
            throw new AccountValidationException(violations);
        }
        return new AccountFields(name, broker, last4);
    }

    private static String requiredText(
            String field,
            String value,
            List<AccountFieldViolation> violations) {
        if (value == null || value.isBlank()) {
            violations.add(new AccountFieldViolation(field, "is required"));
            return "";
        }
        String normalized = value.strip();
        if (normalized.length() > 100) {
            violations.add(new AccountFieldViolation(
                    field, "must be at most 100 characters"));
        }
        return normalized;
    }

    private static String normalizeLast4(
            String value,
            List<AccountFieldViolation> violations) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (!LAST4_PATTERN.matcher(normalized).matches()) {
            violations.add(new AccountFieldViolation(
                    "accountNumberLast4", "must be exactly 4 digits"));
        }
        return normalized;
    }

    private record AccountFields(String name, String broker, String last4) {
    }
}
