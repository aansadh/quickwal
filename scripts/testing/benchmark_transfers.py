#!/usr/bin/env python3
"""Load-test QuickWal's asynchronous peer-to-peer transfer HTTP API.

Run with Python 3 and no third-party packages. The application and funded
wallets must already exist. Every accepted request creates a durable transfer
and changes wallet balances; this script never resets or seeds the database.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any


# Defaults for the local development environment. Adjust these values here or
# override them with command-line flags below.
DEFAULT_BASE_URL = "http://localhost:8081"
DEFAULT_SOURCE_WALLET_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
DEFAULT_TARGET_WALLET_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
DEFAULT_AMOUNT_MINOR = 1
DEFAULT_CURRENCY = "QC"
DEFAULT_REQUESTS_PER_LEVEL = 100
DEFAULT_CONCURRENCY_LEVELS = (1, 10, 25, 50)
DEFAULT_POLL_INTERVAL_SECONDS = 0.1
DEFAULT_TRANSFER_TIMEOUT_SECONDS = 120.0
DEFAULT_HTTP_TIMEOUT_SECONDS = 15.0

#####

TERMINAL_STATUSES = {"SUCCESSFUL", "FAILURE", "ABORTED"}


@dataclass
class PostResult:
    """Outcome and timing for one transfer creation request."""

    status_code: int | None
    started_at: float
    completed_at: float
    transfer_id: str | None
    error: str | None


@dataclass
class CompletionResult:
    """Terminal transfer state and elapsed time, or a timeout/error."""

    transfer_id: str
    status: str
    elapsed_seconds: float | None
    error: str | None


def percentile(values: list[float], fraction: float) -> float | None:
    """Return a nearest-rank percentile, or None when no values were measured."""
    if not values:
        return None
    ordered = sorted(values)
    rank = max(1, math.ceil(fraction * len(ordered)))
    return ordered[rank - 1]


def format_ms(seconds: float | None) -> str:
    """Format a seconds value as milliseconds for the console report."""
    return "n/a" if seconds is None else f"{seconds * 1000:.1f}"


def latency_summary(values: list[float]) -> dict[str, float | None]:
    """Calculate p50, p95, and p99 latency values in seconds."""
    return {
        "p50": percentile(values, 0.50),
        "p95": percentile(values, 0.95),
        "p99": percentile(values, 0.99),
    }


def http_json(
    url: str,
    *,
    method: str,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: float,
) -> tuple[int, dict[str, Any]]:
    """Send one HTTP request and decode its JSON response."""
    request = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw_body = response.read()
        decoded = json.loads(raw_body) if raw_body else {}
        if not isinstance(decoded, dict):
            raise ValueError("API returned a JSON response that is not an object")
        return response.status, decoded


def send_transfer(
    endpoint: str,
    body: bytes,
    http_timeout: float,
) -> PostResult:
    """Submit one transfer with a unique idempotency key and measure latency."""
    started_at = time.perf_counter()
    try:
        status_code, response = http_json(
            endpoint,
            method="POST",
            body=body,
            headers={
                "Content-Type": "application/json",
                "Idempotency-Key": str(uuid.uuid4()),
            },
            timeout=http_timeout,
        )
        transfer_id = response.get("transferId")
        if status_code != 202:
            error = f"Unexpected HTTP status {status_code}: {response}"
            transfer_id = None
        elif not isinstance(transfer_id, str):
            error = "HTTP 202 response did not include transferId"
            transfer_id = None
        else:
            error = None
        return PostResult(status_code, started_at, time.perf_counter(), transfer_id, error)
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", errors="replace")[:500]
        return PostResult(error.code, started_at, time.perf_counter(), None, details)
    except (urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
        return PostResult(None, started_at, time.perf_counter(), None, str(error))


def wait_for_terminal_status(
    endpoint: str,
    post_result: PostResult,
    poll_interval: float,
    transfer_timeout: float,
    http_timeout: float,
) -> CompletionResult:
    """Poll one accepted transfer until terminal status or timeout."""
    transfer_id = post_result.transfer_id
    assert transfer_id is not None
    status_url = f"{endpoint}/{urllib.parse.quote(transfer_id, safe='')}"
    deadline = time.monotonic() + transfer_timeout
    last_error: str | None = None

    while time.monotonic() < deadline:
        try:
            _, response = http_json(
                status_url,
                method="GET",
                timeout=http_timeout,
            )
            status = response.get("status")
            if isinstance(status, str) and status in TERMINAL_STATUSES:
                return CompletionResult(
                    transfer_id,
                    status,
                    time.perf_counter() - post_result.started_at,
                    None,
                )
            if not isinstance(status, str):
                last_error = "Transfer status response did not include status"
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="replace")[:500]
            last_error = f"HTTP {error.code}: {details}"
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
            last_error = str(error)
        time.sleep(poll_interval)

    return CompletionResult(transfer_id, "TIMEOUT", None, last_error)


def run_level(
    *,
    endpoint: str,
    request_body: bytes,
    requests: int,
    concurrency: int,
    poll_interval: float,
    transfer_timeout: float,
    http_timeout: float,
) -> dict[str, Any]:
    """Run one request-count/concurrency batch and collect its measurements."""
    run_started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        post_results = list(
            executor.map(
                lambda _: send_transfer(endpoint, request_body, http_timeout),
                range(requests),
            )
        )
    post_finished = time.perf_counter()

    accepted = [result for result in post_results if result.transfer_id is not None]
    completion_workers = min(concurrency, max(1, len(accepted)))
    with concurrent.futures.ThreadPoolExecutor(max_workers=completion_workers) as executor:
        completion_results = list(
            executor.map(
                lambda result: wait_for_terminal_status(
                    endpoint,
                    result,
                    poll_interval,
                    transfer_timeout,
                    http_timeout,
                ),
                accepted,
            )
        )
    run_finished = time.perf_counter()

    post_latencies = [result.completed_at - result.started_at for result in post_results]
    end_to_end_latencies = [
        result.elapsed_seconds
        for result in completion_results
        if result.elapsed_seconds is not None
    ]
    completion_counts: dict[str, int] = {}
    for result in completion_results:
        completion_counts[result.status] = completion_counts.get(result.status, 0) + 1

    post_duration = post_finished - run_started
    total_duration = run_finished - run_started
    successful = completion_counts.get("SUCCESSFUL", 0)

    return {
        "concurrency": concurrency,
        "requests": requests,
        "accepted": len(accepted),
        "http_errors": requests - len(accepted),
        "post_elapsed_seconds": post_duration,
        "accepted_requests_per_second": len(accepted) / post_duration if post_duration else 0,
        "post_latency_seconds": latency_summary(post_latencies),
        "terminal_statuses": completion_counts,
        "end_to_end_elapsed_seconds": total_duration,
        "successful_transfers_per_second": successful / total_duration if total_duration else 0,
        "end_to_end_latency_seconds": latency_summary(end_to_end_latencies),
        "request_errors": [result.error for result in post_results if result.error],
        "completion_errors": [result.error for result in completion_results if result.error],
    }


def create_argument_parser() -> argparse.ArgumentParser:
    """Create the argument parser for benchmark configuration overrides."""
    parser = argparse.ArgumentParser(
        description=(
            "Benchmark QuickWal's asynchronous transfer API. Each accepted request "
            "persists a transfer and changes wallet balances."
        ),
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Application base URL")
    parser.add_argument(
        "--source-wallet-id",
        default=DEFAULT_SOURCE_WALLET_ID,
        help="Funded sender wallet UUID",
    )
    parser.add_argument(
        "--target-wallet-id",
        default=DEFAULT_TARGET_WALLET_ID,
        help="Receiver wallet UUID",
    )
    parser.add_argument("--amount-minor", type=int, default=DEFAULT_AMOUNT_MINOR)
    parser.add_argument("--currency", default=DEFAULT_CURRENCY)
    parser.add_argument(
        "--requests",
        type=int,
        default=DEFAULT_REQUESTS_PER_LEVEL,
        help="Number of POST requests at each concurrency level",
    )
    parser.add_argument(
        "--concurrency-levels",
        type=int,
        nargs="+",
        default=list(DEFAULT_CONCURRENCY_LEVELS),
        metavar="N",
        help="One or more concurrency levels to measure, run in order",
    )
    parser.add_argument(
        "--poll-interval",
        type=float,
        default=DEFAULT_POLL_INTERVAL_SECONDS,
        help="Seconds between GET status polls for each transfer",
    )
    parser.add_argument(
        "--transfer-timeout",
        type=float,
        default=DEFAULT_TRANSFER_TIMEOUT_SECONDS,
        help="Seconds to wait for each accepted transfer to become terminal",
    )
    parser.add_argument(
        "--http-timeout",
        type=float,
        default=DEFAULT_HTTP_TIMEOUT_SECONDS,
        help="Timeout in seconds for an individual HTTP request",
    )
    return parser


def validate_args(args: argparse.Namespace, parser: argparse.ArgumentParser) -> str:
    """Validate options and return the normalized transfer endpoint."""
    if args.requests <= 0:
        parser.error("--requests must be greater than zero")
    if not args.concurrency_levels or any(level <= 0 for level in args.concurrency_levels):
        parser.error("--concurrency-levels must contain positive integers")
    if args.amount_minor <= 0:
        parser.error("--amount-minor must be greater than zero")
    if args.poll_interval < 0:
        parser.error("--poll-interval cannot be negative")
    if args.transfer_timeout <= 0 or args.http_timeout <= 0:
        parser.error("timeouts must be greater than zero")
    try:
        uuid.UUID(args.source_wallet_id)
        uuid.UUID(args.target_wallet_id)
    except ValueError:
        parser.error("wallet IDs must be valid UUIDs")

    base_url = args.base_url.rstrip("/")
    if not base_url.startswith(("http://", "https://")):
        parser.error("--base-url must start with http:// or https://")
    return f"{base_url}/api/v1/transfers"


def print_report(
    results: list[dict[str, Any]],
    *,
    base_url: str,
    requests: int,
    currency: str,
    amount_minor: int,
) -> None:
    """Print a compact table and explain the two throughput measurements."""
    print("\nQuickWal transfer benchmark")
    print(f"Target: {base_url}")
    print(f"Workload per level: {requests} transfers × {amount_minor} {currency} minor units each")
    print(
        "Concurrency | Accepted/s | POST p50/p95/p99 ms | Successful/s | "
        "E2E p50/p95/p99 ms | POST errors | Terminal results"
    )
    print("-" * 130)
    for result in results:
        post = result["post_latency_seconds"]
        e2e = result["end_to_end_latency_seconds"]
        statuses = ", ".join(
            f"{status}:{count}" for status, count in sorted(result["terminal_statuses"].items())
        ) or "none"
        print(
            f"{result['concurrency']:>11} | {result['accepted_requests_per_second']:>10.1f} | "
            f"{format_ms(post['p50'])}/{format_ms(post['p95'])}/{format_ms(post['p99'])} | "
            f"{result['successful_transfers_per_second']:>12.1f} | "
            f"{format_ms(e2e['p50'])}/{format_ms(e2e['p95'])}/{format_ms(e2e['p99'])} | "
            f"{result['http_errors']:>11} | {statuses}"
        )

    print("\nAccepted/s measures HTTP 202 responses during the POST phase.")
    print("Successful/s measures completed SUCCESSFUL transfers over the full batch duration.")
    print("E2E latency runs from each POST start until its terminal status is observed by polling.")
    print("These results describe this workload and environment; they are not a universal capacity limit.")

    errors = [
        (result["concurrency"], error)
        for result in results
        for error in result["request_errors"] + result["completion_errors"]
    ]
    if errors:
        print("\nSample errors:")
        for concurrency, error in errors[:10]:
            print(f"  concurrency {concurrency}: {error}")
        if len(errors) > 10:
            print(f"  ... and {len(errors) - 10} more")


def main() -> int:
    """Run all configured batches and emit a report to stdout."""
    parser = create_argument_parser()
    args = parser.parse_args()
    endpoint = validate_args(args, parser)
    try:
        request_body = json.dumps(
            {
                "sourceWalletId": args.source_wallet_id,
                "targetWalletId": args.target_wallet_id,
                "amountMinor": args.amount_minor,
                "currency": args.currency,
            }
        ).encode("utf-8")

        print(
            f"Sending {args.requests} transfers at concurrency levels "
            f"{', '.join(map(str, args.concurrency_levels))} to {args.base_url.rstrip('/')}"
        )
        print("Accepted transfers and wallet balance changes are retained in the database.")

        results: list[dict[str, Any]] = []
        for concurrency in args.concurrency_levels:
            print(f"Running concurrency {concurrency}...", flush=True)
            results.append(
                run_level(
                    endpoint=endpoint,
                    request_body=request_body,
                    requests=args.requests,
                    concurrency=concurrency,
                    poll_interval=args.poll_interval,
                    transfer_timeout=args.transfer_timeout,
                    http_timeout=args.http_timeout,
                )
            )

        print_report(
            results,
            base_url=args.base_url.rstrip("/"),
            requests=args.requests,
            currency=args.currency,
            amount_minor=args.amount_minor,
        )
        all_succeeded = all(
            result["accepted"] == args.requests
            and result["terminal_statuses"].get("SUCCESSFUL", 0) == args.requests
            for result in results
        )
        return 0 if all_succeeded else 1
    except KeyboardInterrupt:
        print("\nBenchmark interrupted. Already accepted transfers continue processing.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
