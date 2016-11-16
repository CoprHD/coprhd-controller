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
        private String assetValue;
        private String otherStepValue;
        private String group;
        private boolean required = true;
        private boolean lockdown = false;
        
        @XmlElement(name = "type")
        public String getType() {
            return type;
        }
        public void setType(String type) {
            this.type = type;
        }
        @XmlElement(name = "friendlyName")
        public String getFriendlyName() {
            return friendlyName;
        }
        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }
        
        @XmlElement(name = "defaultValue")
        public String getDefaultValue() {
            return defaultValue;
        }
        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }
        
        @XmlElement(name = "assetValue")
        public String getAssetValue() {
            return assetValue;
        }
        public void setAssetValue(String assetValue) {
            this.assetValue = assetValue;
        }
        
        @XmlElement(name = "otherStepValue")
        public String getOtherStepValue() {
            return otherStepValue;
        }
        public void setOtherStepValue(String otherStepValue) {
            this.otherStepValue = otherStepValue;
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
        
        @XmlElement(name = "lockdown")
        public boolean getLockdown() {
            return lockdown;
        }
        public void setLockdown(boolean lockdown) {
            this.lockdown = lockdown;
        }
    }


    public static class StepAttribute {

        private boolean waitForTask = true;
        private long timeout = DEFAULT_STEP_TIMEOUT;
        
        @XmlElement(name = "waitForTask")
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

        private String stepId;
        private String opName;
        private String description;
        private String type;
        private Map<String, Input> input;
        private Map<String, String> output;
        private StepAttribute stepAttribute;
        private String successCriteria;
        private NextStep next;
        
        @XmlElement(name = "stepId")
        public String getStepId() {
            return stepId;
        }
        public void setStepId(String stepId) {
            this.stepId = stepId;
        }
        
        @XmlElement(name = "opName")
        public String getOpName() {
            return opName;
        }
        public void setOpName(String opName) {
            this.opName = opName;
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
        
        @XmlElement(name = "stepAttribute")
        public StepAttribute getStepAttribute() {
            return stepAttribute;
        }
        public void setStepAttribute(StepAttribute stepAttribute) {
            this.stepAttribute = stepAttribute;
        }
        
        @XmlElement(name = "successCriteria")
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
        
        @XmlElement(name = "failedStep")
        public String getFailedStep() {
            return failedStep;
        }
        public void setFailedStep(String failedStep) {
            this.failedStep = failedStep;
        }
    }
}
