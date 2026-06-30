package dev.hogwai.quarkus.service;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.exception.PostNotFoundException;
import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import dev.hogwai.quarkus.dto.PostSearchRequest;
import dev.hogwai.quarkus.repository.PostRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PostService {

    private final PostRepository repository;

    @Inject
    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

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

    public List<Post> getPostsByAuthor(String subreddit, String author) {
        return repository.findPostsByAuthor(subreddit, author);
    }

    public List<Post> getRecentPosts(String subreddit, Integer limit) {
        return repository.findRecentPosts(subreddit, limit);
    }

    public List<Post> getPostsLastHours(String subreddit, Integer hours) {
        long since = Instant.now().minusSeconds(hours * 3600L).getEpochSecond();
        return repository.findPostsLastHours(subreddit, since);
    }

    // ============ Utility Methods ============

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
}
