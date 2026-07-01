package dev.hogwai.springboot.service;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.exception.PostNotFoundException;
import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import dev.hogwai.springboot.dto.PostSearchRequest;
import dev.hogwai.springboot.repository.PostRepository;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.time.Instant;
import java.util.*;

@Service
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    // region Base CRUD

    public Post createPost(CreatePostRequest request) {
        Post post = Post.builder()
                .id(generateId())
                .subreddit(request.getSubreddit())
                .author(request.getAuthor())
                .title(request.getTitle())
                .selfText(request.getSelfText())
                .keywords(request.getKeywords())
                .createdUtc(Instant.now().getEpochSecond())
                .permalink(buildPermalink(request.getSubreddit()))
                .build();

        repository.save(post);
        return post;
    }

    public Optional<Post> getPost(String subreddit, String id) {
        return repository.findById(subreddit, id);
    }

    public Post updatePost(String subreddit, String id, Post updatedPost) {
        Post existing = repository.findById(subreddit, id)
                .orElseThrow(() -> new PostNotFoundException(subreddit, id));

        updatedPost.setSubreddit(subreddit);
        updatedPost.setId(id);
        updatedPost.setCreatedUtc(existing.getCreatedUtc());
        updatedPost.setAuthor(existing.getAuthor());

        return repository.update(updatedPost);
    }

    public void deletePost(String subreddit, String id) {
        repository.delete(subreddit, id);
    }

    public PagedResponse<Post> getPostsPaginated(String subreddit, int pageSize, String cursor) {
        Map<String, AttributeValue> lastKey = decodeCursor(cursor);

        PagedResult<Post> result = repository.findBySubredditPaginated(subreddit, pageSize, lastKey);

        return PagedResponse.<Post>builder()
                .items(result.items())
                .nextCursor(encodeCursor(result.lastEvaluatedKey()))
                .hasMore(result.hasMorePages())
                .build();
    }

    public long countPosts(String subreddit) {
        return repository.countBySubreddit(subreddit);
    }

    public List<Post> search(PostSearchRequest request) {
        PostSearchCriteria criteria = PostSearchCriteria.builder()
                .subreddit(request.getSubreddit())
                .author(request.getAuthor())
                .keyword(request.getKeyword())
                .sinceUtc(request.getSinceUtc())
                .untilUtc(request.getUntilUtc())
                .titleContains(request.getTitleContains())
                .minKeywords(request.getMinKeywords())
                .limit(request.getLimit())
                .build();

        return repository.search(criteria);
    }

    // endregion

    // region Batch Write

    public List<Post> batchWrite(List<CreatePostRequest> requests) {
        List<Post> posts = requests.stream()
                .map(req -> Post.builder()
                        .id(generateId())
                        .subreddit(req.getSubreddit())
                        .author(req.getAuthor())
                        .title(req.getTitle())
                        .selfText(req.getSelfText())
                        .keywords(req.getKeywords())
                        .createdUtc(Instant.now().getEpochSecond())
                        .permalink(buildPermalink(req.getSubreddit()))
                        .build())
                .toList();

        repository.batchWrite(posts);
        return posts;
    }

    // endregion

    // region Batch Get

    public List<Post> batchGet(List<String[]> keys) {
        List<Post> postKeys = keys.stream()
                .map(k -> Post.builder().subreddit(k[0]).id(k[1]).build())
                .toList();
        return repository.batchGet(postKeys);
    }

    // endregion

    // region Transact Write

    public List<Post> transactWrite(CreatePostRequest request1, CreatePostRequest request2) {
        Post post1 = Post.builder()
                .id(generateId())
                .subreddit(request1.getSubreddit())
                .author(request1.getAuthor())
                .title(request1.getTitle())
                .selfText(request1.getSelfText())
                .keywords(request1.getKeywords())
                .createdUtc(Instant.now().getEpochSecond())
                .permalink(buildPermalink(request1.getSubreddit()))
                .build();

        Post post2 = Post.builder()
                .id(generateId())
                .subreddit(request2.getSubreddit())
                .author(request2.getAuthor())
                .title(request2.getTitle())
                .selfText(request2.getSelfText())
                .keywords(request2.getKeywords())
                .createdUtc(Instant.now().getEpochSecond())
                .permalink(buildPermalink(request2.getSubreddit()))
                .build();

        repository.transactWrite(post1, post2);
        return List.of(post1, post2);
    }

    // endregion

    // region Partial Update

    public Optional<Post> updatePartial(String subreddit, String id, Map<String, Object> updates) {
        return repository.updatePartial(subreddit, id, updates);
    }

    // endregion

    // region Delete with Return Values

    public Optional<Post> deleteAndReturn(String subreddit, String id) {
        return repository.deleteAndReturn(subreddit, id);
    }

    // endregion

    // region Batch Write with Deletes (from CreatePostRequest)

    public CrossTableBatchWriteResult batchWriteWithDeletes(List<CreatePostRequest> requests, List<List<String>> deleteKeys) {
        List<Post> puts = requests.stream()
                .map(req -> Post.builder()
                        .id(generateId())
                        .subreddit(req.getSubreddit())
                        .author(req.getAuthor())
                        .title(req.getTitle())
                        .selfText(req.getSelfText())
                        .keywords(req.getKeywords())
                        .createdUtc(Instant.now().getEpochSecond())
                        .permalink(buildPermalink(req.getSubreddit()))
                        .build())
                .toList();
        return repository.batchWriteWithDeletes(puts,
                deleteKeys.stream().map(k -> new String[]{k.getFirst(), k.get(1)}).toList());
    }

    // endregion

    // region GSI Query

    public List<Post> getPostsByAuthorGsi(String author) {
        return repository.queryByAuthorGsi(author);
    }

    // endregion

    // region Advanced Transact Write

    public void transactWriteAdvanced(List<CreatePostRequest> requests, List<String> conditionCheck) {
        long now = Instant.now().getEpochSecond();
        Post post1 = Post.builder()
                .id(generateId())
                .subreddit(requests.getFirst().getSubreddit())
                .author(requests.getFirst().getAuthor())
                .title(requests.getFirst().getTitle())
                .selfText(requests.getFirst().getSelfText())
                .keywords(requests.getFirst().getKeywords())
                .createdUtc(now)
                .permalink(buildPermalink(requests.getFirst().getSubreddit()))
                .build();
        // Condition check: verify item exists and has createdUtc >= 0
        repository.transactWriteAdvanced(post1, conditionCheck.get(0), conditionCheck.get(1), 0L);
    }

    // endregion

    // region Transact Get

    public List<Post> transactGet(List<String[]> keys) {
        return repository.transactGet(keys);
    }

    // endregion

    // region PartiQL

    public ExecuteStatementResponse executePartiQL(String statement) {
        return repository.executePartiQL(statement);
    }

    // endregion

    // region List Tables

    public List<String> listTables() {
        return repository.listTables();
    }

    // endregion

    // region Entity Table

    public void entityPut(Post post) {
        repository.entityPut(post);
    }

    public void entityPut(CreatePostRequest request) {
        Post post = Post.builder()
                .id(generateId())
                .subreddit(request.getSubreddit())
                .author(request.getAuthor())
                .title(request.getTitle())
                .selfText(request.getSelfText())
                .keywords(request.getKeywords())
                .createdUtc(Instant.now().getEpochSecond())
                .permalink(buildPermalink(request.getSubreddit()))
                .build();
        repository.entityPut(post);
    }

    public Post entityGet(String pk, String sk) {
        return repository.entityGet(pk, sk);
    }

    // endregion

    // region Utility Methods

    private String buildPermalink(String subreddit) {
        return "/r/%s/comments/%s".formatted(subreddit, generateId());
    }

    private String encodeCursor(Map<String, AttributeValue> lastKey) {
        if (lastKey == null || lastKey.isEmpty()) {
            return null;
        }
        try {
            StringBuilder sb = new StringBuilder();
            lastKey.forEach((k, v) -> {
                if (!sb.isEmpty()) sb.append("|");
                sb.append(k).append("=").append(v.s() != null ? v.s() : v.n());
            });
            return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            Map<String, AttributeValue> result = new HashMap<>();
            for (String part : decoded.split("\\|")) {
                String[] kv = part.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0], AttributeValue.builder().s(kv[1]).build());
                }
            }
            return result;
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    // endregion
}
