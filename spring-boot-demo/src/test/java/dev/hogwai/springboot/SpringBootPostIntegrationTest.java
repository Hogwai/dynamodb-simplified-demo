package dev.hogwai.springboot;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.model.Post;
import dev.hogwai.springboot.dto.BatchMixedRequest;
import dev.hogwai.springboot.dto.PartiQLRequest;
import dev.hogwai.springboot.dto.TransactAdvancedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringBootPostIntegrationTest {

    private static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    static {
        dynamoDb.start();
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
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("author").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("createdUtc").attributeType(ScalarAttributeType.N).build())
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("author-index")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("author").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("createdUtc").keyType(KeyType.RANGE).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint-override",
                () -> "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000));
    }

    @AfterAll
    static void stopContainer() {
        dynamoDb.stop();
    }

    @Autowired
    DynamoDbClient dynamoDbClient;

    @LocalServerPort
    int port;

    RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = RestClient.create("http://localhost:" + port);
        // Ensure table exists (created by static initializer but may not be visible to app's client)
        var tables = dynamoDbClient.listTables().tableNames();
        if (!tables.contains("posts")) {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName("posts")
                    .keySchema(
                            KeySchemaElement.builder().attributeName("subreddit").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("subreddit").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("author").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("createdUtc").attributeType(ScalarAttributeType.N).build())
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("author-index")
                                    .keySchema(
                                            KeySchemaElement.builder().attributeName("author").keyType(KeyType.HASH).build(),
                                            KeySchemaElement.builder().attributeName("createdUtc").keyType(KeyType.RANGE).build())
                                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                    .build())
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .build());
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

        ResponseEntity<Post> createResponse = restClient.post()
                .uri("/api/posts")
                .body(request)
                .retrieve()
                .toEntity(Post.class);
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        Post created = createResponse.getBody();
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(subreddit, created.getSubreddit());
        assertEquals("test-author", created.getAuthor());
        assertEquals("Test Title", created.getTitle());
        assertEquals("Test content", created.getSelfText());
        assertTrue(created.getKeywords().containsAll(Set.of("test", "integration")));
        assertNotNull(created.getCreatedUtc());
        assertNotNull(created.getPermalink());

        ResponseEntity<Post> getResponse = restClient.get()
                .uri("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .retrieve()
                .toEntity(Post.class);
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        Post retrieved = getResponse.getBody();
        assertNotNull(retrieved);
        assertEquals(created.getId(), retrieved.getId());
        assertEquals(subreddit, retrieved.getSubreddit());
        assertEquals("test-author", retrieved.getAuthor());
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

        Post created = restClient.post()
                .uri("/api/posts")
                .body(createRequest)
                .retrieve()
                .toEntity(Post.class).getBody();
        assertNotNull(created);

        Post updatePayload = Post.builder()
                .title("Updated Title")
                .selfText("Updated content")
                .keywords(Set.of("updated"))
                .build();

        ResponseEntity<Post> updateResponse = restClient.put()
                .uri("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .body(updatePayload)
                .retrieve()
                .toEntity(Post.class);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());
        Post updated = updateResponse.getBody();
        assertNotNull(updated);
        assertEquals("Updated Title", updated.getTitle());
        assertEquals("Updated content", updated.getSelfText());
        assertTrue(updated.getKeywords().contains("updated"));
        assertEquals(created.getAuthor(), updated.getAuthor());
        assertEquals(created.getCreatedUtc(), updated.getCreatedUtc());
    }

    @Test
    void testDeletePost() {
        String subreddit = "test-delete-" + UUID.randomUUID();
        CreatePostRequest createRequest = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("delete-author")
                .title("Delete Test")
                .build();
        Post created = restClient.post()
                .uri("/api/posts")
                .body(createRequest)
                .retrieve()
                .toEntity(Post.class).getBody();
        assertNotNull(created);

        ResponseEntity<Void> deleteResponse = restClient.delete()
                .uri("/api/posts/{subreddit}/{id}", subreddit, created.getId())
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        assertThrows(HttpClientErrorException.class, () ->
                getPostExpecting404(subreddit, created.getId()));
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
            restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class);
        }

        // List with limit returns PagedResponse
        ResponseEntity<PagedResponse> listResponse = restClient.get()
                .uri("/api/posts/{subreddit}?limit=3", subreddit)
                .retrieve()
                .toEntity(PagedResponse.class);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        PagedResponse listResult = listResponse.getBody();
        assertNotNull(listResult);
        assertEquals(3, listResult.getItems().size());
    }

    @Test
    void testSearchByAuthor() {
        String subreddit = "test-auth-search-" + UUID.randomUUID();
        String author = "search-author-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author(author)
                    .title("Post " + i)
                    .build();
            restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class);
        }
        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit)
                    .author("other-author-" + UUID.randomUUID())
                    .title("Other " + i)
                    .build();
            restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class);
        }

        ResponseEntity<List<Post>> searchResponse = restClient.get()
                .uri("/api/posts/{subreddit}/search?author={author}", subreddit, author)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        List<Post> results = searchResponse.getBody();
        assertNotNull(results);
        assertEquals(3, results.size());
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
        restClient.post().uri("/api/posts").body(reqWithKw).retrieve().toEntity(Post.class);

        CreatePostRequest reqWithoutKw = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("author-1")
                .title("No Keyword")
                .build();
        restClient.post().uri("/api/posts").body(reqWithoutKw).retrieve().toEntity(Post.class);

        ResponseEntity<List<Post>> searchResponse = restClient.get()
                .uri("/api/posts/{subreddit}/search?keyword=special-keyword", subreddit)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, searchResponse.getStatusCode());
        List<Post> results = searchResponse.getBody();
        assertNotNull(results);
        assertEquals(1, results.size());
    }

    @Test
    void testBatchWrite() {
        String subreddit = "test-batch-write-" + UUID.randomUUID();
        List<CreatePostRequest> requests = List.of(
                CreatePostRequest.builder()
                        .subreddit(subreddit)
                        .author("batch-author")
                        .title("Batch Post 1")
                        .build(),
                CreatePostRequest.builder()
                        .subreddit(subreddit)
                        .author("batch-author")
                        .title("Batch Post 2")
                        .build()
        );

        ResponseEntity<List<Post>> batchResponse = restClient.post()
                .uri("/api/posts/batch")
                .body(requests)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.CREATED, batchResponse.getStatusCode());
        List<Post> batchResult = batchResponse.getBody();
        assertNotNull(batchResult);
        assertEquals(2, batchResult.size());
    }

    @Test
    void testBatchGet() {
        String subreddit = "test-batch-get-" + UUID.randomUUID();
        CreatePostRequest req1 = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("bg-author")
                .title("BG Post 1")
                .build();
        CreatePostRequest req2 = CreatePostRequest.builder()
                .subreddit(subreddit)
                .author("bg-author")
                .title("BG Post 2")
                .build();

        Post post1 = restClient.post().uri("/api/posts").body(req1).retrieve().toEntity(Post.class).getBody();
        Post post2 = restClient.post().uri("/api/posts").body(req2).retrieve().toEntity(Post.class).getBody();
        assertNotNull(post1);
        assertNotNull(post2);

        List<String[]> keys = List.of(
                new String[]{subreddit, post1.getId()},
                new String[]{subreddit, post2.getId()}
        );

        ResponseEntity<List<Post>> batchGetResponse = restClient.post()
                .uri("/api/posts/batch-get")
                .body(keys)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, batchGetResponse.getStatusCode());
        List<Post> batchGetResult = batchGetResponse.getBody();
        assertNotNull(batchGetResult);
        assertEquals(2, batchGetResult.size());
    }

    @Test
    void testGsiQueryByAuthor() {
        String subreddit = "test-gsi-" + UUID.randomUUID();
        String author = "gsi-author-" + UUID.randomUUID();

        for (int i = 0; i < 2; i++) {
            CreatePostRequest req = CreatePostRequest.builder()
                    .subreddit(subreddit).author(author).title("GSI Post " + i).build();
            restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class);
        }

        ResponseEntity<List<Post>> response = restClient.get()
                .uri("/api/posts/by-author/{author}", author)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Post> results = response.getBody();
        assertNotNull(results);
        assertTrue(results.size() >= 2);
        results.forEach(p -> assertEquals(author, p.getAuthor()));
    }

    @Test
    void testBatchWriteWithDeletes() {
        String subreddit = "test-bm-" + UUID.randomUUID();
        String author = "bm-author";

        CreatePostRequest req = CreatePostRequest.builder()
                .subreddit(subreddit).author(author).title("Batch Mixed").build();
        Post created = restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class).getBody();
        assertNotNull(created);

        // Batch-mixed: create a new post and delete the existing one
        CreatePostRequest newReq = CreatePostRequest.builder()
                .subreddit(subreddit).author(author).title("New Mixed").build();
        BatchMixedRequest mixedReq = BatchMixedRequest.builder()
                .puts(List.of(newReq))
                .deletes(List.of(List.of(subreddit, created.getId())))
                .build();

        ResponseEntity<Void> response = restClient.post()
                .uri("/api/posts/batch-mixed")
                .body(mixedReq)
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatus.CREATED, response.getStatusCode());

        // Verify the deleted post is gone
        assertThrows(HttpClientErrorException.class, () ->
                getPostExpecting404(subreddit, created.getId()));
    }

    @Test
    void testTransactWriteAdvanced() {
        String subreddit = "test-ta-" + UUID.randomUUID();
        String author = "ta-author";

        // Create a target post for the condition check
        CreatePostRequest checkReq = CreatePostRequest.builder()
                .subreddit(subreddit).author(author).title("Check Target").build();
        Post checkPost = restClient.post().uri("/api/posts").body(checkReq).retrieve().toEntity(Post.class).getBody();
        assertNotNull(checkPost);

        // TransactWriteAdvanced: put a new post atomically + conditionCheck an existing item
        CreatePostRequest newReq = CreatePostRequest.builder()
                .subreddit(subreddit).author(author).title("TA New").build();
        TransactAdvancedRequest taReq = TransactAdvancedRequest.builder()
                .puts(List.of(newReq))
                .conditionCheck(List.of(subreddit, checkPost.getId()))
                .build();

        ResponseEntity<Void> response = restClient.post()
                .uri("/api/posts/transact-advanced")
                .body(taReq)
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testTransactGet() {
        String subreddit = "test-tg-" + UUID.randomUUID();
        CreatePostRequest req1 = CreatePostRequest.builder()
                .subreddit(subreddit).author("tg-author").title("TG 1").build();
        CreatePostRequest req2 = CreatePostRequest.builder()
                .subreddit(subreddit).author("tg-author").title("TG 2").build();

        Post post1 = restClient.post().uri("/api/posts").body(req1).retrieve().toEntity(Post.class).getBody();
        Post post2 = restClient.post().uri("/api/posts").body(req2).retrieve().toEntity(Post.class).getBody();
        assertNotNull(post1);
        assertNotNull(post2);

        List<List<String>> keys = List.of(
                List.of(subreddit, post1.getId()),
                List.of(subreddit, post2.getId())
        );

        ResponseEntity<List<Post>> response = restClient.post()
                .uri("/api/posts/transact-get")
                .body(keys)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Post> results = response.getBody();
        assertNotNull(results);
        assertEquals(2, results.size());
    }

    @Test
    void testPartiQL() {
        String subreddit = "test-partiql-" + UUID.randomUUID();
        CreatePostRequest req = CreatePostRequest.builder()
                .subreddit(subreddit).author("partiql-author").title("PartiQL Test").build();

        Post created = restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class).getBody();
        assertNotNull(created);

        PartiQLRequest partiqlReq = PartiQLRequest.builder()
                .statement("SELECT * FROM \"posts\" WHERE subreddit='%s' AND id='%s'".formatted(subreddit, created.getId()))
                .build();

        ResponseEntity<String> response = restClient.post()
                .uri("/api/posts/partiql")
                .body(partiqlReq)
                .retrieve()
                .toEntity(String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void testListTables() {
        ResponseEntity<List<String>> response = restClient.get()
                .uri("/api/posts/admin/tables")
                .retrieve()
                .toEntity(new ParameterizedTypeReference<>() {
                });
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<String> tables = response.getBody();
        assertNotNull(tables);
        assertTrue(tables.contains("posts"));
    }

    @Test
    void testEntityTable() {
        String subreddit = "test-entity-" + UUID.randomUUID();
        CreatePostRequest req = CreatePostRequest.builder()
                .subreddit(subreddit).author("entity-author").title("Entity Test").build();

        Post created = restClient.post().uri("/api/posts").body(req).retrieve().toEntity(Post.class).getBody();
        assertNotNull(created);

        ResponseEntity<Post> entityResponse = restClient.get()
                .uri("/api/posts/entity/{pk}/{sk}", subreddit, created.getId())
                .retrieve()
                .toEntity(Post.class);
        assertEquals(HttpStatus.OK, entityResponse.getStatusCode());
        Post entityResult = entityResponse.getBody();
        assertNotNull(entityResult);
        assertEquals(created.getId(), entityResult.getId());
    }

    /**
     * Local DTO for deserializing paginated responses.
     * The shared dev.hogwai.demo.dto.PagedResponse cannot be deserialized by Jackson
     * because it lacks a default constructor and setters.
     */
    private ResponseEntity<Post> getPostExpecting404(String subreddit, String id) {
        return restClient.get()
                .uri("/api/posts/{subreddit}/{id}", subreddit, id)
                .retrieve()
                .toEntity(Post.class);
    }

    @SuppressWarnings("unused")
    static class PagedResponse {
        private List<Post> items;
        private String nextCursor;
        private boolean hasMore;

        public List<Post> getItems() {
            return items;
        }

        public void setItems(List<Post> items) {
            this.items = items;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public void setNextCursor(String nextCursor) {
            this.nextCursor = nextCursor;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public void setHasMore(boolean hasMore) {
            this.hasMore = hasMore;
        }
    }
}
