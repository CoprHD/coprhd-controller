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
package com.emc.storageos.driver.dellsc.scapi;

/**
 * Storage Center API exceptions.
 */
public class StorageCenterAPIException extends Exception {

    private static final long serialVersionUID = 4033692373650241886L;
    private int statusCode;

    /**
     * Initializes a new StorageCenterAPIException.
     * 
     * @param message The exception message.
     */
    public StorageCenterAPIException(String message) {
        super(message);
    }

    /**
     * Initializes a new StorageCenterAPIException.
     * 
     * @param message The exception message.
     * @param e The root exception.
     */
    public StorageCenterAPIException(String message, Throwable e) {
        super(message, e);
    }

    /**
     * Initializes a new StorageCenterAPIException.
     * 
     * @param message The exception message.
     * @param statusCode The status code.
     */
    public StorageCenterAPIException(String message, int statusCode) {
        this(message);
        this.statusCode = statusCode;
    }

    /**
     * Initializes a new StorageCenterAPIException.
     * 
     * @param message The exception message.
     * @param statusCode The status code.
     * @param e The root exception.
     */
    public StorageCenterAPIException(String message, int statusCode, Throwable e) {
        this(message, e);
        this.statusCode = statusCode;
    }

    /**
     * Get the status code.
     * 
     * @return The status code.
     */
    public int getStatusCode() {
        return statusCode;
    }
}