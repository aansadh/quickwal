#!/usr/bin/env python3
"""Sample PostgreSQL lock waits and outbox depth during a local load run.

Each sample opens a short psql process through docker exec. Sampling itself adds
a small amount of inspection work; the interval and any missed short waits must
be included when interpreting results.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time


QUERY = """
SELECT json_build_object(
  'timestamp', clock_timestamp(),
  'lock_waiters', (SELECT count(*) FROM pg_stat_activity
                   WHERE datname = current_database() AND wait_event_type = 'Lock'),
  'active_sessions', (SELECT count(*) FROM pg_stat_activity
                      WHERE datname = current_database() AND state = 'active'),
  'outbox_rows', (SELECT count(*) FROM outbox_events),
  'oldest_outbox_age_ms', COALESCE(
      (SELECT (extract(epoch FROM (clock_timestamp() - min(created_at))) * 1000)::bigint
       FROM outbox_events), 0),
  'deadlocks', (SELECT deadlocks FROM pg_stat_database
                WHERE datname = current_database())
);
"""


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=float, required=True, help="Sampling duration in seconds")
    parser.add_argument("--interval", type=float, default=1.0, help="Seconds between read-only snapshots")
    parser.add_argument("--container", default="quickwal-db")
    parser.add_argument("--database", default="quickwal")
    parser.add_argument("--user", default="quickwal")
    parser.add_argument("--emit-samples", action="store_true", help="Print every JSON snapshot as well as the summary")
    args = parser.parse_args()
    if args.duration <= 0 or args.interval <= 0:
        parser.error("--duration and --interval must be positive")

    print(
        f"Sampling PostgreSQL for {args.duration:g}s at {args.interval:g}s intervals; "
        "read-only snapshots add small inspection overhead and can miss brief waits.",
        flush=True,
    )
    deadline = time.monotonic() + args.duration
    next_sample = time.monotonic()
    sample_count = 0
    lock_wait_samples = 0
    max_lock_waiters = 0
    max_active_sessions = 0
    max_outbox_rows = 0
    max_oldest_outbox_age_ms = 0
    max_deadlocks = 0
    while next_sample < deadline:
        result = subprocess.run(
            [
                "docker", "exec", args.container,
                "psql", "-XAt", "-U", args.user, "-d", args.database,
                "-c", QUERY,
            ],
            capture_output=True,
            text=True,
            check=False,
        )
        if result.returncode:
            print(result.stderr.rstrip(), file=sys.stderr, flush=True)
            return result.returncode
        sample = json.loads(result.stdout)
        sample_count += 1
        lock_waiters = int(sample["lock_waiters"])
        active_sessions = int(sample["active_sessions"])
        outbox_rows = int(sample["outbox_rows"])
        oldest_age_ms = int(sample["oldest_outbox_age_ms"])
        deadlocks = int(sample["deadlocks"])
        lock_wait_samples += lock_waiters > 0
        max_lock_waiters = max(max_lock_waiters, lock_waiters)
        max_active_sessions = max(max_active_sessions, active_sessions)
        max_outbox_rows = max(max_outbox_rows, outbox_rows)
        max_oldest_outbox_age_ms = max(max_oldest_outbox_age_ms, oldest_age_ms)
        max_deadlocks = max(max_deadlocks, deadlocks)
        if args.emit_samples:
            print(json.dumps(sample, sort_keys=True), flush=True)
        next_sample += args.interval
        delay = next_sample - time.monotonic()
        if delay > 0:
            time.sleep(delay)
    print(
        json.dumps(
            {
                "samples": sample_count,
                "lock_wait_samples": lock_wait_samples,
                "max_lock_waiters": max_lock_waiters,
                "max_active_sessions": max_active_sessions,
                "max_outbox_rows": max_outbox_rows,
                "max_oldest_outbox_age_ms": max_oldest_outbox_age_ms,
                "deadlocks_counter_max": max_deadlocks,
                "note": "sampled snapshots; waits shorter than interval may be missed",
            },
            sort_keys=True,
        ),
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
