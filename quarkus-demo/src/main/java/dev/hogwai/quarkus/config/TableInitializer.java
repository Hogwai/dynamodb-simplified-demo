package dev.hogwai.quarkus.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@ApplicationScoped
public class TableInitializer {

    private static final Logger log = LoggerFactory.getLogger(TableInitializer.class);

    void init(@Observes StartupEvent event, DynamoDbClient dynamoDbClient) {
        try {
            var existing = dynamoDbClient.listTables().tableNames();
            if (!existing.contains("posts")) {
                dynamoDbClient.createTable(CreateTableRequest.builder()
                        .tableName("posts")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("subreddit").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                        .attributeDefinitions(
                                AttributeDefinition.builder().attributeName("subreddit").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("author").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("createdUtc").attributeType(ScalarAttributeType.N).build())
                        .globalSecondaryIndexes(
                                GlobalSecondaryIndex.builder()
                                        .indexName("author-index")
                                        .keySchema(
                                                KeySchemaElement.builder().attributeName("author").keyType(KeyType.HASH).build(),
                                                KeySchemaElement.builder().attributeName("createdUtc").keyType(KeyType.RANGE).build())
                                        .projection(p -> p.projectionType(ProjectionType.ALL))
                                        .build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());
            }
        } catch (Exception e) {
            // Table might already exist (race condition on startup)
            log.info("Table initialization skipped: {}", e.getMessage());
        }
    }
}
