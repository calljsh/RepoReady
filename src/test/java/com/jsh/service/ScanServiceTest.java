package com.jsh.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jsh.github.GithubClient;
import org.junit.jupiter.api.Test;

class ScanServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GithubClient github = mock(GithubClient.class);
    private final ScanService service = new ScanService(github, new ScanRules());
    private final String sha = "0123456789012345678901234567890123456789";

    @Test
    void scansFixedCommitAndMarksUnscannedFiles() throws Exception {
        when(github.repositoryPath(anyString())).thenReturn("owner/repo");
        when(github.get("/repos/owner/repo")).thenReturn(mapper.readTree("{\"default_branch\":\"main\"}"));
        when(github.get("/repos/owner/repo/commits/main")).thenReturn(mapper.readTree(
                "{\"sha\":\"" + sha + "\",\"commit\":{\"tree\":{\"sha\":\"" + sha + "\"}}}"));
        when(github.get("/repos/owner/repo/git/trees/" + sha + "?recursive=1")).thenReturn(mapper.readTree(
                "{\"truncated\":false,\"tree\":[{\"type\":\"blob\",\"path\":\"README.md\",\"size\":12,\"sha\":\"a\"},"
                        + "{\"type\":\"blob\",\"path\":\"pom.xml\",\"size\":8,\"sha\":\"b\"},"
                        + "{\"type\":\"blob\",\"path\":\"start.ps1\",\"size\":10,\"sha\":\"c\"},"
                        + "{\"type\":\"blob\",\"path\":\"logo.png\",\"size\":50,\"sha\":\"d\"}]}"));
        when(github.blob(eq("owner/repo"), anyString())).thenReturn("example");

        ScanReport report = service.scan("https://github.com/owner/repo");
        assertEquals(sha, report.commit);
        assertEquals(4, report.totalFiles);
        assertEquals(3, report.scannedFiles);
        assertEquals(1, report.skippedFiles);
        assertTrue(report.partialScan);
        assertEquals(95, report.score);
        verify(github).get("/repos/owner/repo/git/trees/" + sha + "?recursive=1");
    }

    @Test
    void rejectsInvalidCommitMetadata() throws Exception {
        when(github.repositoryPath(anyString())).thenReturn("owner/repo");
        when(github.get("/repos/owner/repo")).thenReturn(mapper.readTree("{\"default_branch\":\"main\"}"));
        when(github.get("/repos/owner/repo/commits/main")).thenReturn(mapper.readTree("{}"));
        assertThrows(IllegalArgumentException.class, () -> service.scan("url"));
    }

    @Test
    void marksBlobFailureAndTruncatedTree() throws Exception {
        when(github.repositoryPath(anyString())).thenReturn("owner/repo");
        when(github.get("/repos/owner/repo")).thenReturn(mapper.readTree("{\"default_branch\":\"main\"}"));
        when(github.get("/repos/owner/repo/commits/main")).thenReturn(mapper.readTree(
                "{\"sha\":\"" + sha + "\",\"commit\":{\"tree\":{\"sha\":\"" + sha + "\"}}}"));
        when(github.get("/repos/owner/repo/git/trees/" + sha + "?recursive=1")).thenReturn(mapper.readTree(
                "{\"truncated\":true,\"tree\":[{\"type\":\"blob\",\"path\":\".env\",\"size\":10,\"sha\":\"a\"}]}"));
        when(github.blob("owner/repo", "a")).thenThrow(new IllegalArgumentException());
        ScanReport report = service.scan("url");
        assertTrue(report.partialScan);
        assertEquals(0, report.scannedFiles);
        assertEquals(1, report.skippedFiles);
        assertEquals(".env 文件入库", report.risks.get(0).title);
    }

    @Test
    void rejectsMissingDefaultBranch() throws Exception {
        when(github.repositoryPath(anyString())).thenReturn("owner/repo");
        when(github.get("/repos/owner/repo")).thenReturn(mapper.readTree("{}"));
        assertThrows(IllegalArgumentException.class, () -> service.scan("url"));
    }
}
