#!/usr/bin/env python3
"""Run a bounded, steady-arrival load against QuickWal's async transfer API.

This is a measurement client, not a database reset tool. Every accepted POST
creates persistent application data and changes the selected wallet balances.
Status polling runs concurrently with request generation and continues during
the drain period. Polling itself adds HTTP traffic and its observed end-to-end
latency includes the polling interval and client scheduling delay.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import json
import math
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from dataclasses import dataclass
from typing import Any


DEFAULT_BASE_URL = "http://127.0.0.1:8081"
DEFAULT_SOURCE_WALLET_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
DEFAULT_TARGET_WALLET_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
BENCHMARK_WALLET_PREFIX = "quickwal-benchmark-wallet-"
TERMINAL_STATUSES = {"SUCCESSFUL", "FAILURE", "ABORTED"}


@dataclass
class PostResult:
    scheduled_at: float
    started_at: float
    completed_at: float
    status_code: int | None
    transfer_id: str | None
    error: str | None
    poll_submitted: bool = False


@dataclass
class CompletionResult:
    transfer_id: str
    status: str
    observed_at: float
    error: str | None


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    return ordered[max(1, math.ceil(fraction * len(ordered))) - 1]


def latency_summary(values: list[float]) -> tuple[float | None, float | None, float | None]:
    return tuple(percentile(values, fraction) for fraction in (0.50, 0.95, 0.99))


def format_ms(value: float | None) -> str:
    return "n/a" if value is None else f"{value * 1000:.1f}"


def benchmark_wallet_id(index: int) -> str:
    """Match the deterministic wallet IDs inserted by seed_benchmark_wallets.sql."""
    digest = hashlib.md5(
        f"{BENCHMARK_WALLET_PREFIX}{index}".encode("ascii"),
        usedforsecurity=False,
    ).hexdigest()
    return str(uuid.UUID(hex=digest))


def choose_wallet_pair(
    args: argparse.Namespace,
    wallet_pool: list[str] | None,
    rng: random.Random,
) -> tuple[str, str]:
    if wallet_pool is None:
        return args.source_wallet_id, args.target_wallet_id

    source_index = rng.randrange(len(wallet_pool))
    target_index = rng.randrange(len(wallet_pool) - 1)
    if target_index >= source_index:
        target_index += 1
    return wallet_pool[source_index], wallet_pool[target_index]


def transfer_body(args: argparse.Namespace, source_wallet_id: str, target_wallet_id: str) -> bytes:
    return json.dumps(
        {
            "sourceWalletId": source_wallet_id,
            "targetWalletId": target_wallet_id,
            "amountMinor": args.amount_minor,
            "currency": args.currency,
        }
    ).encode("utf-8")


def http_json(
    url: str,
    *,
    method: str,
    body: bytes | None = None,
    headers: dict[str, str] | None = None,
    timeout: float,
) -> tuple[int, dict[str, Any]]:
    request = urllib.request.Request(url, data=body, headers=headers or {}, method=method)
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw_body = response.read()
        payload = json.loads(raw_body) if raw_body else {}
        if not isinstance(payload, dict):
            raise ValueError("API returned a JSON response that is not an object")
        return response.status, payload


def submit_transfer(
    endpoint: str,
    body: bytes,
    scheduled_at: float,
    timeout: float,
) -> PostResult:
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
            timeout=timeout,
        )
        transfer_id = response.get("transferId")
        if status_code != 202:
            return PostResult(
                scheduled_at, started_at, time.perf_counter(), status_code, None,
                f"Unexpected HTTP status {status_code}: {response}",
            )
        if not isinstance(transfer_id, str):
            return PostResult(
                scheduled_at, started_at, time.perf_counter(), status_code, None,
                "HTTP 202 response did not include transferId",
            )
        return PostResult(scheduled_at, started_at, time.perf_counter(), status_code, transfer_id, None)
    except urllib.error.HTTPError as error:
        details = error.read().decode("utf-8", errors="replace")[:500]
        return PostResult(
            scheduled_at, started_at, time.perf_counter(), error.code, None, details
        )
    except (urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
        return PostResult(
            scheduled_at, started_at, time.perf_counter(), None, None, str(error)
        )


def wait_for_terminal(
    endpoint: str,
    post: PostResult,
    poll_interval: float,
    timeout: float,
    http_timeout: float,
) -> CompletionResult:
    assert post.transfer_id is not None
    status_url = f"{endpoint}/{urllib.parse.quote(post.transfer_id, safe='')}"
    deadline = time.monotonic() + timeout
    last_error: str | None = None
    while time.monotonic() < deadline:
        try:
            _, response = http_json(status_url, method="GET", timeout=http_timeout)
            status = response.get("status")
            if isinstance(status, str) and status in TERMINAL_STATUSES:
                return CompletionResult(post.transfer_id, status, time.perf_counter(), None)
            if not isinstance(status, str):
                last_error = "Status response did not include a string status"
        except urllib.error.HTTPError as error:
            details = error.read().decode("utf-8", errors="replace")[:500]
            last_error = f"HTTP {error.code}: {details}"
        except (urllib.error.URLError, TimeoutError, ValueError, OSError) as error:
            last_error = str(error)
        time.sleep(poll_interval)
    return CompletionResult(post.transfer_id, "TIMEOUT", time.perf_counter(), last_error)


def collect_post_results(
    futures: dict[concurrent.futures.Future[PostResult], float],
    posts: list[PostResult],
) -> list[PostResult]:
    done = [future for future in futures if future.done()]
    for future in done:
        posts.append(future.result())
        del futures[future]
    return [post for post in posts if post.transfer_id is not None]


def run(args: argparse.Namespace, endpoint: str, body: bytes) -> int:
    request_window = args.requests / args.rate
    wallet_pool = (
        [benchmark_wallet_id(index) for index in range(1, args.wallet_pool_size + 1)]
        if args.wallet_pool_size
        else None
    )
    wallet_rng = random.Random(args.wallet_selection_seed)
    completion_futures: dict[concurrent.futures.Future[CompletionResult], float] = {}
    post_futures: dict[concurrent.futures.Future[PostResult], float] = {}
    posts: list[PostResult] = []
    completions: list[CompletionResult] = []
    dropped = 0
    started_at = time.perf_counter()

    print(
        f"Target: {args.base_url.rstrip('/')} | requests={args.requests} | "
        f"offered_rate={args.rate:g}/s | max_in_flight_POST={args.max_concurrency}"
    )
    if wallet_pool is None:
        print(
            f"Payload: {args.amount_minor} {args.currency} minor units per transfer | "
            f"status_poll={args.poll_interval:g}s | wallet pair={args.source_wallet_id} -> "
            f"{args.target_wallet_id}"
        )
    else:
        print(
            f"Payload: {args.amount_minor} {args.currency} minor units per transfer | "
            f"status_poll={args.poll_interval:g}s | random distinct wallet pairs from "
            f"pool_size={len(wallet_pool)} | selection_seed={args.wallet_selection_seed}"
        )
    print("Accepted transfers and their database effects are retained; no reset is performed.")

    with (
        concurrent.futures.ThreadPoolExecutor(max_workers=args.max_concurrency) as post_pool,
        concurrent.futures.ThreadPoolExecutor(max_workers=args.poll_workers) as poll_pool,
    ):
        for index in range(args.requests):
            scheduled_at = started_at + index / args.rate
            source_wallet_id, target_wallet_id = choose_wallet_pair(args, wallet_pool, wallet_rng)
            request_body = (
                body
                if wallet_pool is None
                else transfer_body(args, source_wallet_id, target_wallet_id)
            )
            while True:
                now = time.perf_counter()
                valid_ids = collect_post_results(post_futures, posts)
                for post in valid_ids:
                    if not post.poll_submitted:
                        post.poll_submitted = True
                        completion_futures[poll_pool.submit(
                            wait_for_terminal,
                            endpoint,
                            post,
                            args.poll_interval,
                            args.transfer_timeout,
                            args.http_timeout,
                        )] = post.started_at

                done_completions = [future for future in completion_futures if future.done()]
                for future in done_completions:
                    completions.append(future.result())
                    del completion_futures[future]

                if now >= scheduled_at and len(post_futures) < args.max_concurrency:
                    post_futures[post_pool.submit(
                        submit_transfer, endpoint, request_body, scheduled_at, args.http_timeout
                    )] = scheduled_at
                    break
                if now >= scheduled_at and len(post_futures) >= args.max_concurrency:
                    # Preserve the offered arrival schedule and count this slot as
                    # dropped instead of silently turning the test closed-loop.
                    dropped += 1
                    break
                time.sleep(min(0.01, max(0.0, scheduled_at - now)))

            if index % max(1, args.requests // 10) == 0:
                print(f"Scheduled {index + 1}/{args.requests}; POST slots dropped={dropped}", flush=True)

        load_finished_at = started_at + request_window
        while post_futures or completion_futures:
            collect_post_results(post_futures, posts)
            valid_ids = [post for post in posts if post.transfer_id is not None]
            # Completion polls are normally submitted during the arrival loop. If
            # a final POST completes at the end of the schedule, submit its poll now.
            for post in valid_ids:
                if not post.poll_submitted:
                    post.poll_submitted = True
                    completion_futures[poll_pool.submit(
                        wait_for_terminal,
                        endpoint,
                        post,
                        args.poll_interval,
                        args.transfer_timeout,
                        args.http_timeout,
                    )] = post.started_at

            done_completions = [future for future in completion_futures if future.done()]
            for future in done_completions:
                completions.append(future.result())
                del completion_futures[future]

            if time.perf_counter() > load_finished_at + args.drain_timeout:
                for future in list(completion_futures):
                    future.cancel()
                break
            time.sleep(0.01)

    total_elapsed = time.perf_counter() - started_at
    accepted = [post for post in posts if post.transfer_id is not None]
    post_latencies = [post.completed_at - post.started_at for post in posts]
    post_start_lag = [post.started_at - post.scheduled_at for post in posts]
    completion_by_id = {completion.transfer_id: completion for completion in completions}
    observed_end_to_end = [
        completion.observed_at - post.started_at
        for post in accepted
        if (completion := completion_by_id.get(post.transfer_id or "")) is not None
        and completion.status != "TIMEOUT"
    ]
    terminal_counts: dict[str, int] = {}
    for completion in completions:
        terminal_counts[completion.status] = terminal_counts.get(completion.status, 0) + 1

    accepted_rps = len(accepted) / request_window if request_window else 0.0
    success_count = terminal_counts.get("SUCCESSFUL", 0)
    batch_completion_rps = success_count / total_elapsed if total_elapsed else 0.0
    drain_seconds = max(0.0, total_elapsed - request_window)
    post_p = latency_summary(post_latencies)
    lag_p = latency_summary(post_start_lag)
    e2e_p = latency_summary(observed_end_to_end)
    errors = [post.error for post in posts if post.error]
    completion_errors = [item.error for item in completions if item.error]

    print("\nSteady-arrival transfer benchmark")
    print(f"Load window: {request_window:.3f}s (configured arrivals: {args.requests})")
    print(f"Total elapsed including drain: {total_elapsed:.3f}s")
    print(f"Offered slots: {args.requests} | dropped at in-flight cap: {dropped}")
    print(f"POST attempted: {len(posts)} | HTTP 202 accepted: {len(accepted)}")
    print(f"Accepted requests per configured load second: {accepted_rps:.2f}")
    print(f"Terminal results: {json.dumps(terminal_counts, sort_keys=True)}")
    print(f"Completed-batch throughput, successes / total elapsed: {batch_completion_rps:.2f}/s")
    print(f"Drain time after arrival window: {drain_seconds:.3f}s")
    print(
        "POST latency p50/p95/p99 ms: "
        f"{format_ms(post_p[0])}/{format_ms(post_p[1])}/{format_ms(post_p[2])}"
    )
    print(
        "POST scheduling lag p50/p95/p99 ms: "
        f"{format_ms(lag_p[0])}/{format_ms(lag_p[1])}/{format_ms(lag_p[2])}"
    )
    print(
        "Observed POST-to-terminal p50/p95/p99 ms: "
        f"{format_ms(e2e_p[0])}/{format_ms(e2e_p[1])}/{format_ms(e2e_p[2])}"
    )
    print(
        "End-to-end values are observation times from status polling; they include "
        "the polling interval and load-generator scheduling delay."
    )
    if errors or completion_errors:
        print("Sample errors:")
        for error in (errors + completion_errors)[:10]:
            print(f"  {error}")
    return 0 if not dropped and len(accepted) == args.requests else 1


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description=(
            "Bounded steady-arrival load generator for QuickWal transfers. "
            "Accepted requests permanently write transfer data and change wallet balances."
        ),
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    parser.add_argument("--source-wallet-id", default=DEFAULT_SOURCE_WALLET_ID)
    parser.add_argument("--target-wallet-id", default=DEFAULT_TARGET_WALLET_ID)
    parser.add_argument(
        "--wallet-pool-size",
        type=int,
        default=0,
        help=(
            "Select distinct source/target IDs from the deterministic benchmark wallet pool; "
            "seed it with scripts/testing/seed_benchmark_wallets.sql"
        ),
    )
    parser.add_argument(
        "--wallet-selection-seed",
        type=int,
        default=20261020,
        help="Seed for repeatable source/target selection when --wallet-pool-size is used",
    )
    parser.add_argument("--amount-minor", type=int, default=1)
    parser.add_argument("--currency", default="QC")
    parser.add_argument("--requests", type=int, required=True, help="Exact offered arrival slots")
    parser.add_argument("--rate", type=float, required=True, help="Target offered arrivals per second")
    parser.add_argument("--max-concurrency", type=int, required=True, help="Max in-flight POST requests")
    parser.add_argument("--poll-workers", type=int, default=8)
    parser.add_argument("--poll-interval", type=float, default=0.25)
    parser.add_argument("--transfer-timeout", type=float, default=120.0)
    parser.add_argument("--drain-timeout", type=float, default=180.0)
    parser.add_argument("--http-timeout", type=float, default=15.0)
    return parser


def main() -> int:
    parser = create_parser()
    args = parser.parse_args()
    if args.requests <= 0 or args.rate <= 0 or args.max_concurrency <= 0:
        parser.error("--requests, --rate, and --max-concurrency must be positive")
    if args.poll_workers <= 0 or args.poll_interval <= 0:
        parser.error("--poll-workers and --poll-interval must be positive")
    if args.transfer_timeout <= 0 or args.drain_timeout <= 0 or args.http_timeout <= 0:
        parser.error("timeouts must be positive")
    if args.amount_minor <= 0:
        parser.error("--amount-minor must be positive")
    if args.wallet_pool_size < 0 or args.wallet_pool_size == 1:
        parser.error("--wallet-pool-size must be zero or at least 2")
    try:
        uuid.UUID(args.source_wallet_id)
        uuid.UUID(args.target_wallet_id)
    except ValueError:
        parser.error("wallet IDs must be valid UUIDs")
    if args.source_wallet_id == args.target_wallet_id:
        parser.error("source and target wallet IDs must differ")
    if not args.base_url.startswith(("http://", "https://")):
        parser.error("--base-url must start with http:// or https://")

    endpoint = f"{args.base_url.rstrip('/')}/api/v1/transfers"
    body = json.dumps(
        {
            "sourceWalletId": args.source_wallet_id,
            "targetWalletId": args.target_wallet_id,
            "amountMinor": args.amount_minor,
            "currency": args.currency,
        }
    ).encode("utf-8")
    try:
        return run(args, endpoint, body)
    except KeyboardInterrupt:
        print("\nInterrupted; already accepted transfers continue processing.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
