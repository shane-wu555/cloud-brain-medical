import json
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any

from .config import AiSettings


class LlmError(RuntimeError):
    def __init__(
        self,
        message: str,
        *,
        kind: str = "llm_error",
        status_code: int = 502,
        provider_status: int | None = None,
    ):
        super().__init__(message)
        self.kind = kind
        self.status_code = status_code
        self.provider_status = provider_status


@dataclass(frozen=True)
class LlmResult:
    content: str
    provider: str
    model: str


def chat_json(settings: AiSettings, system_prompt: str, user_payload: dict[str, Any]) -> LlmResult:
    if not settings.llm_enabled:
        raise LlmError(
            "LLM provider is not enabled. Set AI_PROVIDER=openai_compatible and provide AI_OPENAI_API_KEY.",
            kind="provider_not_enabled",
            status_code=503,
        )

    body = {
        "model": settings.openai_model,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": json.dumps(user_payload, ensure_ascii=False, separators=(",", ":")),
            },
        ],
        "temperature": 0.2,
        "response_format": {"type": "json_object"},
    }
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        f"{settings.openai_base_url}/chat/completions",
        data=data,
        headers={
            "Authorization": f"Bearer {settings.openai_api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=settings.timeout_seconds) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        raise _http_error(exc) from exc
    except TimeoutError as exc:
        raise LlmError(
            f"LLM request timed out after {settings.timeout_seconds:g}s.",
            kind="timeout",
            status_code=504,
        ) from exc
    except urllib.error.URLError as exc:
        raise LlmError(
            f"LLM endpoint is unreachable: {exc.reason}",
            kind="network_error",
            status_code=502,
        ) from exc
    except json.JSONDecodeError as exc:
        raise LlmError(
            "LLM provider returned a non-JSON response.",
            kind="invalid_provider_response",
            status_code=502,
        ) from exc

    try:
        content = payload["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError) as exc:
        raise LlmError(
            "LLM response missing choices[0].message.content.",
            kind="invalid_provider_response",
            status_code=502,
        ) from exc
    if not isinstance(content, str) or not content.strip():
        raise LlmError(
            "LLM response content is empty.",
            kind="invalid_provider_response",
            status_code=502,
        )
    return LlmResult(content=content, provider=settings.provider, model=settings.openai_model)


def invalid_llm_output(exc: Exception) -> LlmError:
    return LlmError(
        "LLM output is not valid JSON or does not match the expected schema.",
        kind="invalid_llm_output",
        status_code=502,
    )


def _http_error(exc: urllib.error.HTTPError) -> LlmError:
    message_by_status = {
        400: "LLM provider rejected the request body or model parameters.",
        401: "LLM API key is invalid or missing.",
        403: "LLM API key does not have permission to call this model or workspace.",
        404: "LLM endpoint or model was not found. Check AI_OPENAI_BASE_URL and AI_OPENAI_MODEL.",
        408: "LLM provider timed out while processing the request.",
        429: "LLM provider rate limit or quota was exceeded.",
    }
    status = exc.code
    if status >= 500:
        message = "LLM provider is temporarily unavailable."
    else:
        message = message_by_status.get(status, f"LLM provider returned HTTP {status}.")
    app_status = 504 if status == 408 else 429 if status == 429 else 502
    return LlmError(
        message,
        kind=f"http_{status}",
        status_code=app_status,
        provider_status=status,
    )
