/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.descriptor;

import java.util.Collection;
import java.util.Locale;

/**
 * Parses a ServiceDescriptor descriptor into its Java representation
 */
public interface ServiceDescriptors {
    /**
     * Lists the service descriptors, using the provided locale for i18n localization.
     * 
     * @param locale
     *            the locale.
     * @return the service descriptors.
     */
    public Collection<ServiceDescriptor> listDescriptors(Locale locale);

    /**
     * Gets a service descriptor by ID, using the provided locale for i18n localization.
     * 
     * @return the service descriptor, or null if it cannot be found.
     */
    public ServiceDescriptor getDescriptor(Locale locale, String serviceId);
}
