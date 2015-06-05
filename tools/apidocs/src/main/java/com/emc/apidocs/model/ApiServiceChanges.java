/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.model;

import java.util.List;

/**
 * Describes how a service has been modified from the old API to the new API
 */
public class ApiServiceChanges {

    public ApiService service;
    public List<ApiMethod> newMethods;
    public List<ApiMethod> removedMethods;
    public List<ApiMethod> deprecatedMethods;
    public List<ApiMethodChanges> modifiedMethods;

    public boolean containsChanges() {
        return !newMethods.isEmpty() || !removedMethods.isEmpty();
    }
}
