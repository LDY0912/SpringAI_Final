package com.skala.helpdesk.advisor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class AdvisorPolicyTest {

    @Test
    void 차단은_메모리_순서_200보다_앞에_있다() {
        SafetyAdvisor safety = new SafetyAdvisor(new SimpleMeterRegistry());

        assertThat(safety.getOrder()).isEqualTo(100).isLessThan(200);
    }

    @Test
    void 감사는_가장_바깥이고_계측은_가장_안쪽이다() {
        AuditAdvisor audit = new AuditAdvisor();
        TokenMeterAdvisor meter = new TokenMeterAdvisor(new SimpleMeterRegistry());

        assertThat(audit.getOrder()).isEqualTo(0);
        assertThat(meter.getOrder()).isEqualTo(900);
        assertThat(audit.getOrder()).isLessThan(meter.getOrder());
    }

    @Test
    void 안전과_계측은_동기와_스트리밍에_모두_적용된다() {
        SafetyAdvisor safety = new SafetyAdvisor(new SimpleMeterRegistry());
        TokenMeterAdvisor meter = new TokenMeterAdvisor(new SimpleMeterRegistry());

        assertThat(safety).isInstanceOf(StreamAdvisor.class);
        assertThat(meter).isInstanceOf(StreamAdvisor.class);
    }
}
