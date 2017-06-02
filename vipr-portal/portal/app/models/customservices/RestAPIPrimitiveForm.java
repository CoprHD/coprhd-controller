/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package models.customservices;

import play.data.validation.Required;

public class RestAPIPrimitiveForm {
    private String id; // this is empty for CREATE
    private String wfDirID; // this is empty for EDIT

    // Name and Description step
    @Required
    private String name;
    @Required
    private String description;

    // Details
    @Required
    private String method; // get, post,..
    private String requestURL;
    private String authType;
    private String headers;
    private String rawBody;
    private String queryParams;

    // Input and Outputs
    private String inputs; // comma separated list of inputs
    private String outputs; // comma separated list of ouputs

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWfDirID() {
        return wfDirID;
    }

    public void setWfDirID(String wfDirID) {
        this.wfDirID = wfDirID;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public void setRequestURL(String requestURL) {
        this.requestURL = requestURL;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public String getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(String queryParams) {
        this.queryParams = queryParams;
    }

    public String getInputs() {
        return inputs;
    }

    public void setInputs(String inputs) {
        this.inputs = inputs;
    }

    public String getOutputs() {
        return outputs;
    }

    public void setOutputs(String outputs) {
        this.outputs = outputs;
    }
}
