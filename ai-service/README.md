# ContextClip AI Service

The ContextClip AI Service is a lightweight, standalone Python microservice built with FastAPI and the official OpenAI Python SDK. It provides generative AI intelligence to explain, summarize, and answer questions about developer clipboard history.

## Technology Stack

- **Python**: 3.11+
- **FastAPI**: Modern, high-performance web framework for APIs
- **Uvicorn**: Lightning-fast ASGI server
- **Pydantic v2**: Data validation and response schemas
- **OpenAI Python SDK**: Direct integration with OpenAI models (default: `gpt-4o-mini`)
- **Pytest & HTTPX**: Unit and integration testing

## Project Structure

```text
ai-service/
├── app/
│   ├── __init__.py
│   ├── main.py
│   └── services/
│       ├── __init__.py
│       └── openai_service.py
├── tests/
│   ├── __init__.py
│   └── test_main.py
├── requirements.txt
├── .gitignore
└── README.md
```

## Environment Variables

Configuration is loaded from environment variables or a local `.env` file (which must never be committed to source control).

| Variable | Required | Default | Description |
| :--- | :--- | :--- | :--- |
| `OPENAI_API_KEY` | Yes (for AI generation) | None | OpenAI API secret key |
| `OPENAI_MODEL` | No | `gpt-4o-mini` | OpenAI chat completion model name |
| `AI_SERVICE_PORT` | No | `8000` | Port for the FastAPI server |

## Installation & Setup

1. Navigate to the `ai-service` directory:
   ```bash
   cd ai-service
   ```

2. Create and activate a Python virtual environment:
   ```bash
   python -m venv .venv
   # Windows PowerShell:
   .venv\Scripts\Activate.ps1
   # Linux / macOS:
   source .venv/bin/activate
   ```

3. Install dependencies:
   ```bash
   pip install -r requirements.txt
   ```

## Running Locally

Start the FastAPI application using Uvicorn:

```bash
uvicorn app.main:app --reload --port 8000
```

The interactive OpenAPI documentation will be accessible at `http://localhost:8000/docs`.

## API Endpoints

### 1. Health Check

Checks whether the AI service is online. Does **not** require an OpenAI API key or make external network calls.

- **Method**: `GET`
- **URL**: `/api/ai/health`
- **Response** (`200 OK`):
  ```json
  {
    "status": "UP",
    "service": "ContextClip AI Service"
  }
  ```

### 2. Generate AI Response

Sends a prompt to OpenAI and returns the generated text explanation.

- **Method**: `POST`
- **URL**: `/api/ai/generate`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "prompt": "Explain what a Java HashMap is in simple terms."
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "response": "A HashMap in Java is a data structure used to store key-value pairs..."
  }
  ```
- **Error Responses**:
  - `400 Bad Request`: Empty, blank, or excessively long (>10,000 characters) prompt.
  - `500 Internal Server Error`: Missing `OPENAI_API_KEY` configuration.
  - `502 Bad Gateway`: Upstream OpenAI API error or network failure.

## Automated Testing

Run the automated test suite using `pytest`:

```bash
pytest -v
```

Automated tests use mocks for external OpenAI API calls and execute without requiring an active API key or internet access.
