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
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * JAXB model for Custom Services workflow Validation Response
 */

@XmlRootElement(name = "custom_services_workflow_validation_response")
public class CustomServicesValidationResponse {

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
        private String errorMessage;
        // Error message for workflow attributes
        private Map<String, String> errorAttributes;
        private Map<String, ErrorStep> errorSteps;

        @XmlElement(name = "error_message")
        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @XmlElementWrapper(name = "error_steps")
        @XmlElement(name = "error_step")
        public Map<String, ErrorStep> getErrorSteps() {
            return errorSteps;
        }

        public void setErrorSteps(Map<String, ErrorStep> errorSteps) {
            this.errorSteps = errorSteps;
        }

        @XmlElementWrapper(name = "error_attributes")
        @XmlElement(name = "error_attribute")
        public Map<String, String> getErrorAttributes() {
            return errorAttributes;
        }

        public void setErrorAttributes(Map<String, String> errorAttributes) {
            this.errorAttributes = errorAttributes;
        }
    }

    public static class ErrorStep {
        private String stepName;
        private List<String> errorMessages;
        private Map<String, ErrorInputGroup> errorInputGroups;
        // Error message for workflow attributes
        private Map<String, ErrorInput> errorStepAttributes;

        @XmlElement(name = "step_name")
        public String getStepName() {
            return stepName;
        }

        public void setStepName(String stepName) {
            this.stepName = stepName;
        }

        @XmlElementWrapper(name = "error_messages")
        @XmlElement(name = "error_message")
        public List<String> getErrorMessages() {
            return errorMessages;
        }

        public void setErrorMessages(List<String> errorMessages) {
            this.errorMessages = errorMessages;
        }

        @XmlElementWrapper(name = "error_input_groups")
        public Map<String, ErrorInputGroup> getErrorInputGroups() {
            return errorInputGroups;
        }

        public void setInputGroups(Map<String, ErrorInputGroup> errorInputGroups) {
            this.errorInputGroups = errorInputGroups;
        }

        @XmlElementWrapper(name = "error_step_attributes")
        public Map<String, ErrorInput> getErrorStepAttributes() {
            return errorStepAttributes;
        }

        public void setErrorStepAttributes(Map<String, ErrorInput> errorStepAttributes) {
            this.errorStepAttributes = errorStepAttributes;
        }

    }

    public static class ErrorInputGroup {

        private Map<String, ErrorInput> errorInputs;

        @XmlElementWrapper(name = "error_inputs")
        public Map<String, ErrorInput> getErrorInputs() {
            return errorInputs;
        }

        public void setErrorInputs(Map<String, ErrorInput> errorInputs) {
            this.errorInputs = errorInputs;
        }
    }

    public static class ErrorStepAttributes {

        private Map<String, ErrorInput> errorAttributes;

        @XmlElementWrapper(name = "error_attributes")
        public Map<String, ErrorInput> getErrorAttributes() {
            return errorAttributes;
        }

        public void setErrorAttributes(Map<String, ErrorInput> errorAttributes) {
            this.errorAttributes = errorAttributes;
        }
    }

    public static class ErrorInput {

        private List<String> errorMessages;

        @XmlElement(name = "error_messages")
        public List<String> getErrorMessages() {
            return errorMessages;
        }

        public void setErrorMessages(List<String> errorMessages) {
            this.errorMessages = errorMessages;
        }

    }

}
