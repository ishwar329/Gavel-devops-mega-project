# Gavel

Surplus food and item auction platform. Sellers list items from their shops, buyers bid in real-time, and winners are notified instantly via WebSocket.

## Architecture

Multi-module Maven project with 6 Spring Boot microservices:

| Service | Port | Responsibility |
|---------|------|----------------|
| **auction-service** | 8081 | Auction lifecycle, Lua-based bidding, auto-closer, geo queries |
| **user-service** | 8082 | Registration, JWT auth, profiles, watchlists |
| **shop-service** | 8083 | Shops, items, reviews, S3 image uploads |
| **bid-service** | 8084 | Bid history (Redis), Kafka consumer |
| **payment-service** | 8085 | Payment processing, recovery job, DynamoDB |
| **notification-service** | 8080 | WebSocket push, notification storage, Kafka consumer |

**Shared module** provides JWT auth filter, event records, and Kafka infrastructure.

### Infrastructure

- **Kafka** — event streaming (bid.placed, auction.closed, payment.processed, payment.failed, refund.processed)
- **Redis** — bidding engine (Lua scripts), session data, geo index, notification storage
- **DynamoDB** — persistent storage for users, shops, items, payments, reviews
- **MinIO/S3** — image uploads
- **React + Vite** — frontend SPA

### Event Topics

Services communicate asynchronously via Kafka:

```
auction-service → bid.placed     → bid-service, notification-service
auction-service → auction.closed → bid-service, payment-service, notification-service
payment-service → payment.processed, payment.failed, refund.processed
```

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
- Services: http://localhost:8080-8085
- MinIO Console: http://localhost:9001 (minioadmin/minioadmin)
- DynamoDB Local: http://localhost:8000

## Tech Stack

- Java 21, Spring Boot 3.3.5
- Apache Kafka (KRaft mode, Spring Kafka)
- Redis 7 (Lettuce client, Lua scripts, GEO)
- AWS DynamoDB (Enhanced Client)
- AWS S3 / MinIO
- WebSocket (Spring WebSocket, `ConcurrentWebSocketSessionDecorator`)
- JWT (jjwt / HS256)
- React + TypeScript + Vite
- Docker multi-stage builds (Eclipse Temurin 21)

## Building

```bash
# Build all modules
./mvnw package -DskipTests

# Build a single service
./mvnw package -pl shared,auction-service -am -DskipTests
```

## Testing

**Unit tests** — 477 tests with 84.2% overall coverage:
```bash
./mvnw test
```

Coverage by service:
- shop-service: 90.4% | payment-service: 87.4% | auction-service: 84.2%
- notification-service: 83.5% | shared: 83.8% | user-service: 79.2%
- bid-service: 74.7%

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
├── frontend/                  # React SPA
├── scripts/init-tables/       # DynamoDB table initialization
├── tests/                     # Smoke + load tests
├── infra/                     # Terraform (AWS deployment)
└── docker-compose.yml
```
