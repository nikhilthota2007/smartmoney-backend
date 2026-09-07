package com.nikhil.finance_advisor.prompt;

import com.nikhil.finance_advisor.model.FinancialData;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Loads the advisor's system prompt from a versioned file on the classpath and
 * fills in the user's figures.
 *
 * The prompt lives in src/main/resources/prompts/ rather than in Java source so
 * it can be reviewed as a diff and rolled back on its own. Bump PROMPT_VERSION
 * and add a new file when the wording changes materially.
 */
@Component
public class AdvisorPrompt {

    public static final String PROMPT_VERSION = "v2";

    private static final String PROMPT_RESOURCE = "prompts/advisor-system-prompt." + PROMPT_VERSION + ".md";
    private static final String NOT_PROVIDED = "Not provided";

    /** Everything up to and including this marker is authoring notes, not prompt text. */
    private static final String HEADER_END = "-->";

    private final String template;

    public AdvisorPrompt() {
        this.template = loadTemplate();
    }

    private static String loadTemplate() {
        ClassPathResource resource = new ClassPathResource(PROMPT_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            String raw = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            int headerEnd = raw.indexOf(HEADER_END);
            return (headerEnd >= 0 ? raw.substring(headerEnd + HEADER_END.length()) : raw).strip();
        } catch (IOException e) {
            // Without a prompt the service cannot answer safely, so fail at startup
            // rather than silently serving an unguarded model.
            throw new UncheckedIOException("Unable to load advisor prompt: " + PROMPT_RESOURCE, e);
        }
    }

    /** The system prompt for this user, with their figures substituted in. */
    public String build(FinancialData data) {
        FinancialData safe = data != null ? data : new FinancialData();

        Map<String, String> values = Map.of(
                "monthlyIncome", orNotProvided(safe.getMonthlyIncome()),
                "monthlyExpenses", orNotProvided(safe.getMonthlyExpenses()),
                "savings", orNotProvided(safe.getSavings()),
                "debts", orNotProvided(safe.getDebts()),
                "goals", orNotProvided(safe.getGoals()));

        String prompt = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return prompt;
    }

    private static String orNotProvided(String value) {
        return value == null || value.isBlank() ? NOT_PROVIDED : value;
    }
}
