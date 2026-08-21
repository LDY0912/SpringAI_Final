package com.skala.helpdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** RAG·Tool·Memory·Safety·Observability를 합친 SKALA HelpDesk 종합 실습. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class HelpDeskApplication {

    public static void main(String[] args) {
        SpringApplication.run(HelpDeskApplication.class, args);
    }
}
