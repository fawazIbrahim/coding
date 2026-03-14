package com.example.exceptionhandling.controller;

import com.example.exceptionhandling.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/demo")
public class DemoController {

    record CreateRequest(@NotBlank String name) {}

    @GetMapping("/not-found")
    public void notFound() {
        throw new ResourceNotFoundException("Item with id 42 was not found");
    }

    @GetMapping("/runtime-error")
    public void runtimeError() {
        throw new RuntimeException("Something went wrong");
    }

    @PostMapping("/validate")
    public String validate(@Valid @RequestBody CreateRequest req) {
        return "Created: " + req.name();
    }
}
