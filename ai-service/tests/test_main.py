import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient
from groq import RateLimitError, AuthenticationError, APIConnectionError, BadRequestError

from app.main import app, groq_service
from app.services.groq_service import GroqService

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/api/ai/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "UP"
    assert data["service"] == "ContextClip AI Service"
    assert "configured" in data


def test_generate_missing_prompt_body():
    response = client.post("/api/ai/generate", json={})
    assert response.status_code == 422


def test_generate_empty_prompt():
    response = client.post("/api/ai/generate", json={"prompt": ""})
    assert response.status_code == 400
    assert "Prompt cannot be empty or blank" in response.json()["detail"]


def test_generate_blank_whitespace_prompt():
    response = client.post("/api/ai/generate", json={"prompt": "   \n\t   "})
    assert response.status_code == 400
    assert "Prompt cannot be empty or blank" in response.json()["detail"]


def test_generate_prompt_exceeds_max_length():
    huge_prompt = "a" * 10001
    response = client.post("/api/ai/generate", json={"prompt": huge_prompt})
    assert response.status_code == 400
    assert "exceeds maximum allowed length" in response.json()["detail"]


def test_generate_success_mocked():
    with patch.object(groq_service, "generate_response", return_value="A HashMap is a key-value data structure in Java."):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 200
        assert response.json() == {
            "response": "A HashMap is a key-value data structure in Java."
        }


def test_generate_missing_api_key_handled():
    with patch.object(groq_service, "generate_response", side_effect=ValueError("GROQ_API_KEY environment variable is not set")):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 500
        assert "GROQ_API_KEY" in response.json()["detail"]


def test_generate_rate_limit_handled():
    # Construct a mock RateLimitError
    mock_response = MagicMock()
    mock_response.status_code = 429
    error = RateLimitError("Rate limit exceeded", response=mock_response, body=None)
    with patch.object(groq_service, "generate_response", side_effect=error):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 429
        assert "rate limit reached" in response.json()["detail"].lower()


def test_generate_authentication_error_handled():
    mock_response = MagicMock()
    mock_response.status_code = 401
    error = AuthenticationError("Invalid API key", response=mock_response, body=None)
    with patch.object(groq_service, "generate_response", side_effect=error):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 401
        assert "authentication failed" in response.json()["detail"].lower()


def test_groq_service_missing_key_unit():
    service = GroqService(api_key="")
    with pytest.raises(ValueError, match="GROQ_API_KEY"):
        service.generate_response("Test prompt")


def test_groq_service_mocked_completion_unit():
    service = GroqService(api_key="mock-groq-key", model="llama-3.3-70b-versatile")

    mock_choice = MagicMock()
    mock_choice.message.content = "Mocked explanation from Groq"
    mock_completion = MagicMock()
    mock_completion.choices = [mock_choice]

    with patch("app.services.groq_service.Groq") as mock_groq_cls:
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = mock_completion
        mock_groq_cls.return_value = mock_client

        result = service.generate_response("Explain Spring Boot")
        assert result == "Mocked explanation from Groq"
        mock_client.chat.completions.create.assert_called_once_with(
            messages=[{"role": "user", "content": "Explain Spring Boot"}],
            model="llama-3.3-70b-versatile",
            temperature=0.2
        )
