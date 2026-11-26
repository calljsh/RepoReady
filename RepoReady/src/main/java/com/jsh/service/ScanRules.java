package com.jsh.service;

import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ScanRules {
    private static final Pattern WINDOWS = Pattern.compile("(?i).*\\.(bat|cmd|ps1|vbs)$");
    private static final Pattern DEPENDENCY = Pattern.compile("(?i)(pom.xml|build.gradle(.kts)?|package.json|requirements.txt|pyproject.toml|go.mod|Cargo.toml|Gemfile|composer.json)");
    private static final Pattern START = Pattern.compile("(?i)(dockerfile|docker-compose.y(a)?ml|compose.y(a)?ml|makefile|gradlew|mvnw|start[^/]*\\.(sh|bat|cmd|ps1))");
    private static final Pattern SECRET = Pattern.compile("(?i)(sk-[A-Za-z0-9]{12,}|AKID[A-Za-z0-9]{12,}|Bearer\\s+[A-Za-z0-9._~+/-]{12,}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----)");
    private static final Pattern PATH = Pattern.compile("(?i)(?:[A-Z]:\\\\(?:Users|Program Files|[^\\\\\\s]+)\\\\|/Users/[^/\\s]+/|/home/[^/\\s]+/)");

    /** 生成风险项，不保留命中的敏感信息原值。 */
    public List<ScanReport.Finding> inspect(String path, String content) {
        List<ScanReport.Finding> findings = new ArrayList<>();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (content == null && WINDOWS.matcher(name).matches()) {
            findings.add(new ScanReport.Finding("MEDIUM", "portability", "Windows 专用脚本", path, null, "提供跨平台启动方式"));
        }
        if (content == null && (name.equals(".env") || name.startsWith(".env."))) {
            findings.add(new ScanReport.Finding("HIGH", "security", ".env 文件入库", path, null, "移除真实凭据并改用环境变量"));
        }
        if (content != null) {
            String[] lines = content.split("\\R", -1);
            for (int index = 0; index < lines.length; index++) {
                if (SECRET.matcher(lines[index]).find()) {
                    findings.add(new ScanReport.Finding("HIGH", "security", "疑似凭据或私钥", path, index + 1, "核实并轮换凭据，移除仓库中的原值"));
                }
                if (PATH.matcher(lines[index]).find()) {
                    findings.add(new ScanReport.Finding("MEDIUM", "portability", "疑似本机绝对路径", path, index + 1, "改为相对路径或配置项"));
                }
            }
        }
        return findings;
    }

    /** 按固定的分类权重计算配置完整度与风险得分。 */
    public Map<String, Integer> score(Set<String> paths, List<ScanReport.Finding> findings) {
        boolean readme = paths.stream().anyMatch(p -> p.matches("(?i)(.*/)?readme(?:\\.[^/]*)?"));
        boolean dependency = paths.stream().anyMatch(p -> DEPENDENCY.matcher(p.substring(p.lastIndexOf('/') + 1)).matches());
        boolean start = paths.stream().anyMatch(p -> START.matcher(p.substring(p.lastIndexOf('/') + 1)).matches());
        Map<String, Integer> scores = new LinkedHashMap<>();
        scores.put("documentation", readme ? 25 : 0);
        scores.put("dependencies", dependency ? 20 : 0);
        scores.put("startup", start ? 20 : 0);
        int portability = 15;
        int security = 20;
        for (ScanReport.Finding finding : findings) {
            if ("portability".equals(finding.category)) portability = Math.max(0, portability - 5);
            if ("security".equals(finding.category)) security = Math.max(0, security - 10);
        }
        scores.put("portability", portability);
        scores.put("security", security);
        return scores;
    }
}
