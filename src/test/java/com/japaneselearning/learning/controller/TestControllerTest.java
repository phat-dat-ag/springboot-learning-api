package com.japaneselearning.learning.controller;

import com.japaneselearning.learning.config.SecurityConfig;
import com.japaneselearning.learning.common.filter.CorrelationIdFilter;
import com.japaneselearning.learning.logging.ApiLoggingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")
@AutoConfigureMockMvc
@Import({SecurityConfig.class, CorrelationIdFilter.class, ApiLoggingFilter.class})
@ExtendWith(OutputCaptureExtension.class)
class TestControllerTest {

    @Test
    void swaggerAndOpenApiArePublic() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Learning API"))
                .andExpect(jsonPath("$.paths['/test'].get").exists());
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicTestEndpointLogsRequestWithCorrelationId(CapturedOutput output) throws Exception {
        var result = mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, test"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andReturn();

        String correlationId = result.getResponse().getHeader("X-Correlation-ID");
        assertThat(output.getOut()).contains("API request completed", "\"httpMethod\":\"GET\"",
                "\"uri\":\"/test\"", "\"status\":\"200\"", "\"elapsed_ms\":",
                "\"correlationId\":\"" + correlationId + "\"", "\"service\":\"learning-api\"");
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("httpMethod")).isNull();
        assertThat(MDC.get("uri")).isNull();
        assertThat(MDC.get("status")).isNull();
        assertThat(MDC.get("elapsed_ms")).isNull();
    }
}
