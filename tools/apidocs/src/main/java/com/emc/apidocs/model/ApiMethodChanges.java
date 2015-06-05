/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.apidocs.model;

import com.google.common.collect.Lists;

import java.util.List;

public class ApiMethodChanges {
    public ApiMethod method;

    public boolean compatibleChange;

    public List<String> newRoles = Lists.newArrayList();
    public List<String> removedRoles = Lists.newArrayList();

    public List<String> newPrerequists = Lists.newArrayList();
    public List<String> removedPrerequists = Lists.newArrayList();

    public List<ApiField> queryParameters = Lists.newArrayList();
    public List<ApiField> pathParameters = Lists.newArrayList();
    public List<ApiField> headerParameters = Lists.newArrayList();
    public List<ApiField> responseHeaders = Lists.newArrayList();

    public boolean requestPayloadChanged = false;
    public boolean responsePayloadChanged = false;
    public boolean queryParametersChanged = false;
    public boolean pathParametersChanged = false;
    public boolean requestHeadersChanged = false;
    public boolean responseHeadersChanged = false;

    public ApiClass input = null;
    public ApiClass output = null;

    public boolean containsChanges() {
        return !newRoles.isEmpty() ||
               !removedRoles.isEmpty() ||
               !newPrerequists.isEmpty() ||
               !removedPrerequists.isEmpty() ||
                requestHeadersChanged ||
                responseHeadersChanged ||
               pathParametersChanged ||
               queryParametersChanged ||
               requestPayloadChanged ||
               responsePayloadChanged;
    }

}
