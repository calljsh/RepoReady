package com.jsh.api;

import com.jsh.service.ScanService;
import com.jsh.service.ScanReport;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ScanController {
    private final ScanService scanService;

    public ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** 扫描仓库默认分支当前指向的提交。 */
    @PostMapping("/reports/scan")
    public ScanReport scan(@Valid @RequestBody ScanRequest request) {
        return scanService.scan(request.repositoryUrl);
    }

    /** 返回 HTTP 服务的运行状态。 */
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    public static class ScanRequest {
        @NotBlank
        public String repositoryUrl;
    }
}
