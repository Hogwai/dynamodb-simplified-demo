package dev.hogwai.micronaut.controller;

import dev.hogwai.micronaut.dto.PostSearchRequest;
import dev.hogwai.micronaut.service.PostService;
import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.model.Post;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.annotation.Status;
import software.amazon.awssdk.core.async.SdkPublisher;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Controller("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Get("/{subreddit}")
    public CompletableFuture<List<Post>> listPosts(@PathVariable String subreddit,
                                                    @QueryValue Integer limit) {
        return postService.getRecentPosts(subreddit, limit != null ? limit : 50);
    }

    @Get("/{subreddit}/{id}")
    public CompletableFuture<Optional<Post>> getPost(@PathVariable String subreddit,
                                                      @PathVariable String id) {
        return postService.getPost(subreddit, id);
    }

    @Get("/{subreddit}/author/{author}")
    public CompletableFuture<List<Post>> getByAuthor(@PathVariable String subreddit,
                                                      @PathVariable String author) {
        return postService.getPostsByAuthor(subreddit, author);
    }

    @Get("/{subreddit}/recent")
    public CompletableFuture<List<Post>> getRecentPosts(@PathVariable String subreddit,
                                                         @QueryValue Integer hours) {
        return postService.getPostsLastHours(subreddit, hours);
    }

    @Get("/{subreddit}/search")
    public CompletableFuture<List<Post>> search(@PathVariable String subreddit,
                                                 @QueryValue String author,
                                                 @QueryValue String keyword,
                                                 @QueryValue Long since,
                                                 @QueryValue Integer limit) {
        PostSearchRequest request = PostSearchRequest.builder()
                .subreddit(subreddit)
                .author(author)
                .keyword(keyword)
                .sinceUtc(since)
                .limit(limit)
                .build();

        return postService.search(request);
    }

    @Get("/{subreddit}/paginated")
    public CompletableFuture<PagedResponse<Post>> listPostsPaginated(@PathVariable String subreddit,
                                                                      @QueryValue Integer pageSize,
                                                                      @QueryValue String cursor) {
        return postService.getPostsPaginated(subreddit, pageSize != null ? pageSize : 20, cursor);
    }

    @Get("/{subreddit}/count")
    public CompletableFuture<Long> countPosts(@PathVariable String subreddit) {
        return postService.countPosts(subreddit);
    }

    @Get("/{subreddit}/stream")
    public SdkPublisher<Post> streamPosts(@PathVariable String subreddit) {
        return postService.streamPosts(subreddit);
    }

    @io.micronaut.http.annotation.Post
    @Status(HttpStatus.CREATED)
    public CompletableFuture<Post> createPost(@Body CreatePostRequest request) {
        return postService.createPost(request);
    }

    @Put("/{subreddit}/{id}")
    public CompletableFuture<Post> updatePost(@PathVariable String subreddit,
                                               @PathVariable String id,
                                               @Body Post post) {
        return postService.updatePost(subreddit, id, post);
    }

    @Delete("/{subreddit}/{id}")
    @Status(HttpStatus.NO_CONTENT)
    public CompletableFuture<Void> deletePost(@PathVariable String subreddit,
                                               @PathVariable String id) {
        return postService.deletePost(subreddit, id);
    }
}
