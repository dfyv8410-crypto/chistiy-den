# Tests for «Чистый день»

## Structure

- `unit/` — Pure JS unit tests (run with Node.js)
- `integration/` — Integration tests (need a browser/Playwright)
- `ai/` — AI service tests (need Python + pytest)

## Running

```bash
# Unit tests (pure JS functions extracted from index.html)
node tests/unit/storage.test.js
node tests/unit/diary.test.js

# AI service tests (need Python 3.11+)
cd ai-service && pip install -e ".[dev]" && pytest ../tests/ai/

# Integration tests (need Playwright)
npx playwright test tests/integration/
```
