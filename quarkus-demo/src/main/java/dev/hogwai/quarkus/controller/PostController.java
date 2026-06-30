package dev.hogwai.quarkus.controller;

import dev.hogwai.demo.dto.CreatePostRequest;
import dev.hogwai.demo.dto.PagedResponse;
import dev.hogwai.demo.model.Post;
import dev.hogwai.quarkus.dto.PostSearchRequest;
import dev.hogwai.quarkus.service.PostReactiveService;
import dev.hogwai.quarkus.service.PostService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/posts")
@Produces(MediaType.APPLICATION_JSON)
public class PostController {

    private final PostService postService;
    private final PostReactiveService postReactiveService;

    @Inject
    public PostController(PostService postService, PostReactiveService postReactiveService) {
        this.postService = postService;
        this.postReactiveService = postReactiveService;
    }

    // ===== Sync endpoints =====

    @POST
    public Response createPost(CreatePostRequest request) {
        Post post = postService.createPost(request);
        return Response.status(Response.Status.CREATED).entity(post).build();
    }

    @GET
    @Path("/{subreddit}/{id}")
    public Response getPost(@PathParam("subreddit") String subreddit,
                            @PathParam("id") String id) {
        return postService.getPost(subreddit, id)
                .map(post -> Response.ok(post).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @PUT
    @Path("/{subreddit}/{id}")
    public Post updatePost(@PathParam("subreddit") String subreddit,
                           @PathParam("id") String id,
                           Post post) {
        return postService.updatePost(subreddit, id, post);
    }

    @DELETE
    @Path("/{subreddit}/{id}")
    public Response deletePost(@PathParam("subreddit") String subreddit,
                               @PathParam("id") String id) {
        postService.deletePost(subreddit, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{subreddit}")
    public List<Post> listPosts(@PathParam("subreddit") String subreddit,
                                @QueryParam("limit") @jakarta.ws.rs.DefaultValue("25") int limit,
                                @QueryParam("cursor") String cursor) {
        return postService.getRecentPosts(subreddit, limit);
    }

    @GET
    @Path("/{subreddit}/search")
    public List<Post> search(@PathParam("subreddit") String subreddit,
                              @QueryParam("author") String author,
                              @QueryParam("keyword") String keyword,
                              @QueryParam("since") Long since,
                              @QueryParam("until") Long until,
                              @QueryParam("titleContains") String titleContains,
                              @QueryParam("minKeywords") Integer minKeywords,
                              @QueryParam("limit") Integer limit) {
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

    @GET
    @Path("/{subreddit}/paginated")
    public PagedResponse<Post> listPostsPaginated(@PathParam("subreddit") String subreddit,
                                                    @QueryParam("pageSize") @jakarta.ws.rs.DefaultValue("20") int pageSize,
                                                    @QueryParam("cursor") String cursor) {
        return postService.getPostsPaginated(subreddit, pageSize, cursor);
    }

    @GET
    @Path("/{subreddit}/author/{author}")
    public List<Post> getByAuthor(@PathParam("subreddit") String subreddit,
                                   @PathParam("author") String author) {
        return postService.getPostsByAuthor(subreddit, author);
    }

    @GET
    @Path("/{subreddit}/recent")
    public List<Post> getRecent(@PathParam("subreddit") String subreddit,
                                 @QueryParam("hours") @jakarta.ws.rs.DefaultValue("24") int hours) {
        return postService.getPostsLastHours(subreddit, hours);
    }

    // ===== Reactive endpoints (Quarkus-exclusive) =====

    @GET
    @Path("/{subreddit}/count")
    public Uni<Long> countPosts(@PathParam("subreddit") String subreddit) {
        return postReactiveService.countPosts(subreddit);
    }

    @GET
    @Path("/{subreddit}/stream")
    @Produces(MediaType.APPLICATION_JSON)
    public Multi<Post> streamPosts(@PathParam("subreddit") String subreddit) {
        return postReactiveService.streamPosts(subreddit);
    }
}
