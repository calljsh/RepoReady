package com.jsh.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ScanReport {
    public String repository;
    public String branch;
    public String commit;
    public Instant scannedAt;
    public String ruleVersion = "1";
    public int score;
    public Map<String, Integer> categoryScores;
    public boolean partialScan;
    public int totalFiles;
    public int scannedFiles;
    public int skippedFiles;
    public List<Finding> risks;

    public static class Finding {
        public String severity;
        public String category;
        public String title;
        public String file;
        public Integer line;
        public String recommendation;

        public Finding(String severity, String category, String title, String file, Integer line, String recommendation) {
            this.severity = severity;
            this.category = category;
            this.title = title;
            this.file = file;
            this.line = line;
            this.recommendation = recommendation;
        }
    }
}
