package dev.hogwai.demo.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
@Builder
public class CreatePostRequest {
    private String subreddit;
    private String author;
    private String title;
    private String selfText;
    private Set<String> keywords;
}
