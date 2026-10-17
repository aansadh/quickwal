package com.quickwal.transfer.adapter.in.web.dto;

import java.util.UUID;

/** HTTP representation of a transfer and its current saga state. */
public record TransferResponse(
        UUID transferId,
        UUID sourceWalletId,
        UUID targetWalletId,
        long amountMinor,
        String currency,
        String status,
        String message) {
}
