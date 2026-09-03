import os
from typing import Optional
from groq import Groq, APIError, RateLimitError, AuthenticationError, APIConnectionError, BadRequestError, InternalServerError

DEFAULT_MODEL = "openai/gpt-oss-120b"


class GroqService:
    def __init__(self, api_key: Optional[str] = None, model: Optional[str] = None):
        self._api_key = api_key
        self._model = model

    @property
    def api_key(self) -> Optional[str]:
        if self._api_key is not None:
            return self._api_key
        return os.getenv("GROQ_API_KEY")

    @property
    def model(self) -> str:
        if self._model is not None:
            return self._model
        return os.getenv("GROQ_MODEL", DEFAULT_MODEL)

    def is_configured(self) -> bool:
        key = self.api_key
        return bool(key and key.strip())

    def generate_response(self, prompt: str) -> str:
        key = self.api_key
        if not key or not key.strip():
            raise ValueError("GROQ_API_KEY environment variable is not set")

        client = Groq(api_key=key.strip())
        chat_completion = client.chat.completions.create(
            messages=[
                {
                    "role": "user",
                    "content": prompt,
                }
            ],
            model=self.model,
            temperature=0.2,
        )

        if not chat_completion.choices or not chat_completion.choices[0].message or not chat_completion.choices[0].message.content:
            raise ValueError("Empty response received from Groq API")

        return chat_completion.choices[0].message.content.strip()
