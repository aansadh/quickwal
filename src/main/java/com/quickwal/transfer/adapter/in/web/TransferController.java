package com.quickwal.transfer.adapter.in.web;

import com.quickwal.common.architecture.InboundAdapter;
import com.quickwal.common.money.Money;
import com.quickwal.transfer.adapter.in.web.dto.TransferAcceptedResponse;
import com.quickwal.transfer.adapter.in.web.dto.TransferRequest;
import com.quickwal.transfer.adapter.in.web.dto.TransferResponse;
import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.port.in.GetTransfer;
import com.quickwal.transfer.core.port.in.InitiateTransfer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/** HTTP inbound adapter for initiating and querying peer-to-peer transfers. */
@InboundAdapter
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {
    private final InitiateTransfer initiateTransfer;
    private final GetTransfer getTransfer;

    /**
     * Creates the transfer controller with inbound use-case ports.
     *
     * @param initiateTransfer use case for starting the transfer saga
     * @param getTransfer use case for querying transfer state
     */
    public TransferController(InitiateTransfer initiateTransfer, GetTransfer getTransfer) {
        this.initiateTransfer = initiateTransfer;
        this.getTransfer = getTransfer;
    }

    /**
     * Starts a transfer and returns its identifier for status polling.
     *
     * @param idempotencyKey caller-provided key used to prevent duplicate transfer creation
     * @param request validated HTTP transfer request
     * @return HTTP 202 with the accepted transfer identifier and its status URL
     */
    @PostMapping
    public ResponseEntity<TransferAcceptedResponse> initiate(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody TransferRequest request) {
        Money amount = request.toMoney();
        UUID transferId = initiateTransfer.execute(request.sourceWalletId(), request.targetWalletId(),
                amount, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{transferId}")
                .buildAndExpand(transferId)
                .toUri();
        return ResponseEntity.accepted()
                .location(location)
                .body(new TransferAcceptedResponse(transferId));
    }

    /**
     * Retrieves the latest persisted state of a transfer saga.
     *
     * @param transferId identifier from the accepted transfer response
     * @return the current transfer representation
     */
    @GetMapping("/{transferId}")
    public TransferResponse get(@PathVariable UUID transferId) {
        return toResponse(getTransfer.get(transferId));
    }

    /**
     * Maps the transfer domain object to the HTTP response model.
     *
     * @param transfer current transfer state
     * @return transport-safe transfer response
     */
    private TransferResponse toResponse(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getSenderWalletId(),
                transfer.getReceiverWalletId(), transfer.getAmount().amountMinor(),
                transfer.getAmount().currency().code(), transfer.getStatus().code(), transfer.getMsg());
    }
}
