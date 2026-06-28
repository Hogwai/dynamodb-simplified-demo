package dev.hogwai.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.util.Set;

@Setter
@Getter
@Builder
@DynamoDbBean
@AllArgsConstructor
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
    public String getSubreddit() { return subreddit; }

    @DynamoDbSortKey
    public String getId() { return id; }
}
