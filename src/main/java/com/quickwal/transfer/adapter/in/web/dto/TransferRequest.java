package com.quickwal.transfer.adapter.in.web.dto;

import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;

import java.util.Objects;
import java.util.UUID;

/** HTTP request body for starting a peer-to-peer transfer. */
public record TransferRequest(
        UUID sourceWalletId,
        UUID targetWalletId,
        Long amountMinor,
        String currency) {

    /**
     * Validates required transport fields while leaving transfer rules to the domain.
     *
     * @param sourceWalletId wallet from which funds will be debited
     * @param targetWalletId wallet to which funds will be credited
     * @param amountMinor amount in integer minor units
     * @param currency stable currency code
     */
    public TransferRequest {
        if (sourceWalletId == null) throw new IllegalArgumentException("sourceWalletId is required");
        if (targetWalletId == null) throw new IllegalArgumentException("targetWalletId is required");
        if (amountMinor == null) throw new IllegalArgumentException("amountMinor is required");
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    /**
     * Converts the wire representation into the common money value object.
     *
     * @return a money value in the requested currency
     * @throws IllegalArgumentException if the currency code is unsupported
     */
    public Money toMoney() {
        return new Money(amountMinor, CurrencyType.fromCode(currency));
    }
}

