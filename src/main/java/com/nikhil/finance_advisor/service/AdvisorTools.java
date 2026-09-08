package com.nikhil.finance_advisor.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The tool schemas declared to the model.
 *
 * Loaded from a versioned resource for the same reason as the prompt: so the
 * definitions can be reviewed as a diff. Nothing is executed here — the client
 * runs the calculations against the financial logic the user's screen uses.
 */
@Component
public class AdvisorTools {

    public static final String TOOLS_VERSION = "v1";

    private static final String TOOLS_RESOURCE = "tools/advisor-tools." + TOOLS_VERSION + ".json";

    private final List<Map<String, Object>> declarations;

    public AdvisorTools(ObjectMapper objectMapper) {
        this.declarations = load(objectMapper);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> load(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(TOOLS_RESOURCE).getInputStream()) {
            Map<String, Object> parsed = objectMapper.readValue(
                    StreamUtils.copyToString(in, StandardCharsets.UTF_8), Map.class);
            Object tools = parsed.get("tools");
            if (!(tools instanceof List<?> list) || list.isEmpty()) {
                throw new IllegalStateException("No tools declared in " + TOOLS_RESOURCE);
            }
            return List.copyOf((List<Map<String, Object>>) list);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load tool declarations: " + TOOLS_RESOURCE, e);
        }
    }

    /** The `tools` array sent to Groq, in OpenAI function-calling format. */
    public List<Map<String, Object>> declarations() {
        return declarations;
    }

    @SuppressWarnings("unchecked")
    public List<String> names() {
        return declarations.stream()
                .map(tool -> (Map<String, Object>) tool.get("function"))
                .map(function -> (String) function.get("name"))
                .toList();
    }
}
