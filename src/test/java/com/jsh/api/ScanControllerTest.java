package com.jsh.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jsh.service.ScanReport;
import com.jsh.service.ScanService;
import org.junit.jupiter.api.Test;

class ScanControllerTest {
    @Test
    void delegatesScanAndReportsHealth() {
        ScanService service = mock(ScanService.class);
        ScanReport expected = new ScanReport();
        when(service.scan("https://github.com/a/b")).thenReturn(expected);
        ScanController controller = new ScanController(service);
        ScanController.ScanRequest request = new ScanController.ScanRequest();
        request.repositoryUrl = "https://github.com/a/b";
        assertSame(expected, controller.scan(request));
        assertEquals("ok", controller.health());
    }
}
