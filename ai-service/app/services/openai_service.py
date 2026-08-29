import os
from typing import Optional
from openai import OpenAI, OpenAIError

DEFAULT_MODEL = "gpt-4o-mini"


class OpenAIService:
    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self._api_key = api_key
        self._model = model

    @property
    def api_key(self) -> Optional[str]:
        return self._api_key or os.getenv("OPENAI_API_KEY")

    @property
    def model(self) -> str:
        return self._model or os.getenv("OPENAI_MODEL", DEFAULT_MODEL)

    def generate_response(self, prompt: str) -> str:
        key = self.api_key
        if not key or not key.strip():
            raise ValueError("OPENAI_API_KEY environment variable is not set")

        try:
            client = OpenAI(api_key=key.strip())
            response = client.chat.completions.create(
                model=self.model,
                messages=[
                    {
                        "role": "system",
                        "content": "You are a helpful assistant for the ContextClip clipboard knowledge system."
                    },
                    {
                        "role": "user",
                        "content": prompt
                    }
                ],
            )
            if not response.choices or not response.choices[0].message.content:
                raise ValueError("Empty response received from OpenAI API")

            return response.choices[0].message.content.strip()
        except OpenAIError as e:
            raise RuntimeError(f"OpenAI API error: {e.message if hasattr(e, 'message') else str(e)}")
