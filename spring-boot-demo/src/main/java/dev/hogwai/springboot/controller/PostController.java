package dev.hogwai.springboot.controller;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.model.Post;
import dev.hogwai.dynamodb.simplified.result.CrossTableBatchWriteResult;
import dev.hogwai.springboot.dto.BatchMixedRequest;
import dev.hogwai.springboot.dto.PartiQLRequest;
import dev.hogwai.springboot.dto.PostSearchRequest;
import dev.hogwai.springboot.dto.TransactAdvancedRequest;
import dev.hogwai.springboot.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    // region Base: Paginated list

    @GetMapping("/{subreddit}")
    public PagedResponse<Post> listPostsPaginated(@PathVariable String subreddit,
                                                  @RequestParam(defaultValue = "25") int limit,
                                                  @RequestParam(required = false) String cursor) {
        return postService.getPostsPaginated(subreddit, limit, cursor);
    }

    // endregion

    // region Base: Get single post

    @GetMapping("/{subreddit}/{id}")
    public ResponseEntity<Post> getPost(@PathVariable String subreddit,
                                        @PathVariable String id) {
        return postService.getPost(subreddit, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // endregion

    // region Base: Count

    @GetMapping("/{subreddit}/count")
    public long countPosts(@PathVariable String subreddit) {
        return postService.countPosts(subreddit);
    }

    // endregion

    // region Base: Search

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

    // endregion

    // region Base: Create post

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Post createPost(@RequestBody CreatePostRequest request) {
        return postService.createPost(request);
    }

    // endregion

    // region Base: Update post

    @PutMapping("/{subreddit}/{id}")
    public Post updatePost(@PathVariable String subreddit,
                           @PathVariable String id,
                           @RequestBody Post post) {
        return postService.updatePost(subreddit, id, post);
    }

    // endregion

    // region Base: Delete post

    @DeleteMapping("/{subreddit}/{id}")
    public ResponseEntity<Object> deletePost(@PathVariable String subreddit,
                                             @PathVariable String id,
                                             @RequestParam(defaultValue = "false") boolean returnDeleted) {
        if (returnDeleted) {
            return postService.deleteAndReturn(subreddit, id)
                    .map(ResponseEntity::<Object>ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        postService.deletePost(subreddit, id);
        return ResponseEntity.noContent().build();
    }

    // endregion

    // region Batch Write

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Post> batchWrite(@RequestBody List<CreatePostRequest> requests) {
        return postService.batchWrite(requests);
    }

    // endregion

    // region Batch Get

    @PostMapping("/batch-get")
    public List<Post> batchGet(@RequestBody List<String[]> keys) {
        return postService.batchGet(keys);
    }

    // endregion

    // region Transact Write

    @PostMapping("/transact")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Post> transactWrite(@RequestBody List<CreatePostRequest> requests) {
        if (requests.size() != 2) {
            throw new IllegalArgumentException("Transact write requires exactly 2 items");
        }
        return postService.transactWrite(requests.get(0), requests.get(1));
    }

    // endregion

    // region Partial Update

    @PatchMapping("/{subreddit}/{id}")
    public ResponseEntity<Post> updatePartial(@PathVariable String subreddit,
                                              @PathVariable String id,
                                              @RequestBody Map<String, Object> updates) {
        return postService.updatePartial(subreddit, id, updates)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // endregion

    // region GSI Query by Author

    @GetMapping("/by-author/{author}")
    public List<Post> getByAuthorGsi(@PathVariable String author) {
        return postService.getPostsByAuthorGsi(author);
    }

    // endregion

    // region Batch Write with Deletes

    @PostMapping("/batch-mixed")
    @ResponseStatus(HttpStatus.CREATED)
    public CrossTableBatchWriteResult batchWriteMixed(@RequestBody BatchMixedRequest request) {
        return postService.batchWriteWithDeletes(request.getPuts(), request.getDeletes());
    }

    // endregion

    // region Advanced Transact Write

    @PostMapping("/transact-advanced")
    public void transactWriteAdvanced(@RequestBody TransactAdvancedRequest request) {
        postService.transactWriteAdvanced(request.getPuts(), request.getConditionCheck());
    }

    // endregion

    // region Transact Get

    @PostMapping("/transact-get")
    public List<Post> transactGet(@RequestBody List<List<String>> keys) {
        return postService.transactGet(
                keys.stream().map(k -> new String[]{k.getFirst(), k.get(1)}).toList());
    }

    // endregion

    // region PartiQL

    @PostMapping("/partiql")
    public ExecuteStatementResponse executePartiQL(@RequestBody PartiQLRequest request) {
        return postService.executePartiQL(request.getStatement());
    }

    // endregion

    // region List Tables

    @GetMapping("/admin/tables")
    public List<String> listTables() {
        return postService.listTables();
    }

    // endregion

    // region Entity Table

    @PostMapping("/entity")
    @ResponseStatus(HttpStatus.CREATED)
    public void entityPut(@RequestBody CreatePostRequest request) {
        postService.entityPut(request);
    }

    @GetMapping("/entity/{pk}/{sk}")
    public ResponseEntity<Post> entityGet(@PathVariable String pk, @PathVariable String sk) {
        Post post = postService.entityGet(pk, sk);
        if (post == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(post);
    }

    // endregion
}
