package dev.hogwai.springboot.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartiQLRequest {
    private String statement;
}
