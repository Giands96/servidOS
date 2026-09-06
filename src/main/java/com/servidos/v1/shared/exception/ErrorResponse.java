package com.servidos.v1.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
//* los campos nulos no se incluirán en el JSON
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final Instant timestamp;
    private final String status;
    private final String message;
    private final String path;
    private final String traceID;
}
