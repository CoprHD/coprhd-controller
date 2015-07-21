/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.util;

public interface NameGenerator {
    /**
     * Create a name generator interface that can generate unique names given the
     * following
     *   o Maximum Identifier Length
     *   o Identifier Delimiter
     *   o Tenant Label
     *   o Resource Label
     *   o Resource UID
     * Generate naming for the following identifiers when creating these resources
     *   o Volume Names
     *   o File Share Names
     * Generate naming for these identifiers when provisioning:
     *   o Export Paths
     *   o Host Names
     * Inject this naming generator where appropriate in the controller(s)
     * Have the generate scrub and translate special characters,
     * spaces and delimiter characters. Use the name identifier for the resource when
     * modifying the resource such as export/un-exporting or taking snap shots deleting
     * the resource
     *   o Any future operations that address the resource for modification or
     *   additional operations
     * Use the labels provided with the tenant facing API such that administrators can
     * track or troubleshoot tenant issues from the craft interfaces.
     *
     * @param tenant     - Name of Tenant
     * @param resource   - Name of the Resource (User-provided label)
     * @param resourceURN- Unique ID of the Resource (URN). If this value is null,
     *                   a random UUID will be generated for use in the name.
     * @param delimiter  - Delimiter character to be used in the name
     * @param maxLength  - Maximum size of the string that will be generated
     * @return           - Generated name based on input parameters.
     */
    String generate(String tenant, String resource, String resourceURN,
                    char delimiter, int maxLength);
}
