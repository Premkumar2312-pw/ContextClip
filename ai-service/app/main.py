from fastapi import FastAPI, HTTPException, status
from pydantic import BaseModel, Field
from dotenv import load_dotenv
from groq import RateLimitError, AuthenticationError, APIConnectionError, BadRequestError, InternalServerError, APIError

from app.services.groq_service import GroqService

# Load local .env file if present
load_dotenv()

app = FastAPI(
    title="ContextClip AI Service",
    description="Standalone Python AI Service for ContextClip intelligent clipboard system (Groq API)",
    version="0.2.0"
)

groq_service = GroqService()

MAX_PROMPT_LENGTH = 10000


class HealthResponse(BaseModel):
    status: str
    service: str
    configured: bool


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
        service="ContextClip AI Service",
        configured=groq_service.is_configured()
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
        result = groq_service.generate_response(request.prompt.strip())
        return GenerateResponse(response=result)
    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=str(e)
        )
    except RateLimitError as e:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail=f"Groq API rate limit reached: {str(e)}"
        )
    except AuthenticationError as e:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Groq API authentication failed: {str(e)}"
        )
    except (APIConnectionError, InternalServerError) as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Groq API service unavailable: {str(e)}"
        )
    except BadRequestError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Groq API bad request: {str(e)}"
        )
    except APIError as e:
        status_code = getattr(e, "status_code", 502) or 502
        if status_code not in [400, 401, 403, 429, 500, 502, 503]:
            status_code = 502
        raise HTTPException(
            status_code=status_code,
            detail=f"Groq API error: {str(e)}"
        )
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="An unexpected error occurred while processing the AI request"
        )
