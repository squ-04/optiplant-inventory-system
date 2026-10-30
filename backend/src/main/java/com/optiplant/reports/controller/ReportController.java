package com.optiplant.reports.controller;

import com.optiplant.identity.service.SessionPrincipal;
import com.optiplant.reports.service.ReportService;
import com.optiplant.reports.service.ReportService.ReportFile;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Direct download of the exportable reports (RF-0050–RF-0055). Nothing is stored or generated in the background; errors
 * are ProblemDetail documents, never a partial file. {@code X-Report-Rows} tells the client how many records the file holds.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/movements")
    public ResponseEntity<byte[]> movements(@AuthenticationPrincipal SessionPrincipal principal,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) UUID branch) {
        return download(reports.movements(principal, from, to, branch));
    }

    @GetMapping("/sales")
    public ResponseEntity<byte[]> sales(@AuthenticationPrincipal SessionPrincipal principal,
                                        @RequestParam(required = false) String from,
                                        @RequestParam(required = false) String to,
                                        @RequestParam(required = false) UUID branch) {
        return download(reports.sales(principal, from, to, branch));
    }

    @GetMapping("/transfers")
    public ResponseEntity<byte[]> transfers(@AuthenticationPrincipal SessionPrincipal principal,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) UUID branch,
                                            @RequestParam(required = false) String status) {
        return download(reports.transfers(principal, from, to, branch, status));
    }

    private static ResponseEntity<byte[]> download(ReportFile file) {
        return ResponseEntity.ok().contentType(XLSX).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name()).build().toString())
            .header("X-Report-Rows", String.valueOf(file.rows()))
            .body(file.content());
    }
}
