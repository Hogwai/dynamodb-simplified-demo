package dev.hogwai.micronaut.repository;

import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.async.AsyncDynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.async.AsyncTable;
import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementRequest;
import software.amazon.awssdk.services.dynamodb.model.ExecuteStatementResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Singleton
public class PostAsyncRepository implements AutoCloseable {

    private static final String TABLE_NAME = "posts";
    public static final String AUTHOR = "author";
    public static final String CREATED_UTC = "createdUtc";
    public static final String TITLE = "title";
    public static final String KEYWORDS = "keywords";

    private final AsyncDynamoSimplifiedClient client;
    private final AsyncTable<Post> table;

    public PostAsyncRepository(DynamoDbAsyncClient dynamoDbAsyncClient) {
        this.client = AsyncDynamoSimplifiedClient.create(dynamoDbAsyncClient);
        this.table = client.table(TABLE_NAME, Post.class);
    }

    @Override
    public void close() {
        client.close();
    }

    // region Basic CRUD

    public CompletableFuture<Void> save(Post post) {
        return table.putItem(post);
    }

    public CompletableFuture<Void> saveIfNotExists(Post post) {
        return table.put(post)
                .onlyIfNotExists("id")
                .execute();
    }

    public CompletableFuture<Optional<Post>> findById(String subreddit, String id) {
        return table.getItem(subreddit, id);
    }

    public CompletableFuture<Post> update(Post post) {
        return table.updateItem(post);
    }

    public CompletableFuture<Void> delete(String subreddit, String id) {
        return table.deleteItem(subreddit, id).thenApply(ignored -> null);
    }

    // endregion

    // region Queries by Subreddit

    public CompletableFuture<List<Post>> findBySubreddit(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .executeAll();
    }

    public CompletableFuture<List<Post>> findBySubreddit(String subreddit, int limit) {
        return table.query()
                .partitionKey(subreddit)
                .descending()
                .limit(limit)
                .executeWithPagination()
                .thenApply(PagedResult::items);
    }

    public CompletableFuture<PagedResult<Post>> findBySubredditPaginated(String subreddit,
                                                                         int pageSize,
                                                                         Map<String, AttributeValue> lastKey) {
        var query = table.query()
                .partitionKey(subreddit)
                .descending()
                .limit(pageSize);

        if (lastKey != null && !lastKey.isEmpty()) {
            query.startFrom(lastKey);
        }

        return query.executeWithPagination();
    }

    // endregion

    // region Queries by ID

    public CompletableFuture<List<Post>> findByIdPrefix(String subreddit, String idPrefix) {
        return table.query()
                .partitionKeyAndSortKeyBeginsWith(subreddit, idPrefix)
                .executeAll();
    }

    public CompletableFuture<List<Post>> findByIdBetween(String subreddit, String startId, String endId) {
        return table.query()
                .partitionKeyAndSortKeyBetween(subreddit, startId, endId)
                .executeAll();
    }

    // endregion

    // region Queries by Author

    public CompletableFuture<List<Post>> findByAuthor(String subreddit, String author) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.eq(AUTHOR, author))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findByAuthors(String subreddit, List<String> authors) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.in(AUTHOR, authors.toArray()))
                .executeAll();
    }

    // endregion

    // region Temporal Queries

    public CompletableFuture<List<Post>> findCreatedAfter(String subreddit, long timestampUtc) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                .descending()
                .executeAll();
    }

    public CompletableFuture<List<Post>> findCreatedBefore(String subreddit, long timestampUtc) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.lt(CREATED_UTC, timestampUtc))
                .descending()
                .executeAll();
    }

    public CompletableFuture<List<Post>> findCreatedBetween(String subreddit, long startUtc, long endUtc) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.between(CREATED_UTC, startUtc, endUtc))
                .descending()
                .executeAll();
    }

    // endregion

    // region Keyword Queries

    public CompletableFuture<List<Post>> findByKeyword(String subreddit, String keyword) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.contains(KEYWORDS, keyword))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findWithMinKeywords(String subreddit, int minCount) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.sizeGe(KEYWORDS, minCount))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findWithKeywordCount(String subreddit, int min, int max) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.sizeBetween(KEYWORDS, min, max))
                .executeAll();
    }

    // endregion

    // region Text Search

    public CompletableFuture<List<Post>> searchByTitle(String subreddit, String text) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.contains(TITLE, text))
                .executeAll();
    }

    public CompletableFuture<List<Post>> searchByTitlePrefix(String subreddit, String prefix) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.beginsWith(TITLE, prefix))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findWithContent(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .exists("selfText")
                        .and()
                        .sizeGt("selfText", 0))
                .executeAll();
    }

    // endregion

    // region Combined Queries

    public CompletableFuture<List<Post>> findRecentByAuthor(String subreddit, String author, long sinceUtc) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .eq(AUTHOR, author)
                        .and()
                        .gt(CREATED_UTC, sinceUtc))
                .descending()
                .executeAll();
    }

    public CompletableFuture<List<Post>> findRecentWithKeyword(String subreddit, String keyword, long sinceUtc, int limit) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .contains(KEYWORDS, keyword)
                        .and()
                        .gt(CREATED_UTC, sinceUtc))
                .descending()
                .limit(limit)
                .executeAll();
    }

    public CompletableFuture<List<Post>> findByAuthorWithKeywords(String subreddit, String author, int minKeywords) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .eq(AUTHOR, author)
                        .and()
                        .sizeGe(KEYWORDS, minKeywords))
                .executeAll();
    }

    // endregion

    // region OR Queries

    public CompletableFuture<List<Post>> findByEitherAuthor(String subreddit, String author1, String author2) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .eq(AUTHOR, author1)
                        .or()
                        .eq(AUTHOR, author2))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findByAuthorOrKeyword(String subreddit, String author, String keyword) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f
                        .group(FilterExpression.builder()
                                .eq(AUTHOR, author))
                        .or()
                        .group(FilterExpression.builder()
                                .contains(KEYWORDS, keyword)))
                .executeAll();
    }

    // endregion

    // region Projections

    public CompletableFuture<List<Post>> findSummaries(String subreddit, int limit) {
        return table.query()
                .partitionKey(subreddit)
                .project("id", TITLE, AUTHOR, CREATED_UTC)
                .descending()
                .limit(limit)
                .executeAll();
    }

    public CompletableFuture<List<Post>> findTitlesOnly(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .project("id", TITLE)
                .executeAll();
    }

    // endregion

    // region Scans (cross-subreddit)

    public CompletableFuture<List<Post>> findAllByAuthor(String author) {
        return table.scan()
                .filter(f -> f.eq(AUTHOR, author))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findAllWithKeyword(String keyword) {
        return table.scan()
                .filter(f -> f.contains(KEYWORDS, keyword))
                .executeAll();
    }

    public CompletableFuture<List<Post>> findAllCreatedAfter(long timestampUtc) {
        return table.scan()
                .filter(f -> f.gt(CREATED_UTC, timestampUtc))
                .executeAll();
    }

    public CompletableFuture<PagedResult<Post>> scanAllPaginated(int pageSize, Map<String, AttributeValue> lastKey) {
        var scan = table.scan().limit(pageSize);

        if (lastKey != null && !lastKey.isEmpty()) {
            scan.startFrom(lastKey);
        }

        return scan.executeWithPagination();
    }

    // endregion

    // region Conditional Operations

    public CompletableFuture<Void> saveIfNew(Post post) {
        return table.put(post)
                .condition(c -> c.notExists("id"))
                .execute();
    }

    public CompletableFuture<Void> saveOrUpdateOld(Post post, long olderThanUtc) {
        return table.put(post)
                .condition(c -> c
                        .notExists("id")
                        .or()
                        .lt(CREATED_UTC, olderThanUtc))
                .execute();
    }

    public CompletableFuture<Optional<Post>> updateIfAuthorMatches(Post post, String expectedAuthor) {
        return table.update(post)
                .condition(c -> c.eq(AUTHOR, expectedAuthor))
                .execute();
    }

    public CompletableFuture<Optional<Post>> deleteIfOlderThan(String subreddit, String id, long olderThanUtc) {
        return table.delete(subreddit, id)
                .condition(c -> c.lt(CREATED_UTC, olderThanUtc))
                .execute();
    }

    public CompletableFuture<Optional<Post>> deleteByAuthor(String subreddit, String id, String author) {
        return table.delete(subreddit, id)
                .condition(c -> c.eq(AUTHOR, author))
                .execute();
    }

    // endregion

    // region Dynamic Search

    public CompletableFuture<List<Post>> search(PostSearchCriteria criteria) {
        var query = table.query()
                .partitionKey(criteria.getSubreddit())
                .descending();

        if (criteria.getLimit() != null) {
            query.limit(criteria.getLimit());
        }

        if (criteria.hasFilters()) {
            query.filter(f -> buildFilter(f, criteria));
        }

        if (criteria.getProjectedFields() != null && !criteria.getProjectedFields().isEmpty()) {
            query.project(criteria.getProjectedFields().toArray(new String[0]));
        }

        if (criteria.getLastKey() != null) {
            query.startFrom(criteria.getLastKey());
        }

        return query.executeAll();
    }

    // endregion

    // region Count

    public CompletableFuture<Long> countBySubreddit(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .count();
    }

    // endregion

    // region Stream

    public software.amazon.awssdk.core.async.SdkPublisher<Post> streamBySubreddit(String subreddit) {
        return table.query()
                .partitionKey(subreddit)
                .descending()
                .streamResults();
    }

    // endregion

    // region Filter Building (sync helper)

    private void buildFilter(FilterExpression f, PostSearchCriteria criteria) {
        boolean hasPrevious = false;

        if (criteria.getAuthor() != null) {
            f.eq(AUTHOR, criteria.getAuthor());
            hasPrevious = true;
        }

        hasPrevious = addGtFilter(hasPrevious, f, criteria.getSinceUtc());
        hasPrevious = addLtFilter(hasPrevious, f, criteria.getUntilUtc());
        hasPrevious = addContainsFilter(hasPrevious, f, criteria.getKeyword(), KEYWORDS);
        hasPrevious = addSizeGeFilter(hasPrevious, f, criteria.getMinKeywords());
        addContainsFilter(hasPrevious, f, criteria.getTitleContains(), TITLE);
    }

    private static boolean addGtFilter(boolean hasPrevious, FilterExpression f, Long value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.gt(CREATED_UTC, value);
        return true;
    }

    private static boolean addLtFilter(boolean hasPrevious, FilterExpression f, Long value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.lt(CREATED_UTC, value);
        return true;
    }

    private static boolean addContainsFilter(boolean hasPrevious, FilterExpression f, String value, String attr) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.contains(attr, value);
        return true;
    }

    private static boolean addSizeGeFilter(boolean hasPrevious, FilterExpression f, Integer value) {
        if (value == null) return hasPrevious;
        if (hasPrevious) f.and();
        f.sizeGe(KEYWORDS, value);
        return true;
    }

    // endregion

    // region GSI Query

    public CompletableFuture<List<Post>> queryByAuthorGsi(String author) {
        return table.index("author-index")
                .query()
                .partitionKey(author)
                .executeAll();
    }

    // endregion

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

    public CompletableFuture<List<Map<String, AttributeValue>>> executePartiQL(String statement) {
        return client.executeStatement(
                        ExecuteStatementRequest.builder().statement(statement).build())
                .thenApply(ExecuteStatementResponse::items);
    }

    // endregion

    // region List Tables

    public CompletableFuture<List<String>> listTables() {
        return client.listTables();
    }

    // endregion

    // region Entity Table

    public CompletableFuture<Void> entityPut(Post post) {
        return client.entityTable(Post.class).put(post);
    }

    public CompletableFuture<Post> entityGet(String pk, String sk) {
        return client.entityTable(Post.class).get(pk, sk);
    }

    // endregion
}
