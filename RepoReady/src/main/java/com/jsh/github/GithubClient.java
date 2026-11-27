package com.jsh.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
public class GithubClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(GithubClient.class);
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.-]{1,100}");
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final String token;

    public GithubClient(ObjectMapper mapper, @Value("${github.token:}") String token) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String method) throws java.io.IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        this.http = new RestTemplate(factory);
        this.mapper = mapper;
        this.token = token;
        LOGGER.info("GitHub API 访问模式：{}", token.isEmpty() ? "匿名" : "令牌认证");
    }

    /** 仅接受标准的 github.com 仓库地址。 */
    public String repositoryPath(String input) {
        try {
            URI uri = URI.create(input);
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost())
                    || uri.getPort() != -1 || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new IllegalArgumentException();
            }
            String path = uri.getRawPath().replaceFirst("/+$", "").replaceFirst("\\.git$", "");
            String[] parts = path.split("/", -1);
            if (parts.length != 3 || !parts[0].isEmpty() || !PART.matcher(parts[1]).matches()
                    || !PART.matcher(parts[2]).matches() || parts[1].startsWith(".") || parts[2].startsWith(".")) {
                throw new IllegalArgumentException();
            }
            return parts[1] + "/" + parts[2];
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid GitHub URL");
        }
    }

    /** 从固定的 GitHub API 主机读取 JSON 资源。 */
    public JsonNode get(String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github+json");
        headers.set("X-GitHub-Api-Version", "2022-11-28");
        headers.set("User-Agent", "RepoReady/1.0");
        if (!token.isEmpty()) {
            headers.setBearerAuth(token);
        }
        try {
            ResponseEntity<String> response = http.exchange("https://api.github.com" + path,
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return mapper.readTree(response.getBody());
        } catch (HttpStatusCodeException exception) {
            int code = exception.getRawStatusCode();
            if (code == 404) {
                throw new GithubException(HttpStatus.NOT_FOUND, "仓库不存在或不可公开访问");
            }
            if (code == 429 || (code == 403 && exception.getResponseHeaders() != null
                    && "0".equals(exception.getResponseHeaders().getFirst("X-RateLimit-Remaining")))) {
                throw new GithubException(HttpStatus.TOO_MANY_REQUESTS, "GitHub 限流，请稍后重试");
            }
            if (code == 403) {
                throw new GithubException(HttpStatus.FORBIDDEN, "GitHub 拒绝访问，请检查令牌权限");
            }
            throw new GithubException(HttpStatus.BAD_GATEWAY, "GitHub 请求失败，请稍后重试");
        } catch (ResourceAccessException exception) {
            throw new GithubException(HttpStatus.GATEWAY_TIMEOUT, "GitHub 连接超时，请稍后重试");
        } catch (GithubException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new GithubException(HttpStatus.BAD_GATEWAY, "GitHub 返回了无效数据");
        }
    }

    /** 解码大小受限的 UTF-8 文件；无效内容不参与扫描。 */
    public String blob(String repo, String sha) {
        JsonNode blob = get("/repos/" + repo + "/git/blobs/" + sha);
        if (!"base64".equals(blob.path("encoding").asText()) || blob.path("size").asInt() > 65536) {
            throw new IllegalArgumentException("Unsupported blob");
        }
        byte[] bytes = Base64.getMimeDecoder().decode(blob.path("content").asText());
        if (bytes.length > 65536) {
            throw new IllegalArgumentException("Oversized blob");
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        if (content.indexOf('\0') >= 0 || content.indexOf('\ufffd') >= 0) {
            throw new IllegalArgumentException("Binary blob");
        }
        return content;
    }
}
