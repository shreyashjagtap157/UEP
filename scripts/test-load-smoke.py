#!/usr/bin/env python3
"""Unit tests for the dependency-free load smoke probe."""
from __future__ import annotations

import contextlib
import importlib.util
import io
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("uep_load_smoke", ROOT / "scripts/load-smoke.py")
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("could not load scripts/load-smoke.py")
load_smoke = importlib.util.module_from_spec(SPEC)
import sys
sys.modules[SPEC.name] = load_smoke
SPEC.loader.exec_module(load_smoke)


class SmokeHandler(BaseHTTPRequestHandler):
    status_code = 200

    def do_GET(self):
        self.send_response(self.status_code)
        self.send_header("Content-Type", "text/plain")
        self.end_headers()
        self.wfile.write(b"smoke")

    def log_message(self, _format, *_args):
        return


class LoadSmokeTests(unittest.TestCase):
    def test_nearest_rank_percentiles(self):
        values = [1.0, 2.0, 3.0, 4.0, 5.0]
        self.assertEqual(load_smoke.percentile(values, 0.50), 3.0)
        self.assertEqual(load_smoke.percentile(values, 0.95), 5.0)
        self.assertEqual(load_smoke.percentile(values, 0.99), 5.0)

    def test_probe_passes_for_healthy_local_endpoint(self):
        with self._server(200) as url, contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(load_smoke.main(["load-smoke.py", url, "6", "3", "1000", "1000"]), 0)

    def test_probe_fails_when_optional_latency_bound_is_exceeded(self):
        with self._server(200) as url, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(load_smoke.main(["load-smoke.py", url, "4", "2", "0.000000001"]), 3)

    def test_probe_fails_for_http_errors(self):
        with self._server(500) as url, contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(load_smoke.main(["load-smoke.py", url, "3", "2"]), 2)

    def test_probe_rejects_zero_requests_and_nonpositive_thresholds(self):
        with contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(load_smoke.main(["load-smoke.py", "http://127.0.0.1", "0", "1"]), 2)
            self.assertEqual(load_smoke.main(["load-smoke.py", "http://127.0.0.1", "1", "1", "0"]), 2)

    @contextlib.contextmanager
    def _server(self, status_code: int):
        handler = type(f"Status{status_code}Handler", (SmokeHandler,), {"status_code": status_code})
        server = ThreadingHTTPServer(("127.0.0.1", 0), handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            yield f"http://127.0.0.1:{server.server_port}/health"
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main(verbosity=2)
