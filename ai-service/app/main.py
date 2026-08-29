from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from dotenv import load_dotenv
import os

from app.services.openai_service import OpenAIService

# Load local .env file if present
load_dotenv()

app = FastAPI(
    title="ContextClip AI Service",
    description="Standalone Python AI Service for ContextClip intelligent clipboard system",
    version="0.1.0"
)

openai_service = OpenAIService()

MAX_PROMPT_LENGTH = 10000


class HealthResponse(BaseModel):
    status: str
    service: str


class GenerateRequest(BaseModel):
    prompt: str = Field(..., description="Prompt text to send to the LLM")


class GenerateResponse(BaseModel):
    response: str = Field(..., description="Generated text response from the LLM")


@app.get(
    "/api/ai/health",
    response_model=HealthResponse,
    tags=["Health"]
)
def health():
    return HealthResponse(
        status="UP",
        service="ContextClip AI Service"
    )


@app.post(
    "/api/ai/generate",
    response_model=GenerateResponse,
    status_code=status.HTTP_200_OK,
    tags=["AI Generation"]
)
def generate(request: GenerateRequest):
    if not request.prompt or not request.prompt.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Prompt cannot be empty or blank"
        )

    if len(request.prompt) > MAX_PROMPT_LENGTH:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Prompt exceeds maximum allowed length of {MAX_PROMPT_LENGTH} characters"
        )

    try:
        result = openai_service.generate_response(request.prompt.strip())
        return GenerateResponse(response=result)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
    except RuntimeError as e:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=str(e)
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An unexpected error occurred while processing the AI request"
        )
