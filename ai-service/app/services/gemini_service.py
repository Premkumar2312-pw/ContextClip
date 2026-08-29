import os
from typing import Optional
from google import genai
from google.genai.errors import APIError

DEFAULT_MODEL = "gemini-3.6-flash"


class GeminiService:
    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self._api_key = api_key
        self._model = model

    @property
    def api_key(self) -> Optional[str]:
        if self._api_key is not None:
            return self._api_key
        return os.getenv("GEMINI_API_KEY")

    @property
    def model(self) -> str:
        if self._model is not None:
            return self._model
        return os.getenv("GEMINI_MODEL", DEFAULT_MODEL)

    def generate_response(self, prompt: str) -> str:
        key = self.api_key
        if not key or not key.strip():
            raise ValueError("GEMINI_API_KEY environment variable is not set")

        try:
            client = genai.Client(api_key=key.strip())
            interaction = client.interactions.create(
                model=self.model,
                input=prompt,
            )
            if not interaction or not interaction.output_text or not interaction.output_text.strip():
                raise ValueError("Empty response received from Gemini API")

            return interaction.output_text.strip()
        except APIError as e:
            raise RuntimeError(f"Gemini API error: {e.message if hasattr(e, 'message') else str(e)}")
        except ValueError:
            raise
        except Exception as e:
            raise RuntimeError(f"Gemini service error: {str(e)}")
