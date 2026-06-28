package dev.hogwai.demo.config;

import dev.hogwai.demo.model.Post;
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.Table;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(
            @Value("${aws.region:eu-west-3}") String region,
            @Value("${aws.dynamodb.endpoint-override:}") String endpoint,
            @Value("${aws.credentials.static.access-key-id:fake}") String accessKey,
            @Value("${aws.credentials.static.secret-access-key:fake}") String secretKey
    ) {
        return DynamoDbClient.builder()
                .region(Region.of(region))
                .endpointOverride(endpoint != null && !endpoint.isBlank() ? URI.create(endpoint) : null)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Bean
    public DynamoSimplifiedClient dynamoSimplifiedClient(DynamoDbClient dynamoDbClient) {
        return DynamoSimplifiedClient.create(dynamoDbClient);
    }

    @Bean
    public Table<Post> postTable(DynamoSimplifiedClient client) {
        return client.table("posts", Post.class);
    }
}
