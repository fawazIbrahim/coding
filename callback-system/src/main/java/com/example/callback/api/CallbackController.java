package com.example.callback.api;

import com.example.callback.api.dto.CallbackRequest;
import com.example.callback.api.dto.CallbackResponse;
import com.example.callback.service.CallbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Public API for submitting and querying callback executions.
 */
@RestController
@RequestMapping("/api/v1/callbacks")
public class CallbackController {

    private final CallbackService callbackService;

    public CallbackController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    /**
     * Submit a new callback request.
     * Returns 202 Accepted immediately — delivery is asynchronous.
     */
    @PostMapping
    public ResponseEntity<CallbackResponse> submit(@Valid @RequestBody CallbackRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(callbackService.createCallback(request));
    }

    /**
     * Poll the current status of an execution, including attempt count and
     * next retry time if applicable.
     */
    @GetMapping("/{executionId}")
    public ResponseEntity<CallbackResponse> getStatus(@PathVariable UUID executionId) {
        return ResponseEntity.ok(callbackService.getExecution(executionId));
    }
}
