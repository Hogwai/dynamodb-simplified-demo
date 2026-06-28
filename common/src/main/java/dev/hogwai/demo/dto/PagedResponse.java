package dev.hogwai.demo.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PagedResponse<T> {
    private List<T> items;
    private String nextCursor;
    private boolean hasMore;
}
