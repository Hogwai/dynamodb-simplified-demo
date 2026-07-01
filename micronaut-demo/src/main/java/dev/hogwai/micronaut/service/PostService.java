package dev.hogwai.micronaut.service;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.exception.PostNotFoundException;
import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.micronaut.dto.PostSearchRequest;
import dev.hogwai.micronaut.repository.PostAsyncRepository;
import jakarta.inject.Singleton;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PostService {

    private final PostAsyncRepository repository;

    public PostService(PostAsyncRepository repository) {
        this.repository = repository;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    // region Reading

    public CompletableFuture<Optional<Post>> getPost(String subreddit, String id) {
        return repository.findById(subreddit, id);
    }

    public CompletableFuture<List<Post>> getRecentPosts(String subreddit, int limit) {
        return repository.findBySubreddit(subreddit, limit);
    }

    public CompletableFuture<List<Post>> getPostsByAuthor(String subreddit, String author) {
        return repository.findByAuthor(subreddit, author);
    }

    public CompletableFuture<List<Post>> getPostsLastHours(String subreddit, int hours) {
        long since = Instant.now().minus(hours, ChronoUnit.HOURS).getEpochSecond();
        return repository.findCreatedAfter(subreddit, since);
    }

    // endregion

    // region Search

    public CompletableFuture<List<Post>> search(PostSearchRequest request) {
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

    // region Pagination

    public CompletableFuture<PagedResponse<Post>> getPostsPaginated(String subreddit, int pageSize, String cursor) {
        Map<String, AttributeValue> lastKey = decodeCursor(cursor);

        return repository.findBySubredditPaginated(subreddit, pageSize, lastKey)
                .thenApply(result -> PagedResponse.<Post>builder()
                        .items(result.items())
                        .nextCursor(encodeCursor(result.lastEvaluatedKey()))
                        .hasMore(result.hasMorePages())
                        .build());
    }

    // endregion

    // region Creation

    public CompletableFuture<Post> createPost(CreatePostRequest request) {
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

        return repository.saveIfNotExists(post)
                .thenApply(ignored -> post);
    }

    // endregion

    // region Update

    public CompletableFuture<Post> updatePost(String subreddit, String id, Post updatedPost) {
        return repository.findById(subreddit, id)
                .thenCompose(existing -> {
                    if (existing.isEmpty()) {
                        throw new PostNotFoundException(subreddit, id);
                    }
                    Post existingPost = existing.get();

                    // Preserve immutable fields
                    updatedPost.setSubreddit(subreddit);
                    updatedPost.setId(id);
                    updatedPost.setCreatedUtc(existingPost.getCreatedUtc());
                    updatedPost.setAuthor(existingPost.getAuthor());

                    return repository.update(updatedPost);
                });
    }

    // endregion

    // region Deletion

    public CompletableFuture<Void> deletePost(String subreddit, String id) {
        return repository.delete(subreddit, id);
    }

    // endregion

    // region Count

    public CompletableFuture<Long> countPosts(String subreddit) {
        return repository.countBySubreddit(subreddit);
    }

    // endregion

    // region Stream

    public SdkPublisher<Post> streamPosts(String subreddit) {
        return repository.streamBySubreddit(subreddit);
    }

    // endregion

    // region GSI Query

    public CompletableFuture<List<Post>> getPostsByAuthorGsi(String author) {
        return repository.queryByAuthorGsi(author);
    }

    // endregion

    // region Transact Get

    public CompletableFuture<List<Post>> transactGet(List<List<String>> keys) {
        return repository.transactGet(keys);
    }

    // endregion

    // region PartiQL

    public CompletableFuture<List<Map<String, AttributeValue>>> executePartiQL(String statement) {
        return repository.executePartiQL(statement);
    }

    // endregion

    // region List Tables

    public CompletableFuture<List<String>> listTables() {
        return repository.listTables();
    }

    // endregion

    // region Entity Table

    public CompletableFuture<Void> entityPut(CreatePostRequest request) {
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
        return repository.entityPut(post);
    }

    public CompletableFuture<Post> entityGet(String pk, String sk) {
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
        // Encode as Base64 for transport
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
