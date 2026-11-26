package com.jsh.api;

import com.jsh.github.GithubException;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    /** 将输入错误转换为安全的响应。 */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<Map<String, String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(Collections.singletonMap("message", "请输入标准的公开 GitHub 仓库链接"));
    }

    /** 避免向调用方回显可能包含敏感信息的上游响应。 */
    @ExceptionHandler(GithubException.class)
    public ResponseEntity<Map<String, String>> github(GithubException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Collections.singletonMap("message", exception.getMessage()));
    }

    /** 返回通用错误，避免暴露文件内容或令牌。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Collections.singletonMap("message", "扫描失败，请稍后重试"));
    }
}
