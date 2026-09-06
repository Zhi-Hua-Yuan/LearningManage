# Stage 5 RAG evaluation

The Promptfoo suite is under `evals/stage5` and calls the production `/api/ai/rag/ask` API through a JavaScript provider. It does not duplicate the production system prompt.

Dataset `1.0.0` contains 30 regression and 20 holdout questions. Every fixture source has a unique `EVIDENCE-NNN` marker and a fixed expected title. Deterministic CI indexes all 50 sources, queries through Query Embedding + Qdrant + Rerank + Chat, and requires the expected source in the first five returned citations.

```bash
cd evals/stage3 && npm ci --ignore-scripts && npm rebuild better-sqlite3
cd ../stage5
npm test
npm run validate
npm run prepare:fixture
npm run eval
npm run cleanup:fixture
```

Required runtime variables:

```text
STAGE5_API_BASE_URL
STAGE5_EVAL_ACCOUNT
STAGE5_EVAL_PASSWORD
STAGE5_EVAL_ALLOW_REGISTER=true   # isolated CI only
```

Deterministic CI proves application-path contracts and cited Hit@5. A protected real-provider run is still required for publishable Recall/Hit@5, latency, and cost claims. Real-model results must be repeated three times; privacy and authorization metrics must pass every run.
