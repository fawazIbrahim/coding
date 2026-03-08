package com.example.callback.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Incoming payload for the POST /api/v1/callbacks endpoint.
 *
 * @param type   Logical event type (e.g. "ORDER_SHIPPED", "PAYMENT_RECEIVED")
 * @param data   Arbitrary JSON payload forwarded verbatim to the target
 * @param target Name of the registered {@code CallbackTarget} to use
 */
public record CallbackRequest(
        @NotBlank(message = "type must not be blank")
        String type,

        @NotNull(message = "data must not be null")
        Map<String, Object> data,

        @NotBlank(message = "target must not be blank")
        String target) {
}
