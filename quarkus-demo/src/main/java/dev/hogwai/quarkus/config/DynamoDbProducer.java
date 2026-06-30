package dev.hogwai.quarkus.config;

import dev.hogwai.demo.model.Post;
import dev.hogwai.dynamodb.simplified.DynamoSimplifiedClient;
import dev.hogwai.dynamodb.simplified.Table;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@ApplicationScoped
public class DynamoDbProducer {

    @Produces
    @ApplicationScoped
    public DynamoDbClient dynamoDbClient(
            @ConfigProperty(name = "aws.region", defaultValue = "eu-west-3") String region,
            @ConfigProperty(name = "aws.dynamodb.endpoint-override") String endpoint,
            @ConfigProperty(name = "aws.credentials.static.access-key-id", defaultValue = "fake") String accessKey,
            @ConfigProperty(name = "aws.credentials.static.secret-access-key", defaultValue = "fake") String secretKey
    ) {
        var builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Produces
    @ApplicationScoped
    public DynamoSimplifiedClient dynamoSimplifiedClient(DynamoDbClient dynamoDbClient) {
        return DynamoSimplifiedClient.create(dynamoDbClient);
    }

    @Produces
    @ApplicationScoped
    public DynamoDbAsyncClient dynamoDbAsyncClient(
            @ConfigProperty(name = "aws.region", defaultValue = "eu-west-3") String region,
            @ConfigProperty(name = "aws.dynamodb.endpoint-override") String endpoint,
            @ConfigProperty(name = "aws.credentials.static.access-key-id", defaultValue = "fake") String accessKey,
            @ConfigProperty(name = "aws.credentials.static.secret-access-key", defaultValue = "fake") String secretKey
    ) {
        var builder = DynamoDbAsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Produces
    @Named("postTable")
    @ApplicationScoped
    public Table<Post> postTable(DynamoSimplifiedClient client) {
        return client.table("posts", Post.class);
    }
}
