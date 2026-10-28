package com.swu.atch.config;

import com.swu.atch.auth.dto.request.LoginRequest;
import com.swu.atch.auth.dto.request.SignUpRequest;
import com.swu.atch.support.IsolatedAuthIntegrationTest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class AuthOpenApiExamplesTest extends IsolatedAuthIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper json;
    @Autowired private Validator validator;

    @Test
    void signupAndLoginProvideUsableExamplesMatchingTheirRealDtos() throws Exception {
        String content = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode api = json.readTree(content);
        for (String operation : List.of("signup", "login")) {
            var request = api.path("paths").path("/api/v1/auth/" + operation).path("post").path("requestBody");
            assertThat(request.path("required").asBoolean()).isTrue();
            var media = request.path("content").path("application/json");
            String name = operation.equals("signup") ? "SignUpRequest" : "LoginRequest";
            assertThat(media.path("schema").path("$ref").asText()).isEqualTo("#/components/schemas/" + name);
            JsonNode example = media.path("examples").properties().iterator().next().getValue().path("value");
            assertThat(example.isObject()).isTrue();
            assertThat(example.path("email").asText()).isEqualTo("user@example.com");
            assertThat(example.path("deviceId").asText()).isNotBlank();
            Object dto = operation.equals("signup")
                    ? json.treeToValue(example, SignUpRequest.class)
                    : json.treeToValue(example, LoginRequest.class);
            assertThat(validator.validate(dto)).isEmpty();
            var schema = api.path("components").path("schemas").path(name);
            for (var property : example.properties()) {
                assertThat(schema.path("properties").has(property.getKey())).isTrue();
            }
        }
        var evidence = Path.of("build/verification/cors-auth-openapi.json");
        Files.createDirectories(evidence.getParent());
        Files.writeString(evidence, content);
    }
}
