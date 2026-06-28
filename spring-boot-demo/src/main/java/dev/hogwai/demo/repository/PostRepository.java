package dev.hogwai.demo.repository;

import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PostRepository {

    private static final String AUTHOR = "author";
    private static final String CREATED_UTC = "createdUtc";
    private static final String TITLE = "title";
    private static final String KEYWORDS = "keywords";

    private final Table<Post> table;
    private final DynamoSimplifiedClient client;

    public PostRepository(Table<Post> table, DynamoSimplifiedClient client) {
        this.table = table;
        this.client = client;
    }

    // ============ Base CRUD ============

    public void save(Post post) {
        table.putItem(post);
    }

    public Optional<Post> findById(String subreddit, String id) {
        return table.getItem(subreddit, id);
    }

    public Post update(Post post) {
        return table.updateItem(post);
    }

    public void delete(String subreddit, String id) {
        table.deleteItem(subreddit, id);
    }

    public PagedResult<Post> findBySubredditPaginated(String subreddit,
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

    // ============ Dynamic Search ============

    public List<Post> search(PostSearchCriteria criteria) {
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

    // ============ Batch Write ============

    public void batchWrite(List<Post> posts) {
        var batch = client.batchWrite();
        for (Post post : posts) {
            batch.put(table, post);
        }
        batch.execute();
    }

    // ============ Batch Get ============

    public List<Post> batchGet(List<Post> posts) {
        var batch = client.batchGet();
        for (Post post : posts) {
            batch.addKey(table, post.getSubreddit(), post.getId());
        }
        var result = batch.execute();
        return result.getItems(table);
    }

    // ============ Transact Write ============

    public void transactWrite(Post post1, Post post2) {
        client.transactWrite()
                .put(table, post1)
                .put(table, post2)
                .execute();
    }

    // ============ Partial Update ============

    public Optional<Post> updatePartial(String subreddit, String id, Map<String, Object> updates) {
        Post keyItem = Post.builder().subreddit(subreddit).id(id).build();
        return table.update(keyItem)
                .update(expr -> {
                    for (Map.Entry<String, Object> entry : updates.entrySet()) {
                        expr.set(entry.getKey(), entry.getValue());
                    }
                })
                .returnValues(ReturnValue.ALL_NEW)
                .execute();
    }

    // ============ Delete with Return Values ============

    public Optional<Post> deleteAndReturn(String subreddit, String id) {
        return table.delete(subreddit, id)
                .returnValues(ReturnValue.ALL_OLD)
                .execute();
    }
}
