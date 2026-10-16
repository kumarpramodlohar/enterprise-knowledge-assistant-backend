# Enterprise Knowledge Assistant

Ask questions about your company's documents and get answers you can check.

> **Status:** MVP foundation, document ingestion/management, semantic retrieval and chat API are implemented. Check the specification task list for verified completion and outstanding release-readiness work.

## The problem

Company knowledge lives in many PDFs, Word files and notes. Finding one answer means opening files and searching by hand. AI chatbots can answer quickly, but they sometimes make things up, and you cannot tell which answers to trust.

## What this project does

1. **Upload** documents (PDF, Word, text, Markdown).
2. **Ask** a question in plain English.
3. **Get an answer with sources.** Each answer points to the document and passage it came from, so you can verify it.
4. **Get an honest "I don't know".** If the documents don't contain the answer, the assistant says so instead of guessing.

People only see documents they are allowed to access.

## Who it is for

| Person | What they do |
|---|---|
| **End user** | Asks questions and checks the sources |
| **Knowledge manager** | Uploads and manages documents |
| **Administrator** | Manages users, access and settings |
| **Operator / developer** | Keeps the service healthy, without reading private documents |

## How it works

This is called retrieval-augmented generation (RAG). In short:

```
Upload a document ──> split into small passages ──> store them so they can be searched by meaning

Ask a question ──> find the best matching passages (only ones you may see)
              ──> AI writes an answer using only those passages
              ──> answer is shown with its sources
```

## Technology

| Part | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Spring AI |
| Database | PostgreSQL with pgvector (searching by meaning) |
| AI models | Ollama for local development, OpenAI when deployed |
| Frontend | React + TypeScript (in `../../frontend/EnterpriseAssistance-UI`) |

## What the first version (MVP) includes

- Sign-in and permission checks on the server
- Document upload with clear errors, and visible processing status (queued, processing, indexed, failed)
- Question answering with citations and an "insufficient evidence" response
- Deleting documents (hidden at once, fully removed after 7 days)
- Tests and a way to measure answer quality

**Not in the first version:** keyword search blended with meaning search, result re-ranking, answer feedback, saved chat history, and Kafka. Also out of scope: AI that takes actions in other systems, training models, and web crawling.

## Documentation

All planning documents are in the [`docs`](docs) folder. Start here:

| Read this | To learn |
|---|---|
| [Product Requirements (PRD)](docs/Enterprise_Knowledge_Assistant_PRD_v1.md) | Who it is for and what is in or out of the first version |
| [Architecture](docs/Enterprise_Knowledge_Assistant_Architecture_v1.md) | How the parts fit together |
| [Feature Specifications](docs/Enterprise_Knowledge_Assistant_Feature_Specifications_v1.md) | Exact behavior and tests for upload, ingestion, retrieval and chat |
| [Implementation Backlog](docs/implementation/Implementation_Backlog_v1.md) | The ordered task list |
| [Product Decisions](docs/implementation/product-decisions.md) | Choices made, such as the 10 MB limit and providers |
| [Requirements (SRS)](docs/Enterprise_Knowledge_Assistant_Requirements_v1.md) | The full source requirements; this document wins if others disagree |

## Project layout

```
EnterpriseKnowledgeAssitance/
├── backend/EnterpriseAssistance/      Spring Boot API (this folder)
│   ├── src/                           Application code
│   └── docs/                          Planning documents
└── frontend/EnterpriseAssistance-UI/  React web app
```

## Run it locally

Requires Java 21, Docker Compose, and Ollama with the configured chat and embedding models pulled. Use placeholders in `.env.example`; do not commit real credentials.

```bash
cp .env.example .env
# Replace change-me values in .env with local-only passwords.
docker compose up -d postgres
docker compose ps
ollama pull llama3.1:8b
ollama pull nomic-embed-text
./gradlew bootRun
```

The backend defaults to the `local` profile and PostgreSQL on `localhost:5433`; Flyway applies migrations during startup. The development HTTP Basic user is read from `EKA_LOCAL_USER` and `EKA_LOCAL_USER_PASSWORD`. It is held in memory for authentication, so seed a matching application identity and collection grant separately:

```bash
PGPASSWORD='<local-only-password>' psql -h localhost -p 5433 -U eka_app -d eka \
  -v ON_ERROR_STOP=1 -v local_subject=dev -v collection_name=General \
  -f src/main/resources/db/seed/local-dev.sql
```

The seed grants `MANAGE` only to the configured local subject in the `General` collection. It contains no passwords. Pass the configured local username as `local_subject` and choose a collection name with the two `psql -v` values. For OIDC deployments, a first valid JWT creates a database identity with `MEMBER` role and no grants; an administrator must grant collection access out of band before the user can see content. The chosen bootstrap mechanism is SQL; no admin API/UI is provided.

**Keycloak sign-in with local Ollama (no OpenAI key):** run with `SPRING_PROFILES_ACTIVE=local,oidc` and set `EKA_OIDC_ISSUER_URI` (for example `http://127.0.0.1:8180/realms/eka`). The `oidc` profile validates Keycloak bearer tokens and disables HTTP Basic. The `deployed` profile is for OpenAI and also needs `OPENAI_API_KEY`; changing embedding provider requires re-indexing documents.

Readiness is available at `GET /actuator/health`. Set `VITE_API_BASE_URL=http://localhost:8080` in the frontend `.env` and follow the frontend README for UI startup. The deployed profile additionally requires `OPENAI_API_KEY` and `EKA_OIDC_ISSUER_URI`; never use local HTTP Basic credentials outside `local`.

Useful verification commands:

```bash
./gradlew test
./gradlew bootRun
curl -i http://localhost:8080/actuator/health
```

If startup fails, check PostgreSQL readiness/credentials, Flyway output, Ollama reachability and exact model names. A provider outage returns a safe API error; it is not reported as a successful answer. For the frontend, see its repository README.

## Important note

This is a portfolio project. Answers are grounded in your documents but can still be wrong, so do not use them as the only basis for high-impact decisions. Always check the cited sources.
