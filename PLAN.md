# Development Plan

### 11. Real payment gateway
Payment processing is currently simulated (90% success rate mock). Replace with Stripe or equivalent for production.

### 12. Shop settlement
The payment flow records `shop_id` but does not disburse funds to the shop owner. Settlement flow to be designed.

### ~~17. Recurring auctions~~ DONE
Sellers create auction templates with schedule config (daily or weekly with day selection, time in UTC). Templates stored in DynamoDB `AuctionTemplates` table with `shop_id-index` GSI. `TemplateScheduler` (30s `@Scheduled`) checks active templates and calls `AuctionService.createAuction()` when `next_run_at` has passed. Pickup windows auto-computed from configurable offset/window minutes. Frontend: `CreateTemplatePage`, "Recurring" tab on `SellerShopPage` with pause/resume/delete. REST: `POST /templates`, `GET /shops/{shopId}/templates`, `PATCH /templates/{id}/active`, `DELETE /templates/{id}`.

### 18. Analytics dashboard
Seller-facing analytics page: revenue over time, average selling price vs retail price, bidder count trends, top-performing items. Aggregate data from completed auctions and payments. Chart library (e.g. Recharts) for visualizations. Could also include a platform-wide admin view.

### 22. AI description generator (seller)
"Generate with AI" button on `CreateItemPage` next to the description textarea. Calls a new `POST /ai/describe` endpoint on the shop service. Backend takes `title`, `category`, and `retail_value`, sends a prompt to an LLM (OpenAI / Gemini / Anthropic — provider configured via env vars), and returns a 2–3 sentence product description. Seller can edit the suggestion before saving. API key stored as an env var (e.g. `AI_API_KEY`); falls back gracefully if not configured.

**Implementation detail:**
- **Backend**: `AiService.java` — provider-agnostic LLM client (Anthropic/OpenAI), configured via `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL` env vars. `AiController.java` — `POST /ai/describe` accepting `{title, category, retail_value}`, returns `{description}`. Seller-only auth. Returns 503 if not configured.
- **Config**: `application.yml` adds `ai.provider`, `ai.api-key`, `ai.model`. Security config permits `/ai/**` for authenticated users.
- **Frontend**: "Generate with AI" button in `CreateItemPage.tsx` next to description textarea. Calls `api.ai.describe()`, populates textarea. Shows loading spinner. Disabled when title is empty. New `api.ai.describe(title, category, retailValue, token)` in `api.ts`.

### 23. AI auction recommendations (buyer) + 24. AI chatbot assistant (buyer)
Both live in a new **Python `ai-service`** (FastAPI), sharing an embedding pipeline and vector store.

**Language decision:** #22 stays in Java (simple LLM API call, no vectors). #23 and #24 move to Python because:
- #23 benefits from collaborative filtering (`implicit`, `scipy.sparse`, `scikit-learn`) and learned item embeddings for similarity — same vector store as #24
- #24 needs RAG (chunking, embeddings, vector search) where Python's ecosystem is dramatically stronger (LangChain/LlamaIndex, sentence-transformers, tiktoken)
- Shared infrastructure: both use the same embedding model and vector store, so co-locating avoids duplication

**#23 — Recommendations:**
- "Recommended for You" section on `HomePage` for logged-in buyers
- Scoring: collaborative filtering (user-item bid matrix) + item embedding similarity + heuristic signals (category affinity 40%, ending-soon 25%, popularity 20%, proximity 15%)
- Returns top 5–8 auctions with "why" reason per pick
- `GET /ai/recommendations?lat=X&lng=Y` (authenticated buyer)
- Frontend: horizontal scroll row above main grid, "Why?" tooltip per card, hidden when empty

**#24 — RAG Chatbot:**
- Floating chat widget on all buyer pages, expandable panel
- `POST /ai/chat` with `{message, conversation_id}`
- RAG pipeline: embed reviews + item descriptions into vector store (pgvector / OpenSearch); retrieve top-K chunks per query, inject into LLM prompt
- Indexing: Kafka consumer listens for review/item creation events
- Conversation memory: last N messages in session, no long-term persistence

**Shared ai-service infra:**
- FastAPI + PyJWT (validates HS256 with same `JWT_SECRET`)
- Kafka consumer for indexing events
- Vector store for embeddings
- Vite proxies `/ai` to the service
- Dockerfile in `ai-service/`

