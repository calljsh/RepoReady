package com.jsh.github;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;

class GithubClientTest {
    private final GithubClient client = new GithubClient(new ObjectMapper(), "");

    @Test
    void acceptsCanonicalRepository() {
        assertEquals("owner/repo", client.repositoryPath("https://github.com/owner/repo.git/"));
    }

    @Test
    void rejectsHostConfusionAndExtraSegments() {
        assertThrows(IllegalArgumentException.class, () -> client.repositoryPath("https://github.com.evil.test/owner/repo"));
        assertThrows(IllegalArgumentException.class, () -> client.repositoryPath("https://github.com/owner/repo/tree/main"));
        assertThrows(IllegalArgumentException.class, () -> client.repositoryPath("https://github.com/owner/repo?token=x"));
        assertThrows(IllegalArgumentException.class, () -> client.repositoryPath("http://github.com/owner/repo"));
    }

    @Test
    void decodesTextAndRejectsBinaryOrOversizedBlob() throws Exception {
        GithubClient spy = spy(client);
        ObjectMapper mapper = new ObjectMapper();
        doReturn(mapper.readTree("{\"encoding\":\"base64\",\"size\":5,\"content\":\"aGVsbG8=\"}"))
                .when(spy).get("/repos/a/b/git/blobs/ok");
        assertEquals("hello", spy.blob("a/b", "ok"));
        doReturn(mapper.readTree("{\"encoding\":\"base64\",\"size\":70000,\"content\":\"aGVsbG8=\"}"))
                .when(spy).get("/repos/a/b/git/blobs/big");
        assertThrows(IllegalArgumentException.class, () -> spy.blob("a/b", "big"));
        doReturn(mapper.readTree("{\"encoding\":\"base64\",\"size\":2,\"content\":\"AAE=\"}"))
                .when(spy).get("/repos/a/b/git/blobs/binary");
        assertThrows(IllegalArgumentException.class, () -> spy.blob("a/b", "binary"));
        doReturn(mapper.readTree("{\"encoding\":\"utf-8\",\"size\":5,\"content\":\"hello\"}"))
                .when(spy).get("/repos/a/b/git/blobs/encoding");
        assertThrows(IllegalArgumentException.class, () -> spy.blob("a/b", "encoding"));
    }
}
