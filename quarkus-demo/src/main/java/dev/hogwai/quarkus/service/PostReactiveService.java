package dev.hogwai.quarkus.service;

import dev.hogwai.demo.model.Post;
import dev.hogwai.quarkus.repository.PostReactiveRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PostReactiveService {

    private final PostReactiveRepository repository;

    @Inject
    public PostReactiveService(PostReactiveRepository repository) {
        this.repository = repository;
    }

    public Uni<Long> countPosts(String subreddit) {
        return Uni.createFrom().completionStage(repository.countBySubreddit(subreddit));
    }

    public Multi<Post> streamPosts(String subreddit) {
        return Multi.createFrom().emitter(em -> {
            CompletableFuture<Void> future = repository.streamBySubreddit(subreddit)
                    .subscribe(em::emit);
            future.whenComplete((result, error) -> {
                if (error != null) em.fail(error);
                else em.complete();
            });
        });
    }

    // region Transact Get

    public Uni<List<Post>> transactGet(List<List<String>> keys) {
        return Uni.createFrom().completionStage(repository.transactGet(keys));
    }

    // endregion

    // region PartiQL

    public Uni<ExecuteStatementResponse> executePartiQL(String statement) {
        return Uni.createFrom().completionStage(repository.executePartiQL(statement));
    }

    // endregion
}
