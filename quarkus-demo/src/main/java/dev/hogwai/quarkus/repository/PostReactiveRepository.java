package dev.hogwai.quarkus.repository;

import dev.hogwai.demo.model.Post;
import dev.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.async.AsyncTable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@ApplicationScoped
public class PostReactiveRepository implements AutoCloseable {

    private static final String TABLE_NAME = "posts";
    private final AsyncDynamoSimplifiedClient client;
    private final AsyncTable<Post> table;

    @Inject
    public PostReactiveRepository(DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.client = AsyncDynamoSimplifiedClient.create(dynamoDbAsyncClient);
        this.table = this.client.table(TABLE_NAME, Post.class);
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

    // region Transact Get

    public CompletableFuture<List<Post>> transactGet(List<List<String>> keys) {
        var builder = client.transactGet();
        for (List<String> key : keys) {
            builder.addGetItem(table, key.get(0), key.get(1));
        }
        return builder.execute().thenApply(results -> {
            List<Post> items = new ArrayList<>();
            for (int i = 0; i < keys.size(); i++) {
                Post item = results.get(i);
                if (item != null) items.add(item);
            }
            return items;
        });
    }

    // endregion

    // region PartiQL

    public CompletableFuture<ExecuteStatementResponse> executePartiQL(String statement) {
        return client.executeStatement(
                ExecuteStatementRequest.builder().statement(statement).build());
    }

    // endregion
}
