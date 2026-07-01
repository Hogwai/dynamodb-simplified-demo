package dev.hogwai.quarkus;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.model.Post;
import dev.hogwai.quarkus.dto.PartiQLRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.RestAssured;
import io.restassured.common.mapper.TypeRef;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusIntegrationTest
@QuarkusTestResource(DynamoDbTestResource.class)
class QuarkusPostIntegrationTest {

    @BeforeAll
    static void ensureTableExists() {
        if (DynamoDbTestResource.dynamoDbEndpoint != null) {
            DynamoDbTestResource.createTableIfNeeded(DynamoDbTestResource.dynamoDbEndpoint);
        }
    }

    @Test
    void testCreateAndGetPost() {
        String subreddit = "test-create-" + UUID.randomUUID();
        CreatePostRequest request = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("test-author")
                .title("Test Title")
                .selfText("Test content")
                .keywords(Set.of("test", "integration"))
                .build();

        Post created = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(request)
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);

        assertNotNull(created.getId());
        assertEquals(subreddit, created.getSubreddit());
        assertEquals("test-author", created.getAuthor());

        Post retrieved = RestAssured.when()
                .get("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .then().statusCode(200)
                .extract().as(Post.class);

        assertEquals(created.getId(), retrieved.getId());
    }

    @Test
    void testUpdatePost() {
        String subreddit = "test-update-" + UUID.randomUUID();
        CreatePostRequest createRequest = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("update-author")
                .title("Original Title")
                .selfText("Original content")
                .build();

        Post created = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(createRequest)
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);

        Post updatePayload = Post.builder()
                .title("Updated Title")
                .selfText("Updated content")
                .build();

        Post updated = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(updatePayload)
                .when().put("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .then().statusCode(200)
                .extract().as(Post.class);

        assertEquals("Updated Title", updated.getTitle());
        assertEquals(created.getAuthor(), updated.getAuthor());
    }

    @Test
    void testDeletePost() {
        String subreddit = "test-delete-" + UUID.randomUUID();
        CreatePostRequest createRequest = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("delete-author")
                .title("Delete Test")
                .build();

        Post created = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(createRequest)
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);

        RestAssured.when()
                .delete("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .then().statusCode(204);

        RestAssured.when()
                .get("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .then().statusCode(404);
    }

    @Test
    void testSearchByAuthor() {
        String subreddit = "test-auth-search-" + UUID.randomUUID();
        String author = "search-author-" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit).author(author).title("Post " + i)
                    .build();
            RestAssured.given()
                    .contentType(ContentType.JSON).body(req)
                    .when().post("/api/posts")
                    .then().statusCode(201);
        }

        List<Post> results = RestAssured.when()
                .get("/api/posts/{subreddit}/search?author={author}", subreddit, author)
                .then().statusCode(200)
                .extract().as(new TypeRef<>() {
                });

        assertEquals(3, results.size());
        results.forEach(post -> assertEquals(author, post.getAuthor()));
    }

    @Test
    void testCountPosts() {
        String subreddit = "test-count-" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit).author("count-author").title("Post " + i)
                    .build();
            RestAssured.given()
                    .contentType(ContentType.JSON).body(req)
                    .when().post("/api/posts")
                    .then().statusCode(201);
        }

        RestAssured.when()
                .get("/api/posts/{subreddit}/count", subreddit)
                .then().statusCode(200)
                .body(equalTo("3"));
    }

    @Test
    void testListPosts() {
        String subreddit = "test-list-" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit).author("list-author").title("Post " + i)
                    .build();
            RestAssured.given()
                    .contentType(ContentType.JSON).body(req)
                    .when().post("/api/posts")
                    .then().statusCode(201);
        }

        List<Post> posts = RestAssured.when()
                .get("/api/posts/{subreddit}?limit=3", subreddit)
                .then().statusCode(200)
                .extract().as(new TypeRef<>() {
                });

        assertEquals(3, posts.size());
    }

    // region Client-level operation tests

    @Test
    void testGsiQueryByAuthor() {
        String subreddit = "test-gsi-" + UUID.randomUUID();
        String author = "gsi-author-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit).author(author).title("GSI Post " + i)
                    .build();
            RestAssured.given()
                    .contentType(ContentType.JSON).body(req)
                    .when().post("/api/posts")
                    .then().statusCode(201);
        }

        List<Post> results = RestAssured.when()
                .get("/api/posts/by-author/{author}", author)
                .then().statusCode(200)
                .extract().as(new TypeRef<>() {
                });

        assertTrue(results.size() >= 2);
        results.forEach(p -> assertEquals(author, p.getAuthor()));
    }

    @Test
    void testEntityTable() {
        String subreddit = "test-entity-" + UUID.randomUUID();
        CreatePostRequest req = CreatePostRequest.builder()
                .subreddit(subreddit).author("entity-author").title("Entity Test")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON).body(req)
                .when().post("/api/posts")
                .then().statusCode(201);

        // Retrieve by querying all posts to find the created one
        List<Post> recent = RestAssured.when()
                .get("/api/posts/{subreddit}?limit=50", subreddit)
                .then().statusCode(200)
                .extract().as(new TypeRef<>() {
                });
        assertFalse(recent.isEmpty());
        String createdId = recent.getFirst().getId();

        // Get via entity endpoint
        RestAssured.when()
                .get("/api/posts/entity/{pk}/{sk}", subreddit, createdId)
                .then().statusCode(200)
                .body("id", equalTo(createdId));
    }

    @Test
    void testTransactGet() {
        String subreddit = "test-tg-" + UUID.randomUUID();

        Post post1 = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(CreatePostRequest.builder()
                        .subreddit(subreddit).author("tg-author").title("TG 1").build())
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);
        Post post2 = RestAssured.given()
                .contentType(ContentType.JSON)
                .body(CreatePostRequest.builder()
                        .subreddit(subreddit).author("tg-author").title("TG 2").build())
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);

        List<List<String>> keys = List.of(
                List.of(subreddit, post1.getId()),
                List.of(subreddit, post2.getId()));

        List<Post> results = RestAssured.given()
                .contentType(ContentType.JSON).body(keys)
                .when().post("/api/posts/transact-get")
                .then().statusCode(200)
                .extract().as(new TypeRef<>() {
                });
        assertEquals(2, results.size());
    }

    @Test
    void testPartiQL() {
        String subreddit = "test-partiql-" + UUID.randomUUID();
        CreatePostRequest req = CreatePostRequest.builder()
                .subreddit(subreddit).author("partiql-author").title("PartiQL Test")
                .build();

        Post created = RestAssured.given()
                .contentType(ContentType.JSON).body(req)
                .when().post("/api/posts")
                .then().statusCode(201)
                .extract().as(Post.class);

        PartiQLRequest partiqlReq = new PartiQLRequest();
        partiqlReq.setStatement(
                "SELECT * FROM \"posts\" WHERE subreddit='%s' AND id='%s'".formatted(subreddit, created.getId()));

        // Just verify the endpoint returns 200 — the response structure is complex
        RestAssured.given()
                .contentType(ContentType.JSON).body(partiqlReq)
                .when().post("/api/posts/partiql")
                .then().statusCode(200);
    }

    // endregion
}
