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
    private String workflowName;
    @SerializedName("Description")
    private String description;
    @SerializedName("Steps")
    private ArrayList<Step> steps;

    private String getWorkflowName() {
        return workflowName;
    }

    private String getDescription() {
        return description;
    }

    private ArrayList<Step> getSteps() {
        return steps;
    }

    public  static class Input
    {
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

        private String getType() {
            return type;
        }

        private String getFriendlyName() {
            return friendlyName;
        }

        private String getRequired() {
            return required;
        }

        private String getDefault() {
            return Default;
        }

        private String getAssetValue() {
            return assetValue;
        }

        private String getOtherStepValue() {
            return otherStepValue;
        }

        private String getGroup() {
            return group;
        }

        private String getLockdown() {
            return lockdown;
        }

    public  static class StepAttribute
    {
 	public StepAttribute()
        {
            waitForTask = true;
            timeout = OrchestrationServiceConstants.TIMEOUT;
        }
        @SerializedName("WaitForTask")
        private boolean waitForTask;
        @SerializedName("Timeout")
        private long timeout;

        private boolean getWaitForTask() {
            return waitForTask;
        }

        private long getTimeout() {
            return timeout;
        }

    public  static class Step
    {
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
        private Next next;

        private String getStepId() {
            return stepId;
        }

        private String getOpName() {
            return opName;
        }

        private String getDescription() {
            return description;
        }

        private String getType() {
            return type;
        }

        private Map<String, Input> getInput() {
            return input;
        }

        private Map<String, String> getOutput() {
            return output;
        }

        private StepAttribute getStepAttribute() {
            return stepAttribute;
        }

        private String getSuccessCritera() {
            return successCritera;
        }

        private Next getNext() {
            return next;
        }
    }

    public  static class Next
    {
        @SerializedName("Default")
        private String defaultstep;
        @SerializedName("Condition")
        private String condition;

        private String getDefault() {
            return defaultstep;
        }

        private String getCondition() {
            return condition;
        }
    }
}
