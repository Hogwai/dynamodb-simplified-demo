package dev.hogwai.app.startup;

import dev.hogwai.demo.model.Post;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.event.ApplicationStartupEvent;
import jakarta.inject.Singleton;
import net.datafaker.Faker;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

@Singleton
@Requires(property = "app.data-generator.enabled", value = "true")
public class DataGeneratorListener implements ApplicationEventListener<ApplicationStartupEvent> {

    private final DynamoDbEnhancedAsyncClient enhancedAsyncClient;
    private final Faker faker = new Faker();

    @Value("${app.data-generator.count:1000}")
    private int count;

    @Value("${app.data-generator.table:posts}")
    private String tableName;

    private static final List<String> SUBREDDITS = List.of(
            "java", "programming", "aws", "devops", "kotlin",
            "spring", "micronaut", "docker", "kubernetes", "linux"
    );

    private static final List<String> KEYWORDS_POOL = List.of(
            "tutorial", "help", "question", "discussion", "news",
            "library", "framework", "performance", "security", "database"
    );

    public DataGeneratorListener(DynamoDbEnhancedAsyncClient enhancedAsyncClient) {
        this.enhancedAsyncClient = enhancedAsyncClient;
    }

    @Override
    public void onApplicationEvent(ApplicationStartupEvent event) {
        DynamoDbAsyncTable<Post> table = enhancedAsyncClient.table(tableName, TableSchema.fromBean(Post.class));

        List<Post> posts = IntStream.range(0, count)
                .mapToObj(i -> generatePost())
                .toList();

        List<List<Post>> batches = partition(posts, 25);

        for (List<Post> batch : batches) {
            writeBatch(table, batch);
        }
    }

    private Post generatePost() {
        String subreddit = SUBREDDITS.get(faker.random().nextInt(SUBREDDITS.size()));

        long createdUtc = Instant.now()
                .minus(faker.random().nextInt(730), ChronoUnit.DAYS)
                .minus(faker.random().nextInt(86400), ChronoUnit.SECONDS)
                .getEpochSecond();

        Set<String> keywords = new HashSet<>();
        while (keywords.size() < faker.random().nextInt(2, 5)) {
            keywords.add(KEYWORDS_POOL.get(faker.random().nextInt(KEYWORDS_POOL.size())));
        }

        return Post.builder()
                .id(UUID.randomUUID().toString())
                .subreddit(subreddit)
                .createdUtc(createdUtc)
                .author(faker.internet().uuid())
                .title(faker.lorem().sentence(faker.random().nextInt(5, 15)))
                .selfText(String.join("\n\n", faker.lorem().paragraphs(faker.random().nextInt(1, 5))))
                .permalink("/r/" + subreddit + "/comments/" + faker.random().nextInt(1, 5))
                .keywords(keywords)
                .build();
    }

    private void writeBatch(DynamoDbAsyncTable<Post> table, List<Post> posts) {
        WriteBatch.Builder<Post> builder = WriteBatch.builder(Post.class)
                .mappedTableResource(table);
        posts.forEach(builder::addPutItem);

        enhancedAsyncClient.batchWriteItem(BatchWriteItemEnhancedRequest.builder()
                        .addWriteBatch(builder.build())
                        .build())
                .join();
    }

    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }
}
