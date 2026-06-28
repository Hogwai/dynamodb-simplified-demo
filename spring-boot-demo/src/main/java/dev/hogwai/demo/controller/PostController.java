package dev.hogwai.demo.controller;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.dto.PostSearchRequest;
import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // ============ Base: Paginated list ============

    @GetMapping("/{subreddit}")
    public PagedResponse<Post> listPostsPaginated(@PathVariable String subreddit,
                                                   @RequestParam(defaultValue = "25") int limit,
                                                   @RequestParam(required = false) String cursor) {
        return postService.getPostsPaginated(subreddit, limit, cursor);
    }

    // ============ Base: Get single post ============

    @GetMapping("/{subreddit}/{id}")
    public ResponseEntity<Post> getPost(@PathVariable String subreddit,
                                        @PathVariable String id) {
        return postService.getPost(subreddit, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ============ Base: Search ============

    @GetMapping("/{subreddit}/search")
    public List<Post> search(@PathVariable String subreddit,
                             @RequestParam(required = false) String author,
                             @RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Long since,
                             @RequestParam(required = false) Long until,
                             @RequestParam(required = false) String titleContains,
                             @RequestParam(required = false) Integer minKeywords,
                             @RequestParam(required = false) Integer limit) {
        PostSearchRequest request = PostSearchRequest.builder()
                .subreddit(subreddit)
                .author(author)
                .keyword(keyword)
                .sinceUtc(since)
                .untilUtc(until)
                .titleContains(titleContains)
                .minKeywords(minKeywords)
                .limit(limit)
                .build();

        return postService.search(request);
    }

    // ============ Base: Create post ============

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Post createPost(@RequestBody CreatePostRequest request) {
        return postService.createPost(request);
    }

    // ============ Base: Update post ============

    @PutMapping("/{subreddit}/{id}")
    public Post updatePost(@PathVariable String subreddit,
                           @PathVariable String id,
                           @RequestBody Post post) {
        return postService.updatePost(subreddit, id, post);
    }

    // ============ Base: Delete post ============

    @DeleteMapping("/{subreddit}/{id}")
    public ResponseEntity<?> deletePost(@PathVariable String subreddit,
                                        @PathVariable String id,
                                        @RequestParam(defaultValue = "false") boolean returnDeleted) {
        if (returnDeleted) {
            return postService.deleteAndReturn(subreddit, id)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        postService.deletePost(subreddit, id);
        return ResponseEntity.noContent().build();
    }

    // ============ Batch Write ============

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Post> batchWrite(@RequestBody List<CreatePostRequest> requests) {
        return postService.batchWrite(requests);
    }

    // ============ Batch Get ============

    @PostMapping("/batch-get")
    public List<Post> batchGet(@RequestBody List<String[]> keys) {
        return postService.batchGet(keys);
    }

    // ============ Transact Write ============

    @PostMapping("/transact")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Post> transactWrite(@RequestBody List<CreatePostRequest> requests) {
        if (requests.size() != 2) {
            throw new IllegalArgumentException("Transact write requires exactly 2 items");
        }
        return postService.transactWrite(requests.get(0), requests.get(1));
    }

    // ============ Partial Update ============

    @PatchMapping("/{subreddit}/{id}")
    public ResponseEntity<Post> updatePartial(@PathVariable String subreddit,
                                              @PathVariable String id,
                                              @RequestBody Map<String, Object> updates) {
        return postService.updatePartial(subreddit, id, updates)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
