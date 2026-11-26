package com.jsh.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jsh.github.GithubClient;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;


/**
 * 确定扫描对象：校验 URL，只接受符合要求的 GitHub 仓库地址；查询默认分支，并取得它当前指向的完整 commit SHA。后续文件读取以该 commit 的 tree SHA 为准，避免扫描过程中分支更新导致结果混杂。
 * 获取并筛选文件：读取该 commit 的文件树。先用文件名识别 Windows 脚本、`.env` 等风险，再挑选符合类型和大小限制的文本文件读取内容；优先处理 README、依赖清单等文件。
 * 检测内容风险：逐行查找疑似密钥、私钥、Bearer 凭据和本机绝对路径，记录风险类型、严重程度、文件及行号。报告不包含匹配到的凭据原值。
 * 生成报告：按固定权重计算文档、依赖、启动方式、可移植性和安全性得分；按严重程度排列风险，并附上 commit、扫描时间及覆盖情况。文件树截断、文件读取失败或有文件未扫描时，会标记为“部分扫描”。
 */
@Service
public class ScanService {
    private static final int MAX_FILES = 100;
    private static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;
    private static final int MAX_FILE_BYTES = 65536;
    private final GithubClient github;
    private final ScanRules rules;

    public ScanService(GithubClient github, ScanRules rules) {
        this.github = github;
        this.rules = rules;
    }

    /** 基于固定提交和受限的文件读取预算生成报告。 */
    public ScanReport scan(String url) {
        String repo = github.repositoryPath(url); // 校验URL
        JsonNode metadata = github.get("/repos/" + repo);
        String branch = metadata.path("default_branch").asText();
        if (branch.isEmpty()){
            throw new IllegalArgumentException("Missing default branch");
        }
        JsonNode commit = github.get("/repos/" + repo + "/commits/" + branch);
        String sha = commit.path("sha").asText();
        String treeSha = commit.path("commit").path("tree").path("sha").asText();
        if (!sha.matches("[0-9a-f]{40}") || !treeSha.matches("[0-9a-f]{40}")) {
            throw new IllegalArgumentException("Invalid commit");
        }
        JsonNode tree = github.get("/repos/" + repo + "/git/trees/" + treeSha + "?recursive=1");
        ScanReport report = new ScanReport();
        report.repository = repo;
        report.branch = branch;
        report.commit = sha;
        report.scannedAt = Instant.now();
        report.partialScan = tree.path("truncated").asBoolean();
        report.risks = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        List<JsonNode> candidates = new ArrayList<>();
        for (JsonNode entry : tree.path("tree")) {
            if (!"blob".equals(entry.path("type").asText())) continue;
            report.totalFiles++;
            String path = entry.path("path").asText();
            paths.add(path);
            report.risks.addAll(rules.inspect(path, null));
            if (eligible(path, entry.path("size").asInt(MAX_FILE_BYTES + 1))) candidates.add(entry);
        }
        candidates.sort(Comparator.comparingInt(e -> priority(e.path("path").asText())));
        int bytes = 0;
        for (JsonNode entry : candidates) {
            int size = entry.path("size").asInt();
            if (report.scannedFiles >= MAX_FILES || bytes + size > MAX_TOTAL_BYTES) {
                report.partialScan = true;
                continue;
            }
            try {
                String content = github.blob(repo, entry.path("sha").asText());
                report.risks.addAll(rules.inspect(entry.path("path").asText(), content));
                report.scannedFiles++;
                bytes += size;
            } catch (RuntimeException exception) {
                report.partialScan = true;
            }
        }
        report.skippedFiles = report.totalFiles - report.scannedFiles;
        report.partialScan |= report.skippedFiles > 0;
        report.risks.sort(Comparator.comparingInt(f -> "HIGH".equals(f.severity) ? 0 : 1));
        report.categoryScores = rules.score(paths, report.risks);
        report.score = report.categoryScores.values().stream().mapToInt(Integer::intValue).sum();
        return report;
    }

    private boolean eligible(String path, int size) {
        if (size < 0 || size > MAX_FILE_BYTES || path.matches("(?i).*(^|/)(node_modules|target|dist|build|vendor)/.*")) return false;
        return path.matches("(?i).*(\\.(java|js|ts|tsx|jsx|py|go|rs|rb|php|sh|bat|cmd|ps1|vbs|yml|yaml|json|xml|toml|properties|md|txt|env)|(^|/)(README|Dockerfile|Makefile|Gemfile|\\.env(\\..*)?))$");
    }

    private int priority(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        return name.startsWith("readme") || name.equals(".env") || name.equals("pom.xml") || name.equals("package.json") ? 0 : 1;
    }
}
