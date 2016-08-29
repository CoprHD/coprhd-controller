/*
 * Copyright 2016 Dell Inc.
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
package com.emc.storageos.driver.dellsc.scapi.rest;

/**
 * Response information from a REST call.
 */
public class RestResult {

    private int responseCode = 0;
    private String errorMsg = "";
    private String result = "";
    private String url = "";

    /**
     * Instantiates a new Rest result.
     *
     * @param responseCode the response code
     * @param errorMsg the error message
     * @param result the result
     */
    public RestResult(int responseCode, String errorMsg, String result) {
        this.responseCode = responseCode;
        this.errorMsg = errorMsg;
        this.result = result;
    }

    /**
     * Instantiates a new Rest result.
     *
     * @param url the URL
     * @param responseCode the response code
     * @param errorMsg the error message
     * @param result the result
     */
    public RestResult(String url, int responseCode, String errorMsg, String result) {
        this(responseCode, errorMsg, result);
        this.url = url;
    }

    /**
     * Gets URL.
     *
     * @return the URL
     */
    public String getUrl() {
        return this.url;
    }

    /**
     * Gets response code.
     *
     * @return the response code
     */
    public int getResponseCode() {
        return this.responseCode;
    }

    /**
     * Gets error message.
     *
     * @return the error message
     */
    public String getErrorMsg() {
        return this.errorMsg;
    }

    /**
     * Gets result.
     *
     * @return the result
     */
    public String getResult() {
        return this.result;
    }

    /**
     * Set the error text.
     *
     * @param text The text.
     */
    public void setErrorMsg(String text) {
        this.errorMsg = text;
    }

    @Override
    public String toString() {
        return String.format("[%d] %s %s %s", this.responseCode, this.errorMsg, this.result, this.url);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return toString().equals(String.format("%s", o));
    }
}
