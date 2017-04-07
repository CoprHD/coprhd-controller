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
package com.emc.storageos.model.customservices;

import java.net.URI;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "primitive_resource")
public class CustomServicesPrimitiveResourceResponse {
    private URI id;
    private String status;
    private Error error;

    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @XmlElement(name = "id")
    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    @XmlElement(name = "error")
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }

    public static class Error {
        private int code;
        private String description;
        private String details;

        @XmlElement(name = "code")
        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        @XmlElement(name = "description")
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @XmlElement(name = "details")
        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }

    }
}
