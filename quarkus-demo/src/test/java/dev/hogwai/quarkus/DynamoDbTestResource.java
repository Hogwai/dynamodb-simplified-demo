package dev.hogwai.quarkus;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.Map;

public class DynamoDbTestResource implements QuarkusTestResourceLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbTestResource.class);

    public static String dynamoDbEndpoint;

    private static final GenericContainer<?> dynamoDb = new GenericContainer<>("amazon/dynamodb-local:latest")
            .withExposedPorts(8000);

    public static void createTableIfNeeded(String endpoint) {
        try (var client = DynamoDbClient.builder()
                .region(Region.EU_WEST_3)
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("fake", "fake")))
                .build()) {
            var existing = client.listTables().tableNames();
            if (!existing.contains("posts")) {
                client.createTable(CreateTableRequest.builder()
                        .tableName("posts")
                        .keySchema(
                                KeySchemaElement.builder().attributeName("subreddit").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
                        .attributeDefinitions(
                                AttributeDefinition.builder().attributeName("subreddit").attributeType(ScalarAttributeType.S).build(),
                                AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build())
                        .billingMode(BillingMode.PAY_PER_REQUEST)
                        .build());
                log.info("Created 'posts' table via DynamoDbTestResource");
            }
        }
    }

    @Override
    public Map<String, String> start() {
        dynamoDb.start();
        String endpoint = "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000);
        dynamoDbEndpoint = endpoint;
        createTableIfNeeded(endpoint);
        return Map.of("aws.dynamodb.endpoint-override", endpoint);
    }

    @Override
    public void stop() {
        dynamoDb.stop();
    }
}
