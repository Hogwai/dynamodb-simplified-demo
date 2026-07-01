package dev.hogwai.quarkus.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class PostSearchRequest {
    private String subreddit;
    private String author;
    private String keyword;
    private Long sinceUtc;
    private Long untilUtc;
    private String titleContains;
    private Integer minKeywords;
    private Integer limit;

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

        public Builder subreddit(String v) {
            this.subreddit = v;
            return this;
        }

        public Builder author(String v) {
            this.author = v;
            return this;
        }

        public Builder keyword(String v) {
            this.keyword = v;
            return this;
        }

        public Builder sinceUtc(Long v) {
            this.sinceUtc = v;
            return this;
        }

        public Builder untilUtc(Long v) {
            this.untilUtc = v;
            return this;
        }

        public Builder titleContains(String v) {
            this.titleContains = v;
            return this;
        }

        public Builder minKeywords(Integer v) {
            this.minKeywords = v;
            return this;
        }

        public Builder limit(Integer v) {
            this.limit = v;
            return this;
        }

        public PostSearchRequest build() {
            PostSearchRequest r = new PostSearchRequest();
            r.subreddit = this.subreddit;
            r.author = this.author;
            r.keyword = this.keyword;
            r.sinceUtc = this.sinceUtc;
            r.untilUtc = this.untilUtc;
            r.titleContains = this.titleContains;
            r.minKeywords = this.minKeywords;
            r.limit = this.limit;
            return r;
        }
    }
}
