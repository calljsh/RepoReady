package com.jsh.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class ScanReport {
    /** 仓库标识，格式为 owner/repo。 */
    public String repository;
    /** 本次扫描所依据的默认分支名称。 */
    public String branch;
    /** 本次扫描所依据的完整提交 SHA。 */
    public String commit;
    /** 报告生成时间。 */
    public Instant scannedAt;
    /** 评分与检测规则的版本。 */
    public String ruleVersion = "1";
    /** 接入就绪度总分，范围为 0 至 100。 */
    public int score;
    /** 各评分维度的得分。 */
    public Map<String, Integer> categoryScores;
    /** 是否因文件树截断、预算限制或读取失败而未完成全量扫描。 */
    public boolean partialScan;
    /** 当前文件树中的文件总数。 */
    public int totalFiles;
    /** 已成功读取内容的文件数。 */
    public int scannedFiles;
    /** 未读取内容的文件数。 */
    public int skippedFiles;
    /** 检测出的风险项，按严重程度排序。 */
    public List<Finding> risks;

    public static class Finding {
        /** 风险严重程度，如 HIGH 或 MEDIUM。 */
        public String severity;
        /** 风险所属评分维度。 */
        public String category;
        /** 不包含敏感信息原值的风险标题。 */
        public String title;
        /** 风险所在的仓库相对路径。 */
        public String file;
        /** 内容命中时的行号；文件名规则命中时为空。 */
        public Integer line;
        /** 建议采取的整改措施。 */
        public String recommendation;

        /** 创建不包含敏感信息原值的风险项。 */
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
