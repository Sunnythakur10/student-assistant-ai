# student-assistant

## Prerequisites

- Java 21+
- Maven 3.9+
- Docker Desktop
- Ollama installed locally (https://ollama.com/download)
  - Run: `ollama pull nomic-embed-text`
- Groq API key (https://console.groq.com)

## Setup

### Step 2 — Start Ollama embedding model

```bash
ollama serve
```

(Ollama may already be running as a background service after install.)

### Step 3 — Set your Groq API key

**Windows PowerShell:**

```powershell
$env:GROQ_API_KEY="gsk_your-key-here"
```

**Mac/Linux:**

```bash
export GROQ_API_KEY=gsk_your-key-here
```

Start PostgreSQL (e.g. `docker compose up` from the project root), then run the app with `mvn spring-boot:run` and open http://localhost:8080.
