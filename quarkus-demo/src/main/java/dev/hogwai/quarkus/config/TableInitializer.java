package dev.hogwai.quarkus.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@ApplicationScoped
public class TableInitializer {

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
                                AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());
            }
        } catch (Exception e) {
            // Table might already exist (race condition on startup)
            System.out.println("Table initialization skipped: " + e.getMessage());
        }
    }
}
