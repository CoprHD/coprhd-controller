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
package com.emc.storageos.model.orchestration;

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * JAXB model for Orchestration workflow Definition
 */

@XmlRootElement(name = "workflow_document")
public class OrchestrationWorkflowDocument {

    public static final long DEFAULT_STEP_TIMEOUT = 3600; //min
    
    private String name;
    private String description;
    private String status;
    private List<Step> steps;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
    
    @XmlElement(name = "description" )
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @XmlElement(name = "status" )
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @XmlElementWrapper(name = "steps")
    @XmlElement(name="step")
    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }


    public static class Input {

        private String type;
        private String friendlyName;
        private String defaultValue;
        private String value;
        private String group;
        private boolean required = true;
        private boolean locked = false;
        
        @XmlElement(name = "type")
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        @XmlElement(name = "friendly_name")
        public String getFriendlyName() {
            return friendlyName;
        }
        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }
        
        @XmlElement(name = "default_value")
        public String getDefaultValue() {
            return defaultValue;
        }
        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
        
        @XmlElement(name = "value")
        public String getValue() {
            return value;
        }
        public void setValue(String assetValue) {
            this.value = assetValue;
        }
        
        @XmlElement(name = "group")
        public String getGroup() {
            return group;
        }
        public void setGroup(String group) {
            this.group = group;
        }
        
        @XmlElement(name = "required")
        public boolean getRequired() {
            return required;
        }
        public void setRequired(boolean required) {
            this.required = required;
        }
        
        @XmlElement(name = "locked")
        public boolean getLocked() {
            return locked;
        }
        public void setLocked(boolean locked) {
            this.locked = locked;
        }
    }


    public static class StepAttribute {

        private boolean waitForTask = true;
        private long timeout = DEFAULT_STEP_TIMEOUT;
        
        @XmlElement(name = "wait_for_task")
        public boolean getWaitForTask() {
            return waitForTask;
        }
        public void setWaitForTask(Boolean waitForTask) {
            this.waitForTask = waitForTask;
        }
        
        @XmlElement(name = "timeout")
        public long getTimeout() {
            return timeout;
        }
        public void setTimeout(Long timeout) {
            this.timeout = timeout;
        }

    }

    public static class Step {

        private String id;
        private String operation;
        private String description;
        private String type;
        private Map<String, Input> input;
        private Map<String, String> output;
        private StepAttribute attributes;
        private String successCriteria;
        private NextStep next;
        
        @XmlElement(name = "id")
        public String getId() {
            return id;
        }
        public void setId(String stepId) {
            this.id = stepId;
        }
        
        @XmlElement(name = "operation")
        public String getOperation() {
            return operation;
        }
        public void setOperation(String opName) {
            this.operation = opName;
        }
        
        @XmlElement(name = "description")
        public String getDescription() {
            return description;
        }
        public void setDescription(String description) {
            this.description = description;
        }
        
        @XmlElement(name = "type")
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }

        @XmlElement(name = "input")
        public Map<String, Input> getInput() {
            return input;
        }
        public void setInput(Map<String, Input> input) {
            this.input = input;
        }
        
        @XmlElement(name = "output")
        public Map<String, String> getOutput() {
            return output;
        }
        public void setOutput(Map<String, String> output) {
            this.output = output;
        }
        
        @XmlElement(name = "attributes")
        public StepAttribute getAttributes() {
            return attributes;
        }
        public void setAttributes(StepAttribute attributes) {
            this.attributes = attributes;
        }
        
        @XmlElement(name = "success_criteria")
        public String getSuccessCriteria() {
            return successCriteria;
        }
        public void setSuccessCriteria(String successCritera) {
            this.successCriteria = successCritera;
        }
        @XmlElement(name = "next")
        public NextStep getNext() {
            return next;
        }
        public void setNext(NextStep next) {
            this.next = next;
        }
    }

    public static class NextStep {
        private String defaultStep;
        private String failedStep;
        
        @XmlElement(name = "default")
        public String getDefault() {
            return defaultStep;
        }
        public void setDefault(String defaultstep) {
            this.defaultStep = defaultstep;
        }
        
        @XmlElement(name = "failed")
        public String getFailed() {
            return failedStep;
        }
        public void setFailed(String failedStep) {
            this.failedStep = failedStep;
        }
    }
}
