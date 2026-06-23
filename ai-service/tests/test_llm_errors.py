import json
import socket
import urllib.error

import pytest

from app.clinical_assistance.models import ClinicalAssistanceRequest
from app.clinical_assistance.service import assist
from app.core.config import settings
from app.core.llm import LlmError, chat_json


class FakeHttpError(urllib.error.HTTPError):
    def __init__(self, code: int):
        super().__init__("https://example.test/chat/completions", code, "error", {}, None)


def test_llm_http_401_reports_invalid_key(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai_compatible")
    monkeypatch.setenv("AI_OPENAI_API_KEY", "bad-key")
    monkeypatch.setenv("AI_OPENAI_BASE_URL", "https://example.test/v1")

    def fail(*_, **__):
        raise FakeHttpError(401)

    monkeypatch.setattr("urllib.request.urlopen", fail)

    with pytest.raises(LlmError) as exc:
        chat_json(settings(), "system", {"ping": "pong"})

    assert exc.value.kind == "http_401"
    assert "API key" in str(exc.value)


def test_llm_http_404_reports_model_or_endpoint(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai_compatible")
    monkeypatch.setenv("AI_OPENAI_API_KEY", "key")
    monkeypatch.setenv("AI_OPENAI_BASE_URL", "https://example.test/v1")

    def fail(*_, **__):
        raise FakeHttpError(404)

    monkeypatch.setattr("urllib.request.urlopen", fail)

    with pytest.raises(LlmError) as exc:
        chat_json(settings(), "system", {"ping": "pong"})

    assert exc.value.kind == "http_404"
    assert "AI_OPENAI_MODEL" in str(exc.value)


def test_llm_timeout_is_clear(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai_compatible")
    monkeypatch.setenv("AI_OPENAI_API_KEY", "key")
    monkeypatch.setenv("AI_OPENAI_BASE_URL", "https://example.test/v1")
    monkeypatch.setenv("AI_TIMEOUT_SECONDS", "1")

    def fail(*_, **__):
        raise socket.timeout("timed out")

    monkeypatch.setattr("urllib.request.urlopen", fail)

    with pytest.raises(LlmError) as exc:
        chat_json(settings(), "system", {"ping": "pong"})

    assert exc.value.kind == "timeout"
    assert exc.value.status_code == 504


def test_fallback_true_returns_mock_when_provider_fails(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai_compatible")
    monkeypatch.setenv("AI_OPENAI_API_KEY", "bad-key")
    monkeypatch.setenv("AI_OPENAI_BASE_URL", "https://example.test/v1")
    monkeypatch.setenv("AI_ALLOW_FALLBACK", "true")
    monkeypatch.delenv("AI_RAG_DATABASE_URL", raising=False)
    monkeypatch.delenv("DATABASE_URL", raising=False)

    def fail(*_, **__):
        raise FakeHttpError(401)

    monkeypatch.setattr("urllib.request.urlopen", fail)

    response = assist(
        ClinicalAssistanceRequest(
            appointmentId="appt-1",
            patientId="patient-1",
            chiefComplaint="头痛",
            presentIllness="2小时",
            prompt="给出建议",
        )
    )

    assert response.provider == "mock"
    assert response.fallback_used is True


def test_fallback_false_raises_clear_error(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "openai_compatible")
    monkeypatch.setenv("AI_OPENAI_API_KEY", "bad-key")
    monkeypatch.setenv("AI_OPENAI_BASE_URL", "https://example.test/v1")
    monkeypatch.setenv("AI_ALLOW_FALLBACK", "false")

    def fail(*_, **__):
        raise FakeHttpError(401)

    monkeypatch.setattr("urllib.request.urlopen", fail)

    with pytest.raises(LlmError) as exc:
        assist(
            ClinicalAssistanceRequest(
                appointmentId="appt-1",
                patientId="patient-1",
                chiefComplaint="头痛",
                presentIllness="2小时",
                prompt="给出建议",
            )
        )

    assert exc.value.kind == "http_401"
