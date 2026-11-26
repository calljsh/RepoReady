package com.jsh.api;

import static org.junit.jupiter.api.Assertions.*;
import com.jsh.github.GithubException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void doesNotExposeExceptionDetails() {
        assertEquals(HttpStatus.BAD_REQUEST, handler.invalid(new IllegalArgumentException("secret")).getStatusCode());
        assertFalse(handler.invalid(new IllegalArgumentException("secret")).getBody().toString().contains("secret"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS,
                handler.github(new GithubException(HttpStatus.TOO_MANY_REQUESTS, "限流")).getStatusCode());
        assertFalse(handler.unexpected(new RuntimeException("secret")).getBody().toString().contains("secret"));
    }
}
