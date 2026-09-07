package com.nikhil.finance_advisor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The application requires GROQ_API_KEY at startup — deliberately, so a
 * misconfigured deploy fails fast instead of serving errors. The context test
 * supplies a dummy value so it can run without a real key.
 */
@SpringBootTest(properties = "groq.api.key=test-key-not-used")
class FinanceAdvisorApplicationTests {

	@Test
	void contextLoads() {
	}

}
