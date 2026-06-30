package dev.hogwai.micronaut;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.model.Post;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import reactor.test.StepVerifier;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class MicronautPostIntegrationTest {

    private static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    static {
        dynamoDb.start();
        System.setProperty("aws.dynamodb.endpoint-override",
                "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000));

        try (var client = DynamoDbClient.builder()
                .region(Region.EU_WEST_3)
                .endpointOverride(URI.create("http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000)))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("fake", "fake")))
                .build()) {
            client.createTable(CreateTableRequest.builder()
                    .tableName("posts")
                    .keySchema(
                            KeySchemaElement.builder().attributeName("subreddit").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("subreddit").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        }
    }

    @AfterAll
    static void stopContainer() {
        dynamoDb.stop();
    }

    @Inject
    @Client("/")
    HttpClient client;

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

        var createdRef = new Post[1];

        StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", request), Post.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatus());
                    Post created = response.body();
                    assertNotNull(created);
                    assertNotNull(created.getId());
                    assertEquals(subreddit, created.getSubreddit());
                    assertEquals("test-author", created.getAuthor());
                    assertEquals("Test Title", created.getTitle());
                    assertEquals("Test content", created.getSelfText());
                    assertTrue(created.getKeywords().containsAll(Set.of("test", "integration")));
                    assertNotNull(created.getCreatedUtc());
                    assertNotNull(created.getPermalink());
                    createdRef[0] = created;
                })
                .verifyComplete();

        Post created = createdRef[0];

        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/" + created.getId()), Post.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    Post retrieved = response.body();
                    assertNotNull(retrieved);
                    assertEquals(created.getId(), retrieved.getId());
                    assertEquals(subreddit, retrieved.getSubreddit());
                    assertEquals("test-author", retrieved.getAuthor());
                })
                .verifyComplete();
    }

    @Test
    void testUpdatePost() {
        String subreddit = "test-update-" + UUID.randomUUID();
        CreatePostRequest createRequest = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("update-author")
                .title("Original Title")
                .selfText("Original content")
                .keywords(Set.of("original"))
                .build();

        var createdRef = new Post[1];

        StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", createRequest), Post.class))
                .assertNext(response -> createdRef[0] = response.body())
                .verifyComplete();

        Post created = createdRef[0];
        assertNotNull(created);

        Post updatePayload = Post.builder()
                .title("Updated Title")
                .selfText("Updated content")
                .keywords(Set.of("updated"))
                .build();

        StepVerifier.create(client.exchange(
                        HttpRequest.PUT("/api/posts/" + subreddit + "/" + created.getId(), updatePayload), Post.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    Post updated = response.body();
                    assertNotNull(updated);
                    assertEquals("Updated Title", updated.getTitle());
                    assertEquals("Updated content", updated.getSelfText());
                    assertTrue(updated.getKeywords().contains("updated"));
                    assertEquals(created.getAuthor(), updated.getAuthor());
                    assertEquals(created.getCreatedUtc(), updated.getCreatedUtc());
                })
                .verifyComplete();
    }

    @Test
    void testDeletePost() {
        String subreddit = "test-delete-" + UUID.randomUUID();
        CreatePostRequest createRequest = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("delete-author")
                .title("Delete Test")
                .build();

        var createdRef = new Post[1];

        StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", createRequest), Post.class))
                .assertNext(response -> createdRef[0] = response.body())
                .verifyComplete();

        Post created = createdRef[0];
        assertNotNull(created);

        StepVerifier.create(client.exchange(
                        HttpRequest.DELETE("/api/posts/" + subreddit + "/" + created.getId()), Void.class))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatus()))
                .verifyComplete();

        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/" + created.getId()), Post.class))
                .expectErrorSatisfies(error -> {
                    assertInstanceOf(HttpClientResponseException.class, error);
                    assertEquals(HttpStatus.NOT_FOUND, ((HttpClientResponseException) error).getStatus());
                })
                .verify();
    }

    @Test
    void testListPostsPaginated() {
        String subreddit = "test-paginated-" + UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author("author-" + i)
                    .title("Post " + i)
                    .build();
            StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", req), Post.class))
                    .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                    .verifyComplete();
        }

        // List with limit
        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "?limit=3"),
                        Argument.listOf(Post.class)))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(3, response.body().size());
                })
                .verifyComplete();

        // Paginated: first page
        var page1Ref = new PagedResponse[1];
        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/paginated?pageSize=3"),
                        PagedResponse.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    PagedResponse page1 = response.body();
                    assertNotNull(page1);
                    assertEquals(3, page1.getItems().size());
                    assertTrue(page1.isHasMore());
                    assertNotNull(page1.getNextCursor());
                    page1Ref[0] = page1;
                })
                .verifyComplete();

        // Paginated: second page
        PagedResponse page1 = page1Ref[0];
        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/paginated?pageSize=3&cursor=" + page1.getNextCursor()),
                        PagedResponse.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    PagedResponse page2 = response.body();
                    assertNotNull(page2);
                    assertEquals(2, page2.getItems().size());
                    assertFalse(page2.isHasMore());
                })
                .verifyComplete();
    }

    @Test
    void testSearchByAuthor() {
        String subreddit = "test-author-search-" + UUID.randomUUID();
        String author = "search-author-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author(author)
                    .title("Post " + i)
                    .build();
            StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", req), Post.class))
                    .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                    .verifyComplete();
        }
        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author("other-author-" + UUID.randomUUID())
                    .title("Other " + i)
                    .build();
            StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", req), Post.class))
                    .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                    .verifyComplete();
        }

        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/search?author=" + author),
                        Argument.listOf(Post.class)))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    List<Post> results = response.body();
                    assertNotNull(results);
                    assertEquals(3, results.size());
                    results.forEach(post -> assertEquals(author, post.getAuthor()));
                })
                .verifyComplete();
    }

    @Test
    void testSearchByKeyword() {
        String subreddit = "test-kw-search-" + UUID.randomUUID();
        CreatePostRequest reqWithKw = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("author-1")
                .title("Has Keyword")
                .keywords(Set.of("special-keyword", "other"))
                .build();
        StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", reqWithKw), Post.class))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();

        CreatePostRequest reqWithoutKw = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("author-1")
                .title("No Keyword")
                .build();
        StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", reqWithoutKw), Post.class))
                .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                .verifyComplete();

        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/search?keyword=special-keyword"),
                        Argument.listOf(Post.class)))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    List<Post> results = response.body();
                    assertNotNull(results);
                    assertEquals(1, results.size());
                    assertEquals("Has Keyword", results.getFirst().getTitle());
                })
                .verifyComplete();
    }

    @Test
    void testCountPosts() {
        String subreddit = "test-count-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author("count-author")
                    .title("Count Post " + i)
                    .build();
            StepVerifier.create(client.exchange(HttpRequest.POST("/api/posts", req), Post.class))
                    .assertNext(response -> assertEquals(HttpStatus.CREATED, response.getStatus()))
                    .verifyComplete();
        }

        StepVerifier.create(client.exchange(
                        HttpRequest.GET("/api/posts/" + subreddit + "/count"), Long.class))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatus());
                    assertEquals(3L, response.body());
                })
                .verifyComplete();
    }

    /**
     * Local DTO for deserializing paginated responses.
     * The shared dev.hogwai.demo.dto.PagedResponse cannot be deserialized by Jackson
     * because it lacks a default constructor and setters.
     */
    @SuppressWarnings("unused")
    static class PagedResponse {
        private List<Post> items;
        private String nextCursor;
        private boolean hasMore;

        public List<Post> getItems() { return items; }
        public void setItems(List<Post> items) { this.items = items; }
        public String getNextCursor() { return nextCursor; }
        public void setNextCursor(String nextCursor) { this.nextCursor = nextCursor; }
        public boolean isHasMore() { return hasMore; }
        public void setHasMore(boolean hasMore) { this.hasMore = hasMore; }
    }
}
