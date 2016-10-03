/*
 * Copyright 2016
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
package com.emc.sa.service.vipr.oe.gson;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Map;

/**
 * GSON Structure for Orchestration workflow Definition
 */

public class OEJson
{
    @SerializedName("WorkflowName")
    String workflowName;
    @SerializedName("Description")
    String description;
    @SerializedName("Steps")
    ArrayList<Step> steps;

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ArrayList<Step> getSteps() {
        return steps;
    }

    public void setSteps(ArrayList<Step> steps) {
        this.steps = steps;
    }

    public  class Input
    {
        @SerializedName("Type")
        String type;
        @SerializedName("FriendlyName")
        String friendlyName;
        @SerializedName("Required")
        String required;
        @SerializedName("Default")
        String Default;
        @SerializedName("AssetValue")
        String assetValue;
        @SerializedName("OtherStepValue")
        String otherStepValue;
        @SerializedName("Group")
        String group;
        @SerializedName("Lockdown")
        String lockdown;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        public String getRequired() {
            return required;
        }

        public void setRequired(String required) {
            this.required = required;
        }

        public String getDefault() {
            return Default;
        }

        public void setDefault(String Default) {
            this.Default = Default;
        }

        public String getAssetValue() {
            return assetValue;
        }

        public void setAssetValue(String assetValue) {
            this.assetValue = assetValue;
        }

        public String getOtherStepValue() {
            return otherStepValue;
        }

        public void setOtherStepValue(String otherStepValue) {
            this.otherStepValue = otherStepValue;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getLockdown() {
            return lockdown;
        }

        public void setLockdown(String lockdown) {
            this.lockdown = lockdown;
        }
    }

    public  class StepAttribute
    {
        @SerializedName("WaitForTask")
        boolean waitForTask;
        @SerializedName("Timeout")
        long timeout;

        public boolean getWaitForTask() {
            return waitForTask;
        }

        public void setWaitForTask(boolean waitForTask) {
            this.waitForTask = waitForTask;
        }

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }
    }

    public  class Step
    {
        @SerializedName("StepId")
        String stepId;
        @SerializedName("OpName")
        String opName;
        @SerializedName("Description")
        String description;
        @SerializedName("Type")
        String type;
        @SerializedName("Input")
        Map<String, Input> input;
        @SerializedName("Output")
        Map<String, String> output;
        @SerializedName("StepAttribute")
        StepAttribute stepAttribute;
        @SerializedName("SuccessCritera")
        String successCritera;
        @SerializedName("Next")
        Next next;

        public String getStepId() {
            return stepId;
        }

        public void setStepId(String stepId) {
            this.stepId = stepId;
        }

        public String getOpName() {
            return opName;
        }

        public void setOpName(String OpName) {
            this.opName = OpName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Map<String, Input> getInput() {
            return input;
        }

        public void setInput(Map<String, Input> input) {
            this.input = input;
        }

        public Map<String, String> getOutput() {
            return output;
        }

        public void setOutput(Map<String, String> output) {
            this.output = output;
        }

        public StepAttribute getStepAttribute() {
            return stepAttribute;
        }

        public void setStepAttribute(StepAttribute StepAttribute) {
            this.stepAttribute = stepAttribute;
        }

        public String getSuccessCritera() {
            return successCritera;
        }

        public void setSuccessCritera(String successCritera) {
            successCritera = successCritera;
        }

        public Next getNext() {
            return next;
        }

        public void setNext(Next next) {
            this.next = next;
        }
    }

    public  class Next
    {
        @SerializedName("Default")
        String defaultstep;
        @SerializedName("Condition")
        String condition;

        public String getDefault() {
            return defaultstep;
        }

        public void setDefault(String defaultstep) {
            this.defaultstep = defaultstep;
        }

        public String getCondition() {
            return condition;
        }

        public void setCondition(String condition) {
            this.condition = condition;
        }
    }
}