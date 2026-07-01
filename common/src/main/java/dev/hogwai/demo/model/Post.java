package dev.hogwai.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import dev.hogwai.dynamodb.simplified.entity.Entity;
import dev.hogwai.dynamodb.simplified.entity.KeyComponent;
import java.util.Set;

@Setter
@Getter
@Builder
@DynamoDbBean
@AllArgsConstructor
@Entity(discriminator = "post", table = "posts")
public class Post {

    private String id;
    private String subreddit;
    private Long createdUtc;
    private String author;
    private String title;
    private String selfText;
    private String permalink;
    private Set<String> keywords;

    public Post() {
        // Needed with @DynamoDbBean
    }

    @DynamoDbPartitionKey
    @KeyComponent(component = "PK")
    public String getSubreddit() { return subreddit; }

    @DynamoDbSortKey
    @KeyComponent(component = "SK")
    public String getId() { return id; }

    @DynamoDbSecondaryPartitionKey(indexNames = "author-index")
    public String getAuthor() { return author; }

    @DynamoDbSecondarySortKey(indexNames = "author-index")
    public Long getCreatedUtc() { return createdUtc; }
}
