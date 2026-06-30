package dev.hogwai.springboot.service;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.springboot.dto.PostSearchRequest;
import dev.hogwai.demo.exception.PostNotFoundException;
import dev.hogwai.demo.model.Post;
import dev.hogwai.springboot.repository.PostRepository;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PostService {

    private final PostRepository repository;

    public PostService(PostRepository repository) {
        this.repository = repository;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }

    // ============ Base CRUD ============

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

    // ============ Batch Write ============

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

    // ============ Batch Get ============

    public List<Post> batchGet(List<String[]> keys) {
        List<Post> postKeys = keys.stream()
                .map(k -> Post.builder().subreddit(k[0]).id(k[1]).build())
                .toList();
        return repository.batchGet(postKeys);
    }

    // ============ Transact Write ============

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

    // ============ Partial Update ============

    public Optional<Post> updatePartial(String subreddit, String id, Map<String, Object> updates) {
        return repository.updatePartial(subreddit, id, updates);
    }

    // ============ Delete with Return Values ============

    public Optional<Post> deleteAndReturn(String subreddit, String id) {
        return repository.deleteAndReturn(subreddit, id);
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
