# Demo apps for dynamodb-simplified

Multi-framework demo project for [`dynamodb-simplified-core`](https://github.com/hogwai/dynamodb-simplified), a fluent DynamoDB wrapper library for Java.

Two complementary demo applications share a common `Post` model and run against the same DynamoDB Local table:

| Application           | Framework       | Style                       | Port | Bonus Features                                                              |
|-----------------------|-----------------|-----------------------------|------|-----------------------------------------------------------------------------|
| **:micronaut-demo**   | Micronaut 4.10  | Async (`CompletableFuture`) | 8082 | `/count`, reactive `/stream`                                                |
| **:spring-boot-demo** | Spring Boot 4.1 | Sync                        | 8080 | batch write/get, transact write, `PATCH` partial update, delete with return |

## Quick Start

```sh
# 1. Start DynamoDB Local
docker compose up -d

# 2. Create the posts table
./create-table.sh

# 3. Run either demo (or both)
./gradlew :micronaut-demo:run
./gradlew :spring-boot-demo:bootRun
```

## Project Structure

```
dynamodb-simplified-demo/
├── common/                          # Shared model and DTOs
│   └── src/main/java/dev/hogwai/demo/
│       ├── model/Post.java          # @DynamoDbBean entity
│       ├── dto/
│       │   ├── CreatePostRequest.java
│       │   └── PagedResponse.java
│       ├── search/PostSearchCriteria.java
│       └── exception/PostNotFoundException.java
├── micronaut-demo/                  # Async Micronaut application
│   └── src/main/java/dev/hogwai/app/
│       ├── Application.java
│       ├── config/DynamoDbFactory.java
│       ├── repository/PostAsyncRepository.java
│       ├── service/PostService.java
│       ├── controller/PostController.java
│       ├── dto/PostSearchRequest.java
│       ├── exception/GlobalExceptionHandler.java
│       └── startup/DataGeneratorListener.java
├── spring-boot-demo/               # Sync Spring Boot application
│   └── src/main/java/dev/hogwai/demo/
│       ├── DemoApplication.java
│       ├── config/DynamoDbConfig.java
│       ├── repository/PostRepository.java
│       ├── service/PostService.java
│       ├── controller/PostController.java
│       ├── dto/PostSearchRequest.java
│       └── exception/GlobalExceptionHandler.java
├── docker-compose.yml               # DynamoDB Local + Admin UI
├── create-table.sh                  # Creates the posts table
├── settings.gradle.kts              # Multi-module config
├── build.gradle.kts                 # Root build (Java 21, Lombok)
└── gradle.properties                # Micronaut/Spring Boot/Lombok versions
```

## Shared API endpoints

All endpoints under `/api/posts`. Both demos expose these base operations:

| Method   | Path                                     | Description                |
|----------|------------------------------------------|----------------------------|
| `POST`   | `/api/posts`                             | Create a post              |
| `GET`    | `/api/posts/{subreddit}/{id}`            | Get a post by ID           |
| `PUT`    | `/api/posts/{subreddit}/{id}`            | Update a post              |
| `DELETE` | `/api/posts/{subreddit}/{id}`            | Delete a post              |
| `GET`    | `/api/posts/{subreddit}`                 | List posts (paginated)     |
| `GET`    | `/api/posts/{subreddit}/search`          | Search with filters        |
| `GET`    | `/api/posts/{subreddit}/paginated`       | Paginated list with cursor |
| `GET`    | `/api/posts/{subreddit}/author/{author}` | Posts by author            |
| `GET`    | `/api/posts/{subreddit}/recent`          | Recent posts (N hours)     |

### Micronaut-only endpoints

| Method | Path                            | Description                               |
|--------|---------------------------------|-------------------------------------------|
| `GET`  | `/api/posts/{subreddit}/count`  | Count posts in subreddit                  |
| `GET`  | `/api/posts/{subreddit}/stream` | Reactive stream of posts (`SdkPublisher`) |

### Spring boot-only dndpoints

| Method   | Path                                             | Description                    |
|----------|--------------------------------------------------|--------------------------------|
| `POST`   | `/api/posts/batch`                               | Batch write posts              |
| `POST`   | `/api/posts/batch-get`                           | Batch get posts by keys        |
| `POST`   | `/api/posts/transact`                            | Transact write (2 items)       |
| `PATCH`  | `/api/posts/{subreddit}/{id}`                    | Partial update                 |
| `DELETE` | `/api/posts/{subreddit}/{id}?returnDeleted=true` | Delete and return deleted item |

## Build & Test

```sh
# Compile all modules
./gradlew compileJava

# Compile tests
./gradlew compileTestJava

# Run unit tests
./gradlew test

# Run integration tests (requires Docker — Testcontainers starts DynamoDB Local)
./gradlew :micronaut-demo:integrationTest
./gradlew :spring-boot-demo:integrationTest

# Full check
./gradlew check
```

## Data Generation

Both demos can seed the `posts` table with random data on startup:

```sh
# Micronaut
MICRONAUT_ENVIRONMENTS=local ./gradlew :micronaut-demo:run -Dapp.data-generator.enabled=true -Dapp.data-generator.count=5000

# Spring Boot
./gradlew :spring-boot-demo:bootRun -Dapp.data-generator.enabled=true -Dapp.data-generator.count=5000
```

## Tech Stack

- **Java 21**
- **Gradle 9.x**
- **dynamodb-simplified-core 0.1.0** ([Maven Central](https://central.sonatype.com/artifact/dev.hogwai/dynamodb-simplified-core))
- **Micronaut 4.10** (Netty, Serde, Validation)
- **Spring Boot 4.1** (Web, Validation)
- **AWS SDK v2** (DynamoDB Enhanced Client)
- **Lombok** 1.18.42
- **Testcontainers** (DynamoDB Local integration tests)
- **Docker** (DynamoDB Local + Admin UI)

## Configuration

Default settings in `application.properties` / `application.yml`:

| Property                         | Default                 | Description             |
|----------------------------------|-------------------------|-------------------------|
| `aws.dynamodb.endpoint-override` | `http://localhost:8000` | DynamoDB Local endpoint |
| `aws.region`                     | `eu-west-3`             | AWS region              |
| `app.data-generator.enabled`     | `false`                 | Seed data on startup    |
| `app.data-generator.count`       | `25000`                 | Number of posts to seed |
