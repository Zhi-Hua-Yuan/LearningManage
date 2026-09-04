#!/usr/bin/env python3

"""Deterministic, CI-only OpenAI-compatible response stub.

This process deliberately logs only the request path and status. It never logs
headers, request bodies, prompts, or model responses.
"""

import json
import logging
import os
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = os.environ.get("AI_STUB_HOST", "0.0.0.0")
PORT = int(os.environ.get("AI_STUB_PORT", "8080"))
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
    server_version = "LearningManageCiAiStub/2"

    def _send_json(self, status, body, request_id="ci-ai-stub-request"):
        encoded = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("X-Request-ID", request_id)
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _send_raw(self, status, body):
        encoded = body.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _completion(self, content, finish_reason="stop", tool_calls=None, usage=None):
        message = {"role": "assistant", "content": content}
        if tool_calls is not None:
            message["tool_calls"] = tool_calls
        response = {
            "id": "ci-ai-stub-response",
            "object": "chat.completion",
            "model": "ci-ai-stub",
            "choices": [{"index": 0, "message": message, "finish_reason": finish_reason}],
        }
        if usage is not None:
            response["usage"] = usage
        return response

    def _tool_call(self, index, call_id, name, arguments):
        return {
            "index": index,
            "id": call_id,
            "type": "function",
            "function": {"name": name, "arguments": arguments},
        }

    def _valid_tool_round_trip(self, messages):
        if not isinstance(messages, list) or len(messages) < 2:
            return False
        assistant = messages[-2]
        tool_message = messages[-1]
        if not isinstance(assistant, dict) or not isinstance(tool_message, dict):
            return False
        if assistant.get("role") != "assistant" or tool_message.get("role") != "tool":
            return False
        tool_calls = assistant.get("tool_calls")
        if not isinstance(tool_calls, list) or not tool_calls:
            return False
        for index, tool_call in enumerate(tool_calls):
            if not isinstance(tool_call, dict) or tool_call.get("index") != index:
                return False
        return tool_message.get("tool_call_id") in {
            tool_call.get("id") for tool_call in tool_calls
        }

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
            request = json.loads(self.rfile.read(content_length).decode("utf-8"))
        except (TypeError, ValueError, UnicodeDecodeError, json.JSONDecodeError):
            self._send_json(400, {"error": "invalid_request"})
            return

        if not isinstance(request, dict):
            self._send_json(400, {"error": "invalid_request"})
            return

        model = request.get("model", "")
        if model.startswith("stub-status-"):
            try:
                status = int(model.removeprefix("stub-status-"))
            except ValueError:
                status = 500
            self._send_json(status, {"error": {"type": "stub_error", "status": status}})
            return
        if model == "stub-timeout":
            time.sleep(6)
        if model == "stub-invalid-json":
            self._send_raw(200, "{invalid-json")
            return
        if model == "stub-missing-choices":
            self._send_json(200, {"id": "ci-ai-stub-missing-choices"})
            return
        if model == "stub-empty":
            self._send_json(200, self._completion(None))
            return
        if model == "stub-invalid-arguments":
            call = self._tool_call(0, "call-invalid", "query_tasks", "not-json")
            self._send_json(200, self._completion(None, "tool_calls", [call]))
            return
        messages = request.get("messages") or []
        if model == "stub-tool-call" and messages and isinstance(messages[-1], dict) \
                and messages[-1].get("role") == "tool":
            if not self._valid_tool_round_trip(messages):
                self._send_json(400, {"error": "invalid_tool_round_trip"})
                return
            self._send_json(200, self._completion("工具结果已分析"))
            return
        if model == "stub-tool-result":
            self._send_json(200, self._completion("工具结果已分析"))
            return
        if model == "stub-tool-call":
            call = self._tool_call(0, "call-1", "query_tasks", '{"projectId":1001}')
            self._send_json(200, self._completion(None, "tool_calls", [call]))
            return
        if model == "stub-multi-tool-calls":
            calls = [
                self._tool_call(0, "call-1", "query_tasks", '{"projectId":1001}'),
                self._tool_call(1, "call-2", "query_stats", '{"projectId":1001}'),
            ]
            self._send_json(200, self._completion(None, "tool_calls", calls))
            return
        if model == "stub-text":
            self._send_json(200, self._completion("普通文本结果"))
            return
        if model == "stub-usage":
            usage = {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}
            self._send_json(200, self._completion("包含用量的文本结果", usage=usage))
            return
        if model == "stub-missing-usage":
            self._send_json(200, self._completion("未返回用量"))
            return

        self._send_json(200, self._completion(RESPONSE_CONTENT))

    def log_message(self, fmt, *args):
        logging.info("request=%s status=%s", self.path, args[1] if len(args) > 1 else "unknown")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="ai-stub %(message)s")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
