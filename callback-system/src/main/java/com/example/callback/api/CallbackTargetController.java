package com.example.callback.api;

import com.example.callback.api.dto.CallbackTargetDto;
import com.example.callback.domain.AuthType;
import com.example.callback.domain.CallbackTarget;
import com.example.callback.repository.CallbackTargetRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin API for managing callback target configurations.
 *
 * <p>In production, secure this endpoint with Spring Security or an API Gateway
 * so only operators can create/modify targets.
 */
@RestController
@RequestMapping("/api/v1/admin/targets")
public class CallbackTargetController {

    private final CallbackTargetRepository targetRepository;

    public CallbackTargetController(CallbackTargetRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    @GetMapping
    public List<CallbackTargetDto> list() {
        return targetRepository.findAll().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CallbackTargetDto> get(@PathVariable UUID id) {
        return targetRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CallbackTargetDto> create(@Valid @RequestBody CallbackTargetDto dto) {
        CallbackTarget saved = targetRepository.save(fromDto(new CallbackTarget(), dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CallbackTargetDto> update(@PathVariable UUID id,
                                                    @Valid @RequestBody CallbackTargetDto dto) {
        return targetRepository.findById(id)
                .map(existing -> ResponseEntity.ok(toDto(targetRepository.save(fromDto(existing, dto)))))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!targetRepository.existsById(id)) return ResponseEntity.notFound().build();
        targetRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private CallbackTargetDto toDto(CallbackTarget t) {
        return new CallbackTargetDto(
                t.getId(), t.getName(), t.getUrl(), t.getHttpMethod(),
                t.getHeaders(), t.getAuthType(),
                null,  // authConfig is write-only — never returned
                t.getMaxRetries(), t.getRetryBaseDelaySeconds(),
                t.getRetryBackoffMultiplier(), t.getTimeoutSeconds(), t.isEnabled());
    }

    private CallbackTarget fromDto(CallbackTarget target, CallbackTargetDto dto) {
        target.setName(dto.name());
        target.setUrl(dto.url());
        target.setHttpMethod(dto.httpMethod() != null ? dto.httpMethod() : "POST");
        target.setHeaders(dto.headers());
        target.setAuthType(dto.authType() != null ? dto.authType() : AuthType.NONE);
        if (dto.authConfig() != null) {
            target.setAuthConfig(dto.authConfig()); // only update if explicitly provided
        }
        target.setMaxRetries(dto.maxRetries());
        target.setRetryBaseDelaySeconds(dto.retryBaseDelaySeconds() > 0 ? dto.retryBaseDelaySeconds() : 60L);
        target.setRetryBackoffMultiplier(dto.retryBackoffMultiplier() > 0 ? dto.retryBackoffMultiplier() : 2.0);
        target.setTimeoutSeconds(dto.timeoutSeconds() > 0 ? dto.timeoutSeconds() : 30);
        target.setEnabled(dto.enabled());
        return target;
    }
}
