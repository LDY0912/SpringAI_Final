package com.skala.helpdesk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 코드에 남기지 않고 application.yml에서 조정하는 종합 실습 설정. */
@ConfigurationProperties(prefix = "helpdesk")
public record HelpDeskProperties(
        String tenantId,
        Rag rag,
        Memory memory,
        Ingest ingest,
        Security security) {

    public record Rag(int topK, double threshold) {}
    public record Memory(int maxMessages) {}
    public record Ingest(int chunkSize, int minChunkSizeChars, String version) {}
    public record Security(String user1Password, String user2Password, String adminPassword) {}
}
