#!/usr/bin/env python3

"""Deterministic, CI-only OpenAI-compatible response stub.

This process deliberately logs only the request path and status. It never logs
headers, request bodies, prompts, or model responses.
"""

import json
import logging
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = "0.0.0.0"
PORT = 8080
RESPONSE_CONTENT = json.dumps(
    [
        {
            "name": "阶段一：基础准备",
            "tasks": [
                {"name": "完成目标拆解", "priority": 2, "dueDate": "2099-01-15"},
                {"name": "建立执行清单", "priority": 1, "dueDate": "2099-01-31"},
            ],
        },
        {
            "name": "阶段二：持续执行",
            "tasks": [
                {"name": "完成阶段检查", "priority": 2, "dueDate": "2099-02-15"},
                {"name": "提交阶段复盘", "priority": 3, "dueDate": "2099-02-28"},
            ],
        },
    ],
    ensure_ascii=False,
    separators=(",", ":"),
)


class Handler(BaseHTTPRequestHandler):
    server_version = "LearningManageCiAiStub/1"

    def _send_json(self, status, body):
        encoded = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self):  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path == "/health":
            self._send_json(200, {"status": "ok"})
            return
        self._send_json(404, {"error": "not_found"})

    def do_POST(self):  # noqa: N802 - BaseHTTPRequestHandler API
        if self.path != "/compatible-mode/v1/chat/completions":
            self._send_json(404, {"error": "not_found"})
            return

        try:
            content_length = int(self.headers.get("Content-Length", "0"))
            self.rfile.read(content_length)
        except (TypeError, ValueError):
            self._send_json(400, {"error": "invalid_content_length"})
            return

        self._send_json(
            200,
            {
                "id": "ci-ai-stub-response",
                "object": "chat.completion",
                "model": "ci-ai-stub",
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": RESPONSE_CONTENT},
                        "finish_reason": "stop",
                    }
                ],
            },
        )

    def log_message(self, fmt, *args):
        logging.info("request=%s status=%s", self.path, args[1] if len(args) > 1 else "unknown")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="ai-stub %(message)s")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
