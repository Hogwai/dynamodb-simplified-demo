package dev.hogwai.micronaut.service;

import dev.hogwai.micronaut.dto.PostSearchRequest;
import dev.hogwai.micronaut.repository.PostAsyncRepository;
import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.exception.PostNotFoundException;
import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import jakarta.inject.Singleton;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

    // ============ Reading ============

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

    // ============ Search ============

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

    // ============ Pagination ============

    public CompletableFuture<PagedResponse<Post>> getPostsPaginated(String subreddit, int pageSize, String cursor) {
        Map<String, AttributeValue> lastKey = decodeCursor(cursor);

        return repository.findBySubredditPaginated(subreddit, pageSize, lastKey)
                .thenApply(result -> PagedResponse.<Post>builder()
                        .items(result.items())
                        .nextCursor(encodeCursor(result.lastEvaluatedKey()))
                        .hasMore(result.hasMorePages())
                        .build());
    }

    // ============ Creation ============

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

    // ============ Update ============

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

    // ============ Deletion ============

    public CompletableFuture<Void> deletePost(String subreddit, String id) {
        return repository.delete(subreddit, id);
    }

    // ============ Count ============

    public CompletableFuture<Long> countPosts(String subreddit) {
        return repository.countBySubreddit(subreddit);
    }

    // ============ Stream ============

    public SdkPublisher<Post> streamPosts(String subreddit) {
        return repository.streamBySubreddit(subreddit);
    }

    // ============ Utility Methods ============

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
}
