package com.example.exceptionhandling.config;

import com.example.exceptionhandling.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Replaces Spring Boot's BasicErrorController (/error endpoint).
 *
 * This catches errors that never reach a @RestControllerAdvice, such as:
 *  - 404s when spring.mvc.throw-exception-if-no-handler-found=false (the default)
 *  - Filter-level exceptions
 *  - Servlet container errors
 */
@RestController
@RequestMapping("${server.error.path:${error.path:/error}}")
public class ErrorControllerConfig implements ErrorController {

    private final ErrorAttributes errorAttributes;

    public ErrorControllerConfig(ErrorAttributes errorAttributes) {
        this.errorAttributes = errorAttributes;
    }

    @RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        var webRequest = new org.springframework.web.context.request.ServletWebRequest(request);
        Throwable throwable = errorAttributes.getError(webRequest);

        Map<String, Object> attrs = errorAttributes.getErrorAttributes(webRequest,
                org.springframework.boot.web.error.ErrorAttributeOptions.defaults());

        int statusCode = (int) attrs.getOrDefault("status", 500);
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        String message = throwable != null
                ? throwable.getMessage()
                : (String) attrs.getOrDefault("error", status.getReasonPhrase());

        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), (String) attrs.get("path"), message));
    }
}
