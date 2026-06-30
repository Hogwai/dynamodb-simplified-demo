package dev.hogwai.micronaut.config;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;

import java.net.URI;

@Factory
public class DynamoDbFactory {

    private static final Logger log = LoggerFactory.getLogger(DynamoDbFactory.class);

    @Singleton
    @Primary
    DynamoDbAsyncClient dynamoDbAsyncClient(
            @Value("${aws.region:eu-west-3}") String region,
            @Value("${aws.dynamodb.endpoint-override:}") String endpoint,
            @Value("${aws.credentials.static.access-key-id:fake}") String accessKey,
            @Value("${aws.credentials.static.secret-access-key:fake}") String secretKey
    ) {
        String resolvedEndpoint = System.getProperty("aws.dynamodb.endpoint-override");
        if (resolvedEndpoint == null || resolvedEndpoint.isBlank()) {
            resolvedEndpoint = endpoint;
        }
        log.info("DynamoDB endpoint: {}", resolvedEndpoint);
        return DynamoDbAsyncClient.builder()
                .region(Region.of(region))
                .endpointOverride(resolvedEndpoint != null && !resolvedEndpoint.isBlank() ? URI.create(resolvedEndpoint) : null)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Singleton
    DynamoDbEnhancedAsyncClient dynamoDbEnhancedAsyncClient(DynamoDbAsyncClient client) {
        return DynamoDbEnhancedAsyncClient.builder()
                .dynamoDbClient(client)
                .build();
    }
}
