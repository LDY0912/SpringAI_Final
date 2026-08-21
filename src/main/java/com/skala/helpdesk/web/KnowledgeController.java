package com.skala.helpdesk.web;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.skala.helpdesk.service.HelpDeskIngestService;

/** 재색인과 검색 품질 확인을 상담 API와 분리한 관리자 창구. */
@Validated
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class KnowledgeController {

    private final HelpDeskIngestService ingestService;

    public KnowledgeController(HelpDeskIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping("/ingest")
    public List<HelpDeskIngestService.IngestResult> ingest() {
        return ingestService.ingestSamples();
    }

    @GetMapping("/chunks")
    public List<HelpDeskIngestService.ChunkView> chunks(
            @RequestParam @NotBlank String q,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topK) {
        return ingestService.inspect(q, topK);
    }
}
