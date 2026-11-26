package com.jsh.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScanRulesTest {
    private final ScanRules rules = new ScanRules();

    @Test
    void detectsRisksWithoutExposingSecret() {
        String secret = "sk-abcdefghijklmnop";
        List<ScanReport.Finding> findings = rules.inspect("scripts/start.ps1", "token=" + secret + "\npath=C:\\Users\\Alice\\work");
        assertEquals(2, findings.size());
        assertEquals(1, findings.get(0).line);
        assertFalse(findings.toString().contains(secret));
        assertEquals("HIGH", findings.get(0).severity);
    }

    @Test
    void scoresCompletenessAndCapsRiskPenalties() {
        Set<String> paths = new HashSet<>(Arrays.asList("README.md", "pom.xml", "start.sh"));
        assertEquals(100, rules.score(paths, Collections.emptyList()).values().stream().mapToInt(Integer::intValue).sum());
        List<ScanReport.Finding> risks = new ArrayList<>();
        for (int i = 0; i < 10; i++) risks.addAll(rules.inspect(".env", null));
        assertEquals(0, rules.score(paths, risks).get("security"));
    }
}
