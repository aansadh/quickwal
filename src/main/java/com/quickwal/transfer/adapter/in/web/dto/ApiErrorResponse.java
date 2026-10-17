package com.quickwal.transfer.adapter.in.web.dto;

/** Consistent error body returned by transfer HTTP endpoints. */
public record ApiErrorResponse(String error, String message, String traceId) {
}
