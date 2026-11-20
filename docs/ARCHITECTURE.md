# Sales Online - Architecture

A single Spring Boot application (Java 25 with preview features), packaged by feature. Each feature has a `domain` package (records, services, port interfaces) and adapter packages: `api` (v1 REST controllers), `persistence/postgres` and `messaging/kafka`. The persistence and messaging adapters are selected by `sales.persistence.provider` and `sales.messaging.provider`; only PostgreSQL and Kafka exist today.

| Concern | Implementation |
|---|---|
| API | Spring MVC, path versioning `/api/v{version}` (`ApiVersioningConfig`), OpenAPI at `/v3/api-docs/v1`, Swagger UI at `/swagger-ui.html` |
| Security | HTTP Basic (`users` table, BCrypt) and OAuth2 bearer JWT from the broker (`typ` `JWT` or `at+jwt`) |
| Errors | RFC 9457 problem responses with `correlationId` (`GlobalExceptionHandler`) |
| Data | PostgreSQL via `JdbcClient` + records, Flyway migrations, pgvector |
| Cache | Redis cache-aside (`CacheAside`): `product:{id}`, `category:all`, `product-page:...` |
| Async | Kafka topic `sales_online` (dead letters in `sales_online-dlt`) |
| AI | Spring AI with OpenAI: embeddings for guide chunks, chat for RAG answers |

## Class diagram (core domain)

```mermaid
classDiagram
    direction LR
    class SalesEvent {
        <<sealed interface>>
        +eventId() UUID
        +occurredAt() Instant
        +key() String
    }
    class OrderPlaced {
        <<record>>
        UUID orderId
    }
    class OrderCancelled {
        <<record>>
        UUID orderId
        String reason
    }
    class ProductGuideChanged {
        <<record>>
        long productId
    }
    SalesEvent <|.. OrderPlaced
    SalesEvent <|.. OrderCancelled
    SalesEvent <|.. ProductGuideChanged

    class EventPublisher {
        <<interface>>
        +publish(SalesEvent)
    }
    class KafkaEventPublisher
    class KafkaEventListener
    class EventDispatcher {
        +dispatch(SalesEvent)
    }
    EventPublisher <|.. KafkaEventPublisher
    KafkaEventListener --> EventDispatcher
    EventDispatcher --> ProcessedEventRepository
    EventDispatcher --> OrderProcessor
    EventDispatcher --> GuideIndexer

    class ProductService
    class CategoryService
    class CartService
    class OrderService
    class OrderProcessor
    class GuideIndexer
    class SearchService
    class AssistantService
    class UserService
    class CacheAside

    ProductService --> ProductRepository
    ProductService --> InventoryRepository
    ProductService --> CacheAside
    ProductService --> EventPublisher
    CategoryService --> CategoryRepository
    CategoryService --> CacheAside
    CartService --> CartRepository
    CartService --> InventoryRepository
    OrderService --> OrderRepository
    OrderService --> CartService
    OrderService --> EventPublisher
    OrderProcessor --> OrderRepository
    OrderProcessor --> InventoryRepository
    OrderProcessor --> PaymentGateway
    OrderProcessor --> CacheAside
    GuideIndexer --> ProductRepository
    GuideIndexer --> GuideVectorStore
    GuideIndexer --> CacheAside
    SearchService --> FullTextSearch
    SearchService --> GuideVectorStore
    AssistantService --> GuideVectorStore
    AssistantService --> ProductRepository
    UserService --> UserRepository

    class ProductRepository {
        <<interface>>
    }
    class InventoryRepository {
        <<interface>>
    }
    class CategoryRepository {
        <<interface>>
    }
    class CartRepository {
        <<interface>>
    }
    class OrderRepository {
        <<interface>>
    }
    class UserRepository {
        <<interface>>
    }
    class ProcessedEventRepository {
        <<interface>>
    }
    class GuideVectorStore {
        <<interface>>
    }
    class FullTextSearch {
        <<interface>>
    }
    class PaymentGateway {
        <<interface>>
    }
    ProductRepository <|.. PostgresProductRepository
    OrderRepository <|.. PostgresOrderRepository
    GuideVectorStore <|.. PgVectorGuideStore
    FullTextSearch <|.. PostgresFullTextSearch
    PaymentGateway <|.. MockPaymentGateway

    class PaymentResult {
        <<sealed interface>>
    }
    class Approved {
        <<record>>
    }
    class Declined {
        <<record>>
    }
    PaymentResult <|.. Approved
    PaymentResult <|.. Declined

    class SalesException {
        <<sealed abstract>>
    }
    SalesException <|-- NotFoundException
    SalesException <|-- ConflictException
    SalesException <|-- ValidationException
```

Every port has a Postgres adapter (`Postgres*Repository`); only a few are drawn.

## Sequence: asynchronous order placement

```mermaid
sequenceDiagram
    actor C as Client
    participant API as OrderController v1
    participant OS as OrderService
    participant DB as PostgreSQL
    participant K as Kafka sales_online
    participant L as KafkaEventListener
    participant D as EventDispatcher
    participant OP as OrderProcessor
    participant PG as MockPaymentGateway
    participant R as Redis

    C->>API: POST /api/v1/orders (Basic or Bearer)
    API->>OS: place(user, shippingAddress)
    rect rgb(240, 240, 240)
        note over OS,K: one transaction
        OS->>DB: insert order PENDING + items (price snapshot), clear cart
        OS->>K: publish OrderPlaced (key = orderId), wait for the ack
    end
    API-->>C: 202 Accepted, Location /api/v1/orders/{id}
    K-->>L: OrderPlaced (correlation id header)
    L->>D: dispatch (ScopedValue bound, one transaction)
    D->>DB: insert processed_events (duplicate delivery: skip)
    D->>OP: process(orderId)
    OP->>DB: lock order, select inventory ... for update (product id order)
    alt stock is short
        OP->>DB: status REJECTED (reason)
    else stock available
        OP->>PG: charge(total)
        OP->>DB: insert payment (gateway_response jsonb)
        alt approved
            OP->>DB: decrement stock, status CONFIRMED
            OP->>R: evict product:{id}
        else declined
            OP->>DB: status REJECTED (reason)
        end
    end
    C->>API: GET /api/v1/orders/{id}
    API-->>C: 200 {status: CONFIRMED}
```

A failed event is retried with backoff and then sent to `sales_online-dlt`. If the broker is down when the order is placed, the transaction rolls back, the cart is kept and the client gets `503`.

## Sequence: product update and guide re-embedding

```mermaid
sequenceDiagram
    actor A as Admin
    participant PS as ProductService
    participant DB as PostgreSQL
    participant R as Redis
    participant K as Kafka
    participant GI as GuideIndexer
    participant AI as OpenAI embeddings

    A->>PS: PUT /api/v1/products/{id}
    PS->>DB: update product (search_vector is regenerated)
    PS->>R: evict product:{id}
    PS->>K: publish ProductGuideChanged
    PS-->>A: 200 ProductDetails
    K-->>GI: ProductGuideChanged (via listener and dispatcher)
    GI->>GI: chunk the guide (custom Gatherer, overlap)
    GI->>AI: embed chunks (Gatherers.windowFixed batches)
    GI->>DB: replace product_guide_chunks
    GI->>R: evict product:{id} (guide summary changed)
```

## Sequence: cache-aside product read

```mermaid
sequenceDiagram
    actor C as Client
    participant PS as ProductService
    participant R as Redis
    participant DB as PostgreSQL
    C->>PS: GET /api/v1/products/{id}
    PS->>R: GET product:{id}
    alt hit
        R-->>PS: json
    else miss (or Redis down)
        par structured concurrency
            PS->>DB: product
        and
            PS->>DB: stock
        and
            PS->>DB: guide summary
        end
        PS->>R: SET product:{id} EX 10 min
    end
    PS-->>C: 200 ProductDetails
```

## Sequence: authentication

```mermaid
sequenceDiagram
    actor C as Client
    participant B as OAuth2 broker :8443
    participant F as Security filter chain
    participant US as UserService
    participant DB as PostgreSQL
    alt HTTP Basic
        C->>F: Authorization: Basic
        F->>US: loadUserByUsername
        US->>DB: select LOCAL user (BCrypt hash, roles)
    else Bearer JWT
        C->>B: authorization code + PKCE
        B-->>C: access token (typ at+jwt)
        C->>F: Authorization: Bearer
        F->>B: JWKS (cached, TLS via broker truststore)
        F->>F: verify signature, expiry, issuer, typ; roles from scope
        F->>US: current(jwt)
        US->>DB: find by external_id = sub, or insert OAUTH2 user
    end
    F-->>C: 401 / 403 problem response on failure
```

## Database (schema `sales_online`)

```mermaid
erDiagram
    users ||--o| carts : owns
    users ||--o{ orders : places
    categories ||--o{ products : contains
    products ||--|| inventory : stocked
    products ||--o{ product_guide_chunks : "embedded as"
    carts ||--o{ cart_items : has
    products ||--o{ cart_items : in
    orders ||--o{ order_items : has
    products ||--o{ order_items : in
    orders ||--o| payments : paid_by

    users {
        uuid id PK
        varchar username UK
        email_address email UK
        varchar password_hash "null for OAUTH2"
        varchar auth_provider "LOCAL|OAUTH2"
        varchar external_id UK
        varchar_array roles
        postal_address default_address "composite"
        jsonb preferences
        timestamptz created_at
    }
    categories {
        bigint id PK
        varchar slug UK
        varchar name UK
        text description
    }
    products {
        bigint id PK
        sku_code sku UK
        bigint category_id FK
        varchar name
        varchar brand
        text description
        money_amount price
        currency_code currency
        rating rating
        text_array tags "GIN"
        text_array image_urls
        dimensions_cm dimensions "composite"
        jsonb attributes "GIN jsonb_path_ops"
        text user_guide
        text_array guide_sections
        tsvector search_vector "generated, GIN"
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }
    inventory {
        bigint product_id PK, FK
        int quantity "check >= 0"
        timestamptz updated_at
    }
    product_guide_chunks {
        bigint id PK
        bigint product_id FK
        int chunk_index
        text content
        jsonb metadata
        vector embedding "1536, HNSW cosine"
    }
    carts {
        uuid id PK
        uuid user_id FK, UK
        timestamptz updated_at
    }
    cart_items {
        uuid cart_id PK, FK
        bigint product_id PK, FK
        positive_qty quantity
    }
    orders {
        uuid id PK
        uuid user_id FK
        order_status status
        money_amount total
        currency_code currency
        postal_address shipping_address "composite"
        jsonb status_history
        timestamptz created_at
        timestamptz updated_at
    }
    order_items {
        uuid order_id PK, FK
        bigint product_id PK, FK
        varchar product_name
        price_tag unit_price "composite"
        positive_qty quantity
    }
    payments {
        uuid id PK
        uuid order_id FK, UK
        money_amount amount
        varchar status "APPROVED|DECLINED"
        varchar reference
        jsonb gateway_response
        timestamptz created_at
    }
    processed_events {
        uuid event_id PK
        varchar event_type
        jsonb payload
        timestamptz processed_at
    }
```

Array columns are written as `text_array` / `varchar_array` because mermaid cannot render `text[]`. Custom domains (`email_address`, `sku_code`, `money_amount`, `currency_code`, `positive_qty`, `rating`, `order_status`) and composite types (`postal_address`, `dimensions_cm`, `price_tag`) are defined in `V1__types.sql`.

Views (`V3__views.sql`): `v_product_catalog` (product, category, stock, `in_stock`), `v_order_summary` (order with items as `jsonb_agg` and flattened address), `v_category_stats` (counts, price range, distinct tags). Indexes: GIN on `search_vector`, `tags` and `attributes`; HNSW on `embedding`; B-tree on `products(category_id)` and `orders(user_id, created_at desc)`.

## Tests

| Suite | Command | Runs against |
|---|---|---|
| Unit (`*Test`) | `./mvnw test` | mocks and fakes |
| Integration (`*IT`) | `./mvnw verify` | dev servers, schema `sales_online_test`, Redis DB 1, topic `sales_online_test`, fake AI models, self-signed test JWTs |
| End-to-end (`*E2E`) | `./mvnw -Pe2e verify` | the running server on `https://localhost:9443` and the OAuth2 broker; Playwright API client and headless Chromium |
