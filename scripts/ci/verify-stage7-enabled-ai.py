#!/usr/bin/env python3
"""Exercise enabled RAG and Agent paths without emitting credentials or bodies."""

import json
import time
import urllib.error
import urllib.request
import uuid


BASE_URL = "http://backend:8123/api"
TERMINAL_AGENT_STATES = {"SUCCEEDED", "PARTIAL", "FAILED", "TIMED_OUT", "CANCELED"}


def request(path, method="GET", payload=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            body = json.load(error)
        except (json.JSONDecodeError, UnicodeDecodeError):
            body = {"code": error.code}
        return error.code, body


def require_success(path, method="GET", payload=None, token=None):
    status, body = request(path, method, payload, token)
    if status < 200 or status >= 300 or body.get("code") != 0:
        raise RuntimeError(
            f"Stage 7 enabled-AI smoke failed at {path}; http={status}; code={body.get('code')}"
        )
    return body.get("data")


def wait_for_rag(project_id, token, question):
    deadline = time.monotonic() + 90
    last_code = None
    while time.monotonic() < deadline:
        status, body = request(
            "/ai/rag/ask",
            "POST",
            {"projectId": project_id, "question": question},
            token,
        )
        last_code = body.get("code")
        data = body.get("data") or {}
        if status == 200 and last_code == 0 and data.get("sources"):
            return
        time.sleep(1)
    raise RuntimeError(f"Stage 7 RAG smoke did not return a source; lastCode={last_code}")


def wait_for_agent(project_id, token, client_request_id):
    submitted = require_success(
        "/ai/agent/project-risk",
        "POST",
        {"projectId": project_id, "clientRequestId": client_request_id},
        token,
    )
    run_id = str((submitted or {}).get("runId") or "")
    if not run_id:
        raise RuntimeError("Stage 7 Agent smoke did not return runId")
    deadline = time.monotonic() + 75
    while time.monotonic() < deadline:
        run = require_success(f"/ai/agent/run/{run_id}", token=token) or {}
        status = run.get("status")
        if status in TERMINAL_AGENT_STATES:
            if status != "SUCCEEDED" or not run.get("draftId"):
                raise RuntimeError(f"Stage 7 Agent smoke terminal state is {status}")
            return
        time.sleep(1)
    raise RuntimeError("Stage 7 Agent smoke timed out")


def verify_metric_tags():
    allowed = {
        "scene",
        "model",
        "status",
        "failure_type",
        "degraded",
        "orchestration_mode",
        "tool_name",
        "source_type",
        "currency",
        "price_version",
        # Prometheus adds `le` to histogram buckets; application code cannot set it.
        "le",
    }
    with urllib.request.urlopen(
        "http://backend:9123/actuator/prometheus", timeout=15
    ) as response:
        metrics = response.read().decode("utf-8")
    required = (
        "learning_rag_queries_total",
        "learning_agent_runs_total",
        "learning_ai_tokens_total",
    )
    if any(name not in metrics for name in required):
        raise RuntimeError("Stage 7 enabled-AI metrics are incomplete")
    for line in metrics.splitlines():
        if not line.startswith("learning_") or "{" not in line:
            continue
        labels = line.split("{", 1)[1].split("}", 1)[0]
        for entry in labels.split(","):
            key = entry.split("=", 1)[0]
            if key and key not in allowed:
                raise RuntimeError(f"Stage 7 metric tag is not allow-listed: {key}")


def main():
    suffix = uuid.uuid4().hex[:12]
    account = f"s7{suffix}"
    password = "stage7-smoke-password"
    require_success(
        "/user/register",
        "POST",
        {
            "account": account,
            "username": "Stage7 Smoke",
            "password": password,
            "confirmPassword": password,
        },
    )
    login = require_success(
        "/user/login", "POST", {"account": account, "password": password}
    )
    token = str((login or {}).get("token") or "")
    if not token:
        raise RuntimeError("Stage 7 smoke login returned no token")

    project = require_success(
        "/project/add",
        "POST",
        {
            "name": f"Stage7 enabled AI smoke {suffix}",
            "goal": "Validate RAG and Agent in the private observability stack",
        },
        token,
    )
    project_id = str(project.get("id") if isinstance(project, dict) else project)
    if not project_id.isdigit():
        raise RuntimeError("Stage 7 smoke project ID is invalid")
    phrase = f"stage seven observability evidence {suffix}"
    require_success(
        "/task/add",
        "POST",
        {
            "projectId": project_id,
            "title": phrase,
            "description": phrase,
            "priority": 2,
        },
        token,
    )

    wait_for_rag(project_id, token, phrase)
    wait_for_agent(project_id, token, f"stage7-smoke-{suffix}")
    verify_metric_tags()
    print("stage7.enabled_ai_smoke=PASS rag=PASS agent=PASS metric_tags=PASS")


if __name__ == "__main__":
    main()
