# Enterprise Knowledge Assistant

Ask questions about your company's documents and get answers you can check.

> **Status:** Planning and early setup. The documents describing what to build are written. The backend is an empty Spring Boot project and the frontend is a blank React project. Nothing described below works yet.

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

The backend currently only starts an empty application. Requires Java 21.

```bash
./gradlew bootRun     # start the backend
./gradlew test        # run tests
```

For the frontend, see its [README](../../frontend/EnterpriseAssistance-UI/README.md).

## Important note

This is a portfolio project. Answers are grounded in your documents but can still be wrong, so do not use them as the only basis for high-impact decisions. Always check the cited sources.
