import pytest
from unittest.mock import MagicMock, patch
from fastapi.testclient import TestClient

from app.main import app, gemini_service
from app.services.gemini_service import GeminiService

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
    with patch.object(gemini_service, "generate_response", return_value="A HashMap is a key-value data structure in Java."):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 200
        assert response.json() == {
            "response": "A HashMap is a key-value data structure in Java."
        }


def test_generate_missing_api_key_handled():
    with patch.object(gemini_service, "generate_response", side_effect=ValueError("GEMINI_API_KEY environment variable is not set")):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 500
        assert "GEMINI_API_KEY" in response.json()["detail"]


def test_generate_gemini_api_error_handled():
    with patch.object(gemini_service, "generate_response", side_effect=RuntimeError("Gemini API error: Rate limit reached")):
        response = client.post(
            "/api/ai/generate",
            json={"prompt": "Explain what a Java HashMap is."}
        )
        assert response.status_code == 502
        assert "Gemini API error" in response.json()["detail"]


def test_gemini_service_missing_key_unit():
    service = GeminiService(api_key="")
    with pytest.raises(ValueError, match="GEMINI_API_KEY"):
        service.generate_response("Test prompt")


def test_gemini_service_mocked_interactions_unit():
    service = GeminiService(api_key="mock-gemini-key", model="gemini-3.6-flash")

    mock_interaction = MagicMock()
    mock_interaction.output_text = "Mocked explanation from Interactions API"

    with patch("app.services.gemini_service.genai.Client") as mock_client_cls:
        mock_client = MagicMock()
        mock_client.interactions.create.return_value = mock_interaction
        mock_client_cls.return_value = mock_client

        result = service.generate_response("Explain Spring Boot")
        assert result == "Mocked explanation from Interactions API"
        mock_client.interactions.create.assert_called_once_with(
            model="gemini-3.6-flash",
            input="Explain Spring Boot"
        )
