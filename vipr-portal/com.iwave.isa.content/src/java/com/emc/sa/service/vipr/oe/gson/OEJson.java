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

import java.util.ArrayList;
import java.util.Map;

/**
 * GSON Structure for Orchestration workflow Definition
 */

public class OEJson
{
    String WorkflowName;
    String Description;
    ArrayList<Step> Steps;

    public String getWorkflowName() {
        return WorkflowName;
    }

    public void setWorkflowName(String workflowName) {
        workflowName = workflowName;
    }

    public String getDescription() {
        return Description;
    }

    public void setDescription(String description) {
        this.Description = description;
    }

    public ArrayList<Step> getSteps() {
        return Steps;
    }

    public void setSteps(ArrayList<Step> steps) {
        this.Steps = steps;
    }

    public  class Input
    {
        String Type;
        String FriendlyName;
        String Required;
        String Default;
        String AssetValue;
        String OtherStepValue;
        String Group;
        String Lockdown;

        public String getType() {
            return Type;
        }

        public void setType(String Type) {
            this.Type = Type;
        }

        public String getFriendlyName() {
            return FriendlyName;
        }

        public void setFriendlyName(String FriendlyName) {
            this.FriendlyName = FriendlyName;
        }

        public String getRequired() {
            return Required;
        }

        public void setRequired(String Required) {
            this.Required = Required;
        }

        public String getDefault() {
            return Default;
        }

        public void setDefault(String Default) {
            this.Default = Default;
        }

        public String getAssetValue() {
            return AssetValue;
        }

        public void setAssetValue(String AssetValue) {
            this.AssetValue = AssetValue;
        }

        public String getOtherStepValue() {
            return OtherStepValue;
        }

        public void setOtherStepValue(String otherStepValue) {
            OtherStepValue = otherStepValue;
        }

        public String getGroup() {
            return Group;
        }

        public void setGroup(String Group) {
            this.Group = Group;
        }

        public String getLockdown() {
            return Lockdown;
        }

        public void setLockdown(String lockdown) {
            this.Lockdown = lockdown;
        }
    }

    public  class StepAttribute
    {
        Boolean WaitForTask;
        int Timeout;

        public Boolean getWaitForTask() {
            return WaitForTask;
        }

        public void setWaitForTask(Boolean WaitForTask) {
            this.WaitForTask = WaitForTask;
        }

        public int getTimeout() {
            return Timeout;
        }

        public void setTimeout(int Timeout) {
            this.Timeout = Timeout;
        }
    }

    public  class Step
    {
        String StepId;
        String OpName;
        String Description;
        String Type;
        Map<String, Input> Input;
        Map<String, String> Output;
        StepAttribute StepAttribute;
        String SuccessCritera;
        Next Next;

        public String getStepId() {
            return StepId;
        }

        public void setStepId(String StepId) {
            this.StepId = StepId;
        }

        public String getOpName() {
            return OpName;
        }

        public void setOpName(String OpName) {
            this.OpName = OpName;
        }

        public String getDescription() {
            return Description;
        }

        public void setDescription(String Description) {
            Description = Description;
        }

        public String getType() {
            return Type;
        }

        public void setType(String Type) {
            Type = Type;
        }

        public Map<String, Input> getInput() {
            return Input;
        }

        public void setInput(Map<String, Input> input) {
            this.Input = input;
        }

        public Map<String, String> getOutput() {
            return Output;
        }

        public void setOutput(Map<String, String> Output) {
            this.Output = Output;
        }

        public StepAttribute getStepAttribute() {
            return StepAttribute;
        }

        public void setStepAttribute(StepAttribute StepAttribute) {
            this.StepAttribute = StepAttribute;
        }

        public String getSuccessCritera() {
            return SuccessCritera;
        }

        public void setSuccessCritera(String successCritera) {
            SuccessCritera = successCritera;
        }

        public Next getNext() {
            return Next;
        }

        public void setNext(Next next) {
            this.Next = next;
        }
    }

    public  class Next
    {
        String Default;
        String Condition;

        public String getDefault() {
            return Default;
        }

        public void setDefault(String Default) {
            this.Default = Default;
        }

        public String getCondition() {
            return Condition;
        }

        public void setCondition(String condition) {
            this.Condition = condition;
        }
    }
}