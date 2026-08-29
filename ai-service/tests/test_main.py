import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient

from app.main import app, openai_service
from app.services.openai_service import OpenAIService

client = TestClient(app)


def test_health_endpoint():
    response = client.get("/api/ai/health")
    assert response.status_code == 200
    assert response.json() == {
        "status": "UP",
        "service": "ContextClip AI Service"
    }


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
    with patch.object(openai_service, "generate_response", return_value="A HashMap is a key-value data structure in Java."):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 200
        assert response.json() == {
            "response": "A HashMap is a key-value data structure in Java."
        }


def test_generate_missing_api_key_handled():
    with patch.object(openai_service, "generate_response", side_effect=ValueError("OPENAI_API_KEY environment variable is not set")):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 500
        assert "OPENAI_API_KEY" in response.json()["detail"]


def test_generate_openai_api_error_handled():
    with patch.object(openai_service, "generate_response", side_effect=RuntimeError("OpenAI API error: Rate limit reached")):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 502
        assert "OpenAI API error" in response.json()["detail"]


def test_openai_service_missing_key_unit():
    service = OpenAIService(api_key="")
    with pytest.raises(ValueError, match="OPENAI_API_KEY"):
        service.generate_response("Test prompt")


def test_openai_service_mocked_client_unit():
    service = OpenAIService(api_key="mock-key-123", model="gpt-4o-mini")
    
    mock_choice = MagicMock()
    mock_choice.message.content = "Mocked explanation response"
    mock_response = MagicMock()
    mock_response.choices = [mock_choice]

    with patch("app.services.openai_service.OpenAI") as mock_openai_cls:
        mock_client = MagicMock()
        mock_client.chat.completions.create.return_value = mock_response
        mock_openai_cls.return_value = mock_client

        result = service.generate_response("Explain Spring Boot")
        assert result == "Mocked explanation response"
        mock_client.chat.completions.create.assert_called_once_with(
            model="gpt-4o-mini",
            messages=[
                {
                    "role": "system",
                    "content": "You are a helpful assistant for the ContextClip clipboard knowledge system."
                },
                {
                    "role": "user",
                    "content": "Explain Spring Boot"
                }
            ]
        )
