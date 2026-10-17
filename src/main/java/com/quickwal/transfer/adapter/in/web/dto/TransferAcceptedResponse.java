package com.quickwal.transfer.adapter.in.web.dto;

import java.util.UUID;

/** Response returned after a transfer request has been accepted for asynchronous processing. */
public record TransferAcceptedResponse(UUID transferId) {
}
