#!/usr/bin/env python3

import json
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse


MODE = "normal"
TOKEN = os.environ.get("FINNHUB_STUB_TOKEN", "ci-finnhub-dummy-token")


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json(200, {"status": "UP", "mode": MODE})
            return
        if parsed.path != "/quote":
            self.send_json(404, {"error": "not found"})
            return
        if self.headers.get("X-Finnhub-Token") != TOKEN:
            self.send_json(401, {"error": "invalid dummy token"})
            return
        symbol = parse_qs(parsed.query).get("symbol", [""])[0]
        if not symbol:
            self.send_json(400, {"error": "symbol is required"})
            return
        if MODE == "timeout":
            time.sleep(5)
            return
        if MODE == "server_error":
            self.send_json(500, {"error": "stub server outage"})
            return
        if MODE == "rate_limit":
            self.send_json(429, {"error": "stub rate limit"})
            return
        self.send_json(
            200,
            {
                "c": 123.456789,
                "pc": 120.000000,
                "t": int(time.time()),
            },
        )
# 处理来自客户端的 POST 请求，并根据请求的 mode 返回相应状态
    def do_POST(self):
        global MODE
        parsed = urlparse(self.path)
        if parsed.path != "/__control":
            self.send_json(404, {"error": "not found"})
            return
        requested = parse_qs(parsed.query).get("mode", [""])[0]
        if requested not in {"normal", "server_error", "rate_limit", "timeout"}:
            self.send_json(400, {"error": "invalid mode"})
            return
        MODE = requested
        self.send_json(200, {"mode": MODE})
# 将字典格式的数据转换为 JSON 字符串并发送给客户端
    def send_json(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        except BrokenPipeError:
            pass

    def log_message(self, _format, *_args):
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", 8080), Handler).serve_forever()
