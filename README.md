# Gavel

Surplus food and item auction platform. Sellers list items from their shops, buyers bid in real-time, and winners are notified instantly via WebSocket.

## Architecture

Multi-module Maven project with 6 Spring Boot microservices + a Python AI service:

| Service | Port | Responsibility |
|---------|------|----------------|
| **auction-service** | 8081 | Auction lifecycle, Lua-based bidding, auto-closer, recurring auction templates, geo queries |
| **user-service** | 8082 | Registration, JWT auth, profiles, watchlists |
| **shop-service** | 8083 | Shops, items, reviews, S3 image uploads, AI description generator, Kafka events |
| **bid-service** | 8084 | Bid history (Redis), Kafka consumer |
| **payment-service** | 8085 | Payment processing, recovery job, DynamoDB |
| **notification-service** | 8080 | WebSocket push, notification storage, Kafka consumer |
| **ai-service** | 8086 | Auction recommendations, RAG chatbot (Python/FastAPI) |

**Shared module** provides JWT auth filter, event records, and Kafka infrastructure.

### Infrastructure

- **Kafka** — event streaming (bid.placed, auction.closed, payment.processed, payment.failed, refund.processed, item.created, review.created)
- **Redis** — bidding engine (Lua scripts), session data, geo index, notification storage
- **DynamoDB** — persistent storage for users, shops, items, payments, reviews, auction templates
- **MinIO/S3** — image uploads
- **React + Vite** — frontend SPA
- **Python / FastAPI** — AI recommendation & chatbot service

### Event Topics

Services communicate asynchronously via Kafka:

```
auction-service → bid.placed     → bid-service, notification-service, ai-service
auction-service → auction.closed → bid-service, payment-service, notification-service
payment-service → payment.processed, payment.failed, refund.processed
shop-service    → item.created   → ai-service
shop-service    → review.created → ai-service
```

### AI Description Generator

Sellers can generate product descriptions with AI when creating items. A "Generate with AI" button on the Add Item page sends the item title, category, and retail value to an LLM and populates the description textarea with a 2–3 sentence suggestion the seller can edit before saving.

- **Provider-agnostic**: supports Anthropic (default) and OpenAI via `AI_PROVIDER`, `AI_API_KEY`, `AI_MODEL` env vars
- **Graceful fallback**: button hidden / endpoint returns 503 if no API key is configured
- **Endpoint**: `POST /ai/describe` on shop-service (seller auth required)

### AI Recommendations & Chatbot

Buyers get personalized auction recommendations and a conversational assistant powered by a dedicated Python AI service.

**Recommendations** — "Recommended for You" section on the homepage:
- Scoring: category affinity (40%) + ending-soon urgency (25%) + popularity (20%) + proximity (15%)
- Collaborative filtering boosts auctions liked by similar bidders
- Returns top 8 auctions with a "why" reason per pick
- Endpoint: `GET /ai/recommendations?lat=X&lng=Y` (buyer auth required)

**RAG Chatbot** — floating chat widget on all buyer pages:
- Indexes auction listings and shop reviews into a TF-IDF vector store (refreshed every 30s)
- Retrieves top-5 relevant context chunks per query, injects into LLM system prompt
- Conversation memory per session (last 10 messages, 30-min TTL)
- Endpoint: `POST /ai/chat` with `{message, conversation_id}` (buyer auth required)

**Infrastructure**: FastAPI + PyJWT (validates same HS256 `JWT_SECRET`), Kafka consumer for real-time indexing (`bid.placed`, `item.created`, `review.created` events), Chroma vector store with sentence-transformer embeddings, provider-agnostic LLM client (Anthropic/OpenAI)

### Recurring Auctions

Sellers can create auction templates that auto-publish auctions on a schedule — ideal for bakeries and restaurants with predictable daily surplus.

- **Schedule types**: daily or weekly (with specific day selection)
- **Auto-computed pickup windows**: configurable offset and duration relative to auction end
- **Template management**: pause, resume, and delete from the seller dashboard "Recurring" tab
- **Scheduler**: `TemplateScheduler` checks active templates every 30s and generates auctions via the standard `AuctionService.createAuction()` flow

## Quick Start

```bash
# Prerequisites: Docker, Docker Compose

# Start everything
docker compose up --build

# Run smoke tests (45 assertions)
bash tests/smoke_test.sh

# Run load test (requires k6)
k6 run tests/loadtest/k6_bid_contention.js
```

The platform is available at:
- Frontend: http://localhost:5173
- Services: http://localhost:8080-8086
- MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
- DynamoDB Local: http://localhost:8000

## Tech Stack

- Java 21, Spring Boot 3.3.5
- Apache Kafka (KRaft mode, Spring Kafka)
- Redis 7 (Lettuce client, Lua scripts, GEO)
- AWS DynamoDB (Enhanced Client)
- AWS S3 / MinIO
- WebSocket (Spring WebSocket, `ConcurrentWebSocketSessionDecorator`)
- JWT (jjwt / HS256, PyJWT for ai-service)
- React + TypeScript + Vite
- Python 3.12, FastAPI, scikit-learn
- Docker multi-stage builds (Eclipse Temurin 21, python:3.12-slim)

## Building

```bash
# Build all modules
./mvnw package -DskipTests

# Build a single service
./mvnw package -pl shared,auction-service -am -DskipTests
```

## Testing

**Java unit tests** — 168 tests across all services:
```bash
./mvnw test
```

**Python unit tests** — 30 tests for ai-service:
```bash
cd ai-service && python -m pytest
```

**Smoke tests** — end-to-end API contract validation:
```bash
bash tests/smoke_test.sh
```

**Load tests** — concurrent bid contention (50 VUs, 30s):
```bash
k6 run tests/loadtest/k6_bid_contention.js
```

Results from load testing:
- p50: 2ms, p95: 5ms, p99: 22ms bid latency
- Zero double-accepts under 50 concurrent bidders
- ~248 bids/second throughput

## Project Structure

```
gavel/
├── pom.xml                    # Root Maven POM (Java 21, Spring Boot 3.3.5)
├── shared/                    # JWT, events, Kafka publisher + config
├── auction-service/           # Core bidding engine
├── bid-service/               # Bid history consumer
├── notification-service/      # WebSocket + notification storage
├── user-service/              # Auth + user management
├── shop-service/              # Shops, items, reviews, uploads
├── payment-service/           # Payment processing + recovery
├── ai-service/                # Recommendations + RAG chatbot (Python)
├── frontend/                  # React SPA
├── scripts/init-tables/       # DynamoDB table initialization
├── tests/                     # Smoke + load tests
├── infra/                     # Terraform (AWS deployment)
└── docker-compose.yml
```
