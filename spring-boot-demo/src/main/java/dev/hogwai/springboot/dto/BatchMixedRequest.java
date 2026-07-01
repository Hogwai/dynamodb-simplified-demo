package dev.hogwai.springboot.dto;

import dev.hogwai.demo.dto.CreatePostRequest;
import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchMixedRequest {
    private List<CreatePostRequest> puts;
    private List<List<String>> deletes;
}
