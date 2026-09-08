package com.nikhil.finance_advisor.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool declarations, and the contract with the frontend that executes them.
 *
 * advisor-tools.json is generated from the frontend's own TOOL_MANIFEST and
 * checked in on both sides. A tool declared here but not implemented there means
 * the model asks for a calculation nobody can run; a parameter renamed on one
 * side means the model supplies an argument the handler ignores, and answers
 * with a default it never mentioned. Both fail this test.
 */
@SpringBootTest(properties = "groq.api.key=test-key-not-used")
class AdvisorToolsTest {

    @Autowired
    private AdvisorTools advisorTools;

    @Autowired
    private ObjectMapper objectMapper;

    private record ToolContract(List<Tool> tools) {
        private record Tool(String name, List<String> parameters) {}
    }

    private ToolContract frontendContract() throws IOException {
        try (InputStream in = new ClassPathResource("contract/advisor-tools.json").getInputStream()) {
            return objectMapper.readValue(
                    StreamUtils.copyToString(in, StandardCharsets.UTF_8), ToolContract.class);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> functionNamed(String name) {
        return advisorTools.declarations().stream()
                .map(tool -> (Map<String, Object>) tool.get("function"))
                .filter(function -> name.equals(function.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No tool declared named " + name));
    }

    @Test
    void loadsTheDeclarationsAtStartup() {
        assertThat(advisorTools.declarations()).isNotEmpty();
        assertThat(AdvisorTools.TOOLS_VERSION).isEqualTo("v1");
    }

    @Test
    void declaresEveryToolTheFrontendImplements() throws IOException {
        List<String> implemented = frontendContract().tools().stream().map(ToolContract.Tool::name).toList();

        assertThat(advisorTools.names()).containsExactlyInAnyOrderElementsOf(implemented);
    }

    @Test
    void declaresOnlyParametersTheFrontendReads() throws IOException {
        for (ToolContract.Tool tool : frontendContract().tools()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = (Map<String, Object>) functionNamed(tool.name()).get("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) parameters.get("properties");

            assertThat(properties.keySet())
                    .as("parameters declared for %s", tool.name())
                    .containsExactlyInAnyOrderElementsOf(tool.parameters());
        }
    }

    @Test
    void everyDeclarationIsShapedTheWayTheApiExpects() {
        for (Map<String, Object> tool : advisorTools.declarations()) {
            assertThat(tool.get("type")).isEqualTo("function");
            assertThat(tool.get("function")).isInstanceOf(Map.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> function = (Map<String, Object>) tool.get("function");
            assertThat(function.get("name")).asString().isNotBlank();
            assertThat(function.get("description")).asString()
                    .as("a tool without a description is one the model will misuse")
                    .isNotBlank();
            assertThat(function.get("parameters")).isInstanceOf(Map.class);
        }
    }

    @Test
    void declarationsAreImmutable() {
        assertThat(advisorTools.declarations()).isUnmodifiable();
    }
}
