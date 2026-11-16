# Java RAG smoke test

This small synthetic corpus checks ingestion, indexing, authorized collection retrieval, citations, answer generation, and abstention. It is not a substitute for evaluation against the Java 5 book you uploaded.

## Run it

1. Upload **only** the five Markdown files in `sources/` into one collection where your user has `MANAGE` permission.
2. Wait until all five documents show **Ready / INDEXED**.
3. Ask the 50 questions in `questions-and-answers.md`, selecting that collection. Leave Version, Document type, and Tags empty. The expected answers are in the answer key; do not upload that file.
4. For questions 1–45, check that the response has a grounded answer and at least one citation to the listed source document. For questions 46–50, check that EKA abstains with insufficient evidence.

The Markdown source is deliberately clean and text-based so PDF font/OCR limitations do not affect this baseline test. A failure here points to app configuration, authorization, indexing, retrieval, or answer generation. If this pack works but the PDF book does not, investigate PDF extraction or that book's content.
