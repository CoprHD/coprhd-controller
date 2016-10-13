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

public class WorkflowDefinition {

    @SerializedName("WorkflowName")
    private String workflowName;

    @SerializedName("Description")
    private String description;

    @SerializedName("Steps")
    private ArrayList<Step> steps;

    public String getWorkflowName() {
        return workflowName;
    }

    public String getDescription() {
        return description;
    }

    public ArrayList<Step> getSteps() {
        return steps;
    }

    public  static class Input {

        @SerializedName("Type")
        private String type;
       
	@SerializedName("FriendlyName")
        private String friendlyName;
        
	@SerializedName("Required")
        private String required;
        
	@SerializedName("Default")
        private String Default;
        
	@SerializedName("AssetValue")
        private String assetValue;
        
	@SerializedName("OtherStepValue")
        private String otherStepValue;
        
	@SerializedName("Group")
        private String group;
        
	@SerializedName("Lockdown")
        private String lockdown;

        public String getType() {
            return type;
        }

        public String getFriendlyName() {
            return friendlyName;
        }

        public String getRequired() {
            return required;
        }

        public String getDefault() {
            return Default;
        }

        public String getAssetValue() {
            return assetValue;
        }

        public String getOtherStepValue() {
            return otherStepValue;
        }

        public String getGroup() {
            return group;
        }

        public String getLockdown() {
            return lockdown;
        }
    }

    public  static class StepAttribute {

 	public StepAttribute()
        {
            waitForTask = true;
            timeout = OrchestrationServiceConstants.TIMEOUT;
        }

        @SerializedName("WaitForTask")
        private boolean waitForTask;

        @SerializedName("Timeout")
        private long timeout;

        public boolean getWaitForTask() {
            return waitForTask;
        }

        public long getTimeout() {
            return timeout;
        }
    }

    public  static class Step {

        @SerializedName("StepId")
        private String stepId;

        @SerializedName("OpName")
        private String opName;

        @SerializedName("Description")
        private String description;

        @SerializedName("Type")
        private String type;

        @SerializedName("Input")
        private Map<String, Input> input;

        @SerializedName("Output")
        private Map<String, String> output;

        @SerializedName("StepAttribute")
        private StepAttribute stepAttribute;

        @SerializedName("SuccessCritera")
        private String successCritera;

        @SerializedName("Next")
        private NextStep next;

        public String getStepId() {
            return stepId;
        }

        public String getOpName() {
            return opName;
        }

        public String getDescription() {
            return description;
        }

        public String getType() {
            return type;
        }

        public Map<String, Input> getInput() {
            return input;
        }

        public Map<String, String> getOutput() {
            return output;
        }

        public StepAttribute getStepAttribute() {
            return stepAttribute;
        }

        public String getSuccessCritera() {
            return successCritera;
        }

        public NextStep getNext() {
            return next;
        }
    }

    public  static class NextStep {

        @SerializedName("Default")
        private String defaultstep;

        @SerializedName("Condition")
        private String condition;

        public String getDefault() {
            return defaultstep;
        }

        public String getCondition() {
            return condition;
        }
    }
}
