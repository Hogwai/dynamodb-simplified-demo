package dev.hogwai.micronaut.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Serdeable
public class PostSearchRequest {
    private String subreddit;
    private String author;
    private String keyword;
    private Long sinceUtc;
    private Long untilUtc;
    private String titleContains;
    private Integer minKeywords;
    private Integer limit;

    public PostSearchRequest() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String subreddit;
        private String author;
        private String keyword;
        private Long sinceUtc;
        private Long untilUtc;
        private String titleContains;
        private Integer minKeywords;
        private Integer limit;

        public Builder subreddit(String subreddit) {
            this.subreddit = subreddit;
            return this;
        }

        public Builder author(String author) {
            this.author = author;
            return this;
        }

        public Builder keyword(String keyword) {
            this.keyword = keyword;
            return this;
        }

        public Builder sinceUtc(Long sinceUtc) {
            this.sinceUtc = sinceUtc;
            return this;
        }

        public Builder untilUtc(Long untilUtc) {
            this.untilUtc = untilUtc;
            return this;
        }

        public Builder titleContains(String titleContains) {
            this.titleContains = titleContains;
            return this;
        }

        public Builder minKeywords(Integer minKeywords) {
            this.minKeywords = minKeywords;
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public PostSearchRequest build() {
            PostSearchRequest request = new PostSearchRequest();
            request.subreddit = this.subreddit;
            request.author = this.author;
            request.keyword = this.keyword;
            request.sinceUtc = this.sinceUtc;
            request.untilUtc = this.untilUtc;
            request.titleContains = this.titleContains;
            request.minKeywords = this.minKeywords;
            request.limit = this.limit;
            return request;
        }
    }
}
