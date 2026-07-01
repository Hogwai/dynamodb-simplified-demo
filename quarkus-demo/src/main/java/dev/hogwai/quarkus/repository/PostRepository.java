package dev.hogwai.quarkus.repository;

import dev.hogwai.demo.model.Post;
import dev.hogwai.demo.search.PostSearchCriteria;
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.Table;
import dev.hogwai.dynamodb.simplified.expression.FilterExpression;
import dev.hogwai.dynamodb.simplified.result.PagedResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class PostRepository {

    private static final String AUTHOR = "author";
    private static final String CREATED_UTC = "createdUtc";
    private static final String TITLE = "title";
    private static final String KEYWORDS = "keywords";

    private final Table<Post> table;
    private final DynamoSimplifiedClient client;

    @Inject
    public PostRepository(@Named("postTable") Table<Post> table, DynamoSimplifiedClient client) {
        this.table = table;
        this.client = client;
    }

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

    public List<Post> findPostsByAuthor(String subreddit, String author) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.eq(AUTHOR, author))
                .executeAll();
    }

    public List<Post> findRecentPosts(String subreddit, Integer limit) {
        return table.query()
                .partitionKey(subreddit)
                .descending()
                .limit(limit != null ? limit : 50)
                .executeAll();
    }

    public List<Post> findPostsLastHours(String subreddit, long sinceUtc) {
        return table.query()
                .partitionKey(subreddit)
                .filter(f -> f.gt(CREATED_UTC, sinceUtc))
                .descending()
                .executeAll();
    }

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

    // region GSI Query

    public List<Post> queryByAuthorGsi(String author) {
        return table.index("author-index")
                .query()
                .partitionKey(author)
                .executeAll();
    }

    // endregion

    // region Entity Table

    public void entityPut(Post post) {
        client.entityTable(Post.class).put(post);
    }

    public Post entityGet(String pk, String sk) {
        return client.entityTable(Post.class).get(pk, sk);
    }

    // endregion
}
