#!/usr/bin/env python3
"""Dependency-free concurrent HTTP smoke probe.

Usage: python scripts/load-smoke.py URL [requests] [concurrency] [max-p95-ms] [max-p99-ms]
Optional latency bounds make this usable as a conservative CI smoke gate; use the k6
profile for the production qualification arrival-rate target.
"""
from __future__ import annotations

import concurrent.futures
import math
import sys
import time
from urllib.request import Request, urlopen


def one(url: str) -> tuple[int, float, str | None]:
    started = time.perf_counter()
    try:
        with urlopen(Request(url, headers={"User-Agent": "uep-qualification/0.15"}), timeout=10) as response:
            response.read(4096)
            return response.status, (time.perf_counter() - started) * 1000, None
    except Exception as error:  # urllib raises for connection errors and HTTP error statuses
        return 0, (time.perf_counter() - started) * 1000, str(error)


def percentile(sorted_values: list[float], quantile: float) -> float:
    """Nearest-rank percentile, returning the rank ceil(quantile * n)."""
    if not sorted_values:
        raise ValueError("percentile requires at least one sample")
    rank = max(1, math.ceil(quantile * len(sorted_values)))
    return sorted_values[min(len(sorted_values) - 1, rank - 1)]


def main(argv: list[str]) -> int:
    if len(argv) > 6:
        print(__doc__, file=sys.stderr)
        return 2

    url = argv[1] if len(argv) > 1 else "http://localhost:8080/actuator/health"
    try:
        count = int(argv[2]) if len(argv) > 2 else 50
        workers = int(argv[3]) if len(argv) > 3 else min(10, count)
        max_p95 = float(argv[4]) if len(argv) > 4 else None
        max_p99 = float(argv[5]) if len(argv) > 5 else None
    except ValueError:
        print("requests/concurrency must be integers and latency bounds must be numbers", file=sys.stderr)
        return 2

    if count < 1 or workers < 1:
        print("requests and concurrency must both be greater than zero", file=sys.stderr)
        return 2
    if any(bound is not None and (not math.isfinite(bound) or bound <= 0) for bound in (max_p95, max_p99)):
        print("latency bounds must be finite positive milliseconds", file=sys.stderr)
        return 2

    started = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        rows = list(executor.map(one, [url] * count))

    elapsed = (time.perf_counter() - started) * 1000
    samples = sorted(row[1] for row in rows)
    successes = sum(1 for status, _, _ in rows if 200 <= status < 400)
    failures = count - successes
    p95 = percentile(samples, 0.95)
    p99 = percentile(samples, 0.99)
    maximum = samples[-1]
    print(
        f"requests={count} concurrency={workers} ok={successes} errors={failures} "
        f"elapsed_ms={elapsed:.1f} p95_ms={p95:.1f} p99_ms={p99:.1f} max_ms={maximum:.1f}"
    )

    if failures:
        return 2
    exceeded = []
    if max_p95 is not None and p95 > max_p95:
        exceeded.append(f"p95 {p95:.1f}ms exceeds {max_p95:.1f}ms")
    if max_p99 is not None and p99 > max_p99:
        exceeded.append(f"p99 {p99:.1f}ms exceeds {max_p99:.1f}ms")
    if exceeded:
        print("latency threshold failed: " + "; ".join(exceeded), file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
