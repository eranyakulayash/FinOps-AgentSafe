package com.finops.agentsafe.model;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry managing provider-neutral ModelAdapters by logical names.
 */
@Component
public class ModelAdapterRegistry {

    private final Map<String, ModelAdapter> adapters = new ConcurrentHashMap<>();

    public ModelAdapterRegistry(List<ModelAdapter> adapterList) {
        if (adapterList != null) {
            for (ModelAdapter adapter : adapterList) {
                registerAdapter(adapter);
            }
        }
    }

    public void registerAdapter(ModelAdapter adapter) {
        if (adapter != null && adapter.getProviderName() != null) {
            adapters.put(adapter.getProviderName().toLowerCase(Locale.ROOT), adapter);
        }
    }

    public Optional<ModelAdapter> getAdapter(String providerName) {
        if (providerName == null) return Optional.empty();
        return Optional.ofNullable(adapters.get(providerName.toLowerCase(Locale.ROOT)));
    }

    public Collection<ModelAdapter> getAllAdapters() {
        return Collections.unmodifiableCollection(adapters.values());
    }

    public boolean hasAdapter(String providerName) {
        if (providerName == null) return false;
        return adapters.containsKey(providerName.toLowerCase(Locale.ROOT));
    }
}
