package com.finops.agentsafe.model.mock;

import org.springframework.stereotype.Component;

/**
 * Spring bean exposing MockModelAdapter to ModelAdapterRegistry.
 */
@Component
public class MockModelAdapterBean extends MockModelAdapter {
    public MockModelAdapterBean() {
        super(MockMode.DETERMINISTIC_SUCCESS);
    }
}
