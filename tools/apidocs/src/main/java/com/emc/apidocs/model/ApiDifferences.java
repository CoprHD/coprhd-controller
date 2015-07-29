/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.model;

import java.util.List;

public class ApiDifferences {
    public List<ApiService> newServices;
    public List<ApiService> removedServices;
    public List<ApiServiceChanges> modifiedServices;

    public Change getChange(String serviceName) {
        for (ApiService apiService : newServices) {
            if (apiService.javaClassName.equals(serviceName)) {
                return Change.NEW;
            }
        }

        for (ApiService apiService : removedServices) {
            if (apiService.javaClassName.equals(serviceName)) {
                return Change.REMOVED;
            }
        }

        return Change.NOT_CHANGED;
    }

    public Change getChange(String serviceName, String javaMethodName) {
        Change serviceChange = getChange(serviceName);

        if (serviceChange == Change.NOT_CHANGED) {
            for (ApiServiceChanges serviceChanges : modifiedServices) {
                if (serviceChanges.service.javaClassName.equals(serviceName)) {

                    for (ApiMethod newMethod : serviceChanges.newMethods) {
                        if (newMethod.javaMethodName.equals(javaMethodName)) {
                            return Change.NEW;
                        }
                    }

                    for (ApiMethodChanges methodChange : serviceChanges.modifiedMethods) {
                        if (methodChange.method.javaMethodName.equals(javaMethodName)) {
                            return Change.MODIFIED;
                        }
                    }
                }
            }

            return Change.NOT_CHANGED;
        }

        return serviceChange;
    }

    public static enum Change {
        NEW,
        REMOVED,
        MODIFIED,
        NOT_CHANGED
    }
}
