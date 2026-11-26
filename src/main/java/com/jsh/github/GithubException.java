package com.jsh.github;

import org.springframework.http.HttpStatus;

public class GithubException extends RuntimeException {
    private final HttpStatus status;

    public GithubException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** 返回上游请求失败对应的安全 HTTP 状态码。 */
    public HttpStatus getStatus() {
        return status;
    }
}
