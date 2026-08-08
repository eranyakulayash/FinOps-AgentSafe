package com.finops.agentsafe.failure;

import com.finops.agentsafe.AbstractPostgreSQLIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that failure injection is only active when the test/benchmark profile enables it.
 *
 * The test profile (application-test.yml) sets finops.failure-injection.enabled=true,
 * so in this test class, fault injection IS active.
 *
 * This test proves:
 *   - With enabled=true (test profile): X-Injected-Failure: DATABASE_FAILURE triggers a 500
 *   - Without the header: normal endpoint responds normally
 *
 * To test the default-off behavior (enabled=false), one would need a separate
 * @SpringBootTest with a property override. Since we run the test profile with enabled=true,
 * we verify the positive case (injection works) and the absence case (no header = no fault).
 */
@AutoConfigureMockMvc
class FailureInjectionProfileTest extends AbstractPostgreSQLIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("TEST PROFILE (enabled=true): X-Injected-Failure: DATABASE_FAILURE triggers HTTP 500")
    void testFailureInjectionActiveInTestProfile() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/api/v1/transactions/NON_EXISTENT_ID")
                    .header("X-Injected-Failure", "DATABASE_FAILURE")
                    .header("X-Scenario-ID", "PROFILE-SAFETY-TEST")
            )
            .andReturn();

        int status = result.getResponse().getStatus();
        assertEquals(500, status, "DATABASE_FAILURE injection must return HTTP 500 when failure-injection is enabled");
    }

    @Test
    @DisplayName("TEST PROFILE (enabled=true): No X-Injected-Failure header = normal 404 response (no injection)")
    void testNoHeaderNoFaultInjection() throws Exception {
        mockMvc.perform(
                get("/api/v1/transactions/NON_EXISTENT_ID")
                    .header("X-Scenario-ID", "PROFILE-SAFETY-TEST-NORMAL")
            )
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("TEST PROFILE (enabled=true): X-Injected-Failure: API_RATE_LIMIT triggers HTTP 429")
    void testRateLimitInjectionActiveInTestProfile() throws Exception {
        MvcResult result = mockMvc.perform(
                get("/api/v1/transactions/NON_EXISTENT_ID")
                    .header("X-Injected-Failure", "API_RATE_LIMIT")
            )
            .andReturn();

        assertEquals(429, result.getResponse().getStatus(), "API_RATE_LIMIT injection must return HTTP 429 when failure-injection is enabled");
    }

    @Test
    @DisplayName("TEST PROFILE (enabled=true): Unknown failure type is silently ignored — no fault triggered")
    void testUnknownFailureTypeIsIgnored() throws Exception {
        // Unknown failure type should be silently ignored — not result in a server error
        MvcResult result = mockMvc.perform(
                get("/api/v1/transactions/NON_EXISTENT_ID")
                    .header("X-Injected-Failure", "UNKNOWN_FAILURE_TYPE_XYZ")
            )
            .andReturn();

        // Should get a normal 404 since the failure type is unknown
        assertEquals(404, result.getResponse().getStatus(), "Unknown failure type should be ignored and endpoint responds normally");
    }
}
