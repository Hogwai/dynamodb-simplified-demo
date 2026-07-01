package dev.hogwai.micronaut.dto;

import io.micronaut.serde.annotation.Serdeable;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class PartiQLRequest {
    private String statement;
}
