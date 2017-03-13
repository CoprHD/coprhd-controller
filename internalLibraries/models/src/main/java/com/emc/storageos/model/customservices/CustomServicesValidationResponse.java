/*
 * Copyright 2016 Dell Inc. or its subsidiaries.
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

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * JAXB model for Custom Services workflow Definition
 */

@XmlRootElement(name = "validation_response")
public class CustomServicesValidationResponse {

    private String name;
    private String description;
    private String status;
    private Error error;



    @XmlElement(name = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "error")
    public Error getError() {
        return error;
    }

    public void setError(Error error) {
        this.error = error;
    }



    public static class Error {
        private String errorMessage;
        private List<ErrorStep> errorSteps;



        @XmlElement(name = "errorMessage")
        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorDescription(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @XmlElementWrapper(name = "errorSteps")
        @XmlElement(name = "errorStep")
        public List<ErrorStep> getErrorSteps() {
            return errorSteps;
        }

        public void setErrorSteps(List<ErrorStep> errorSteps) {
            this.errorSteps = errorSteps;
        }

    }

    public static class ErrorStep {

        private String id;
        private String errorDescription;
        private Map<String, InputGroup> inputGroups;

        @XmlElement(name = "id")
        public String getId() {
            return id;
        }

        public void setId(String stepId) {
            this.id = stepId;
        }

        @XmlElement(name = "errorDescription")
        public String getErrorDescription() {
            return errorDescription;
        }

        public void setErrorDescription(String errorDescription) {
            this.errorDescription = errorDescription;
        }

        @XmlElementWrapper(name = "inputGroups")
        public Map<String, InputGroup> getInputGroups() {
            return inputGroups;
        }

        public void setInputGroups(Map<String, InputGroup> inputGroups) {
            this.inputGroups = inputGroups;
        }

    }

    public static class InputGroup {

        private List<Input> inputGroup;

        @XmlElement(name = "input")
        public List<Input> getInputGroup() {
            return inputGroup;
        }

        public void setInputGroup(List<Input> inputGroup) {
            this.inputGroup = inputGroup;
        }
    }

    public static class Input {

        private String name;
        private String type;
        private String inputError;

        @XmlElement(name = "name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "type")
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @XmlElement(name = "inputError")
        public String getInputError() {
            return inputError;
        }

        public void setInputError(String inputError) {
            this.inputError = inputError;
        }

    }

}
