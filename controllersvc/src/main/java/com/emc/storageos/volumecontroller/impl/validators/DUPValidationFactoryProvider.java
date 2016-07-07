package com.emc.storageos.volumecontroller.impl.validators;

import java.util.Map;

/**
 * A provider of {@link AbstractDUPValidationFactory} instances across the various storage systems.
 */
public class DUPValidationFactoryProvider {

    private Map<String, AbstractDUPValidationFactory> factories;

    public void setFactories(Map<String, AbstractDUPValidationFactory> factories) {
        this.factories = factories;
    }

    public AbstractDUPValidationFactory vmax() {
        return factories.get("vmax");
    }

    public AbstractDUPValidationFactory xtremio() {
        return factories.get("xtremio");
    }
}
