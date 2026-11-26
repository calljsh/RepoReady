package com.jsh;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RepoReadyApplication {
    /** 启动 HTTP 服务。 */
    public static void main(String[] args) {
        SpringApplication.run(RepoReadyApplication.class, args);
    }
}
