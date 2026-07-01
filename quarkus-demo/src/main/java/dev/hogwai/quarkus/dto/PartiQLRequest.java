package dev.hogwai.quarkus.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PartiQLRequest {
    private String statement;
}
