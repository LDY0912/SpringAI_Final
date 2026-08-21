package com.skala.helpdesk.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import com.skala.helpdesk.config.HelpDeskProperties;

class HelpDeskIngestServiceTest {

    private VectorStore vectorStore;
    private HelpDeskIngestService service;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        HelpDeskProperties properties = new HelpDeskProperties(
                "skala",
                new HelpDeskProperties.Rag(5, 0.3),
                new HelpDeskProperties.Memory(20),
                new HelpDeskProperties.Ingest(800, 350, "test-v1"),
                new HelpDeskProperties.Security("u1", "u2", "admin"));
        service = new HelpDeskIngestService(vectorStore, properties);
    }

    @Test
    void 첫_인제스트는_세_문서에_출처와_버전을_넣는다() {
        var result = service.ingestSamples();

        assertThat(result).hasSize(3)
                .allSatisfy(item -> {
                    assertThat(item.version()).isEqualTo("test-v1");
                    assertThat(item.chunks()).isPositive();
                    assertThat(item.chunkSize()).isEqualTo(800);
                });
        verify(vectorStore, times(3)).add(anyList());
    }

    @Test
    void 같은_문서를_다시_넣으면_기존_청크를_먼저_삭제한다() {
        service.ingestSamples();
        service.ingestSamples();

        verify(vectorStore, times(6)).add(anyList());
        verify(vectorStore, times(3)).delete(anyList());
    }

    @Test
    void 품질_확인_검색어는_비어_있을_수_없다() {
        assertThatThrownBy(() -> service.inspect(" ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("검색어");
    }
}
