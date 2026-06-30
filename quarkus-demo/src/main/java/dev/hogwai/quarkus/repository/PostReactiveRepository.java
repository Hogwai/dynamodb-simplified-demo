package dev.hogwai.quarkus.repository;

import dev.hogwai.demo.model.Post;
import dev.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.async.AsyncTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PostReactiveRepository implements AutoCloseable {

    private static final String TABLE_NAME = "posts";
    private final AsyncTable<Post> table;

    @Inject
    public PostReactiveRepository(DynamoDbAsyncClient dynamoDbAsyncClient) {
        var client = AsyncDynamoSimplifiedClient.create(dynamoDbAsyncClient);
        this.table = client.table(TABLE_NAME, Post.class);
    }

    @Override
    public void close() {
        // AsyncDynamoSimplifiedClient doesn't need explicit close
    }

    public CompletableFuture<Long> countBySubreddit(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .count();
    }

    public software.amazon.awssdk.core.async.SdkPublisher<Post> streamBySubreddit(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .descending()
                .streamResults();
    }
}
