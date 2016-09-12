package com.emc.sa.service.vipr.oe.gson;

import java.util.Map;
import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.*;

public class OEJson 
{
            String WorkflowName;
            Map<String, String> inputParams;
            ArrayList<Step> Steps;

            public String getWorkflowName() {
                return WorkflowName;
            }

            public void setWorkflowName(String workflowName) {
                WorkflowName = workflowName;
            }

            public Map<String, String> getInputParams() {
                return inputParams;
            }

            public void setInputParams(Map<String, String> inputParams) {
                this.inputParams = inputParams;
            }

            public ArrayList<Step> getSteps() {
                return Steps;
            }

            public void setSteps(ArrayList<Step> steps) {
                this.Steps = steps;
            }

        public static class Step {
            String StepId;
            String OpName;
            String Description;
            String Type;
            Map<String, String> inputParams;
            Map<String, String> inputFromOtherSteps;
            ArrayList<String> output;

            String SuccessCritera;
            Next Next;

            public String getStepId() {
                return StepId;
            }

            public void setStepId(String stepId) {
                StepId = stepId;
            }

            public String getOpName() {
                return OpName;
            }

            public void setOpName(String opName) {
                OpName = opName;
            }

            public String getDescription() {
                return Description;
            }

            public void setDescription(String description) {
                Description = description;
            }

            public String getType() {
                return Type;
            }

            public void setType(String type) {
                Type = type;
            }

            public Map<String, String> getInputParams() {
                return inputParams;
            }

            public void setInputParams(Map<String, String> inputparams) {
                this.inputParams = inputparams;
            }

            public Map<String, String> getInputFromOtherSteps() {
                return inputFromOtherSteps;
            }

            public void setInputFromOtherSteps(Map<String, String> inputFromOtherSteps) {
                this.inputFromOtherSteps = inputFromOtherSteps;
            }

            public ArrayList<String> getOutput() {
                return output;
            }

            public void setOutput(ArrayList<String> output) {
                this.output = output;
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

        public static class Next {

            String Default;
            String condition;

            public String getDefault() {
                return Default;
            }

            public void setDefault(String aDefault) {
                Default = aDefault;
            }

            public String getCondition() {
                return condition;
            }

            public void setCondition(String condition) {
                this.condition = condition;
            }
        }
}
