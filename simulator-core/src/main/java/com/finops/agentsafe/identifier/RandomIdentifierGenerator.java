package com.finops.agentsafe.identifier;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Default IdentifierGenerator using UUID.randomUUID().
 * Used in normal production execution.
 */
@Component
public class RandomIdentifierGenerator implements IdentifierGenerator {

    @Override
    public UUID nextUUID() {
        return UUID.randomUUID();
    }
}
