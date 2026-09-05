#!/usr/bin/env python3

"""Deterministic, CI-only OpenAI-compatible response stub.

This process deliberately logs only the request path and status. It never logs
headers, request bodies, prompts, or model responses.
"""

import json
import logging
import os
import re
import time
from datetime import date, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


HOST = os.environ.get("AI_STUB_HOST", "0.0.0.0")
PORT = int(os.environ.get("AI_STUB_PORT", "8080"))
USAGE = {"prompt_tokens": 120, "completion_tokens": 80, "total_tokens": 200}


def compact_json(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def message_text(messages):
    return "\n".join(
        str(message.get("content") or "")
        for message in messages
        if isinstance(message, dict)
    )


def stage3_fault(text):
    match = re.search(r"\[\[STAGE3_STUB:([a-z0-9-]+)\]\]", text)
    return match.group(1) if match else None


def stage3_scene(text):
    if "任务智能重排助手" in text:
        return "list-replan"
    if "任务命名优化助手" in text:
        return "daily-rename"
    if "任务调度助手" in text:
        return "today-order"
    if "周复盘总结" in text:
        return "weekly-polish"
    return "task-breakdown"


def task_ids(text, pending_only=False):
    source = text
    if pending_only and "未完成任务(JSON):" in source:
        source = source.rsplit("未完成任务(JSON):", 1)[1]
    elif "任务列表(JSON)：" in source:
        source = source.rsplit("任务列表(JSON)：", 1)[1]
    elif "任务列表(JSON):" in source:
        source = source.rsplit("任务列表(JSON):", 1)[1]
    ids = []
    for value in re.findall(r'"taskId"\s*:\s*(\d+)', source):
        parsed = int(value)
        if parsed not in ids:
            ids.append(parsed)
    return ids


def breakdown_content(text, detailed=False):
    milestone_count = 3
    task_count = 4 if detailed else 3
    today_match = re.search(r"今天日期(?:（含）)?：(\d{4}-\d{2}-\d{2})", text)
    end_match = re.search(r"最晚截止日期(?:（含）)?：(\d{4}-\d{2}-\d{2})", text)
    duration_match = re.search(r"(?:原始)?周期：([^，。]+)", text)
    start = date.fromisoformat(today_match.group(1)) if today_match else date.today()
    duration = duration_match.group(1) if duration_match else "1个月"
    amount_match = re.search(r"(\d+)", duration)
    amount = int(amount_match.group(1)) if amount_match else 1
    planning_end = date.fromisoformat(end_match.group(1)) if end_match else None
    total_days = (planning_end - start).days if planning_end else amount * (7 if "周" in duration else 30)
    total_tasks = milestone_count * task_count
    milestones = []
    for milestone_index in range(milestone_count):
        tasks = []
        for task_index in range(task_count):
            tasks.append({
                "name": f"完成阶段{milestone_index + 1}执行项{task_index + 1}",
                "priority": (task_index % 3) + 1,
                "dueDate": (start + timedelta(days=max(1, total_days * (milestone_index * task_count + task_index + 1) // total_tasks))).isoformat(),
            })
        milestones.append({"name": f"阶段{milestone_index + 1}：计划与交付", "tasks": tasks})
    return compact_json(milestones)


def valid_stage3_content(scene, text):
    if scene == "weekly-polish":
        review = (
            "本周围绕既定目标完成了主要任务，并根据任务记录形成了可核对的阶段成果。"
            "推进过程中能够及时识别优先事项，关键进展总体符合计划。"
            "目前主要问题是部分工作估时偏乐观，测试与复盘安排相对靠后，导致收尾压力增加。"
            "下周将提前明确验收条件，减少并行事项，按优先级逐项完成并及时记录结果。"
        )
        return compact_json({"review": review})
    if scene == "today-order":
        ids = task_ids(text)
        return compact_json({
            "strategy": "balanced",
            "items": [
                {
                    "taskId": task_id,
                    "difficulty": 3,
                    "cost": 2,
                    "benefit": 4,
                    "estimatedMinutes": 45,
                    "reason": "综合截止时间、优先级和完成收益排序",
                }
                for task_id in ids
            ],
        })
    if scene == "daily-rename":
        ids = task_ids(text, pending_only=True)
        return compact_json({
            "items": [
                {
                    "taskId": task_id,
                    "newTitle": f"完成任务{task_id}并记录验收结果",
                    "reason": "补充动作和可验收产出",
                    "confidence": 88,
                }
                for task_id in ids
            ]
        })
    if scene == "list-replan":
        ids = task_ids(text, pending_only=True)
        return compact_json({
            "items": [
                {
                    "taskId": task_id,
                    "newTitle": f"完成任务{task_id}并提交结果",
                    "newPriority": 2,
                    "newDueDate": None,
                    "confidence": 86,
                    "reason": "结合当前完成进度调整执行顺序，截止日期保持不变",
                }
                for task_id in ids
            ]
        })
    return breakdown_content(text, "细颗粒度" in text)


def fault_content(scene, fault, text, valid_content):
    if fault == "invalid-json":
        return "{invalid-json"
    if fault == "invalid-structure":
        return "{}" if scene != "task-breakdown" else "[]"
    if fault == "markdown-wrapped":
        return f"```json\n{valid_content}\n```"
    ids = task_ids(text, pending_only=scene in {"daily-rename", "list-replan"})
    first_id = ids[0] if ids else 930001
    if fault == "unknown-id" and scene == "today-order":
        return compact_json({"strategy": "balanced", "items": [{
            "taskId": 99999999, "difficulty": 3, "cost": 2,
            "benefit": 4, "estimatedMinutes": 45, "reason": "故障注入",
        }]})
    if fault == "duplicate-id" and scene == "today-order":
        return compact_json({"strategy": "balanced", "items": [{
            "taskId": first_id, "difficulty": 3, "cost": 2,
            "benefit": 4, "estimatedMinutes": 45, "reason": "故障注入",
        }, {
            "taskId": first_id, "difficulty": 2, "cost": 2,
            "benefit": 3, "estimatedMinutes": 30, "reason": "故障注入",
        }]})
    if fault == "unauthorized-id" and scene == "daily-rename":
        return compact_json({"items": [{
            "taskId": 930201, "newTitle": "不应被放行的任务", "reason": "故障注入", "confidence": 99,
        }]})
    if fault == "overlong-title" and scene == "daily-rename":
        return compact_json({"items": [{
            "taskId": first_id, "newTitle": "超长标题" * 30, "reason": "故障注入", "confidence": 80,
        }]})
    if fault == "completed-id" and scene == "list-replan":
        return compact_json({"items": [{
            "taskId": 930005, "newTitle": "不应重排已完成任务", "newPriority": 3,
            "newDueDate": None, "confidence": 99, "reason": "故障注入",
        }]})
    if fault == "invalid-date" and scene == "list-replan":
        return compact_json({"items": [{
            "taskId": first_id, "newTitle": f"完成任务{first_id}", "newPriority": 2,
            "newDueDate": "not-a-date", "confidence": 80, "reason": "故障注入",
        }]})
    return valid_content


class Handler(BaseHTTPRequestHandler):
    server_version = "LearningManageCiAiStub/3"

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

        text = message_text(messages)
        fault = stage3_fault(text)
        if fault == "http-429":
            self._send_json(429, {"error": {"type": "rate_limit", "status": 429}})
            return
        if fault == "http-500":
            self._send_json(500, {"error": {"type": "server_error", "status": 500}})
            return
        if fault == "timeout":
            time.sleep(6)
        if fault == "missing-choices":
            self._send_json(200, {"id": "stage3-missing-choices", "model": "ci-ai-stub"})
            return
        if fault == "empty":
            self._send_json(200, self._completion(None, usage=USAGE))
            return

        scene = stage3_scene(text)
        valid_content = valid_stage3_content(scene, text)
        content = fault_content(scene, fault, text, valid_content)
        usage = None if fault == "missing-usage" else USAGE
        self._send_json(200, self._completion(content, usage=usage))

    def log_message(self, fmt, *args):
        logging.info("request=%s status=%s", self.path, args[1] if len(args) > 1 else "unknown")


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO, format="ai-stub %(message)s")
    ThreadingHTTPServer((HOST, PORT), Handler).serve_forever()
