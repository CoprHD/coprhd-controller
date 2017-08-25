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

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * JAXB model for Custom Services workflow Definition
 */

@XmlRootElement(name = "workflow_document")
public class CustomServicesWorkflowDocument {

    public static final long DEFAULT_STEP_TIMEOUT = 600000; // Setting default to 10 mins

    private String name;
    private String description;
    private List<Step> steps;

    /**
     * Name of the workflow
     *
     */
    @XmlElement(name = "name", nillable = true)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Description of the workflow
     *
     */
    @XmlElement(name = "description", nillable = true)
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

<<<<<<< HEAD
=======
    /**
     * Workflow attributes - as key value pair. valid keys are -
     * "timeout" - Workflow timeout in milliseconds
     * "loop_workflow" - To run workflow as loop - valid values 'true'/ 'false'
     *
     */
    @XmlElement(name = "attributes")
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(final Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * Steps in the workflow
     *
     */

>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
    @XmlElementWrapper(name = "steps")
    @XmlElement(name = "step")
    public List<Step> getSteps() {
        return steps;
    }

    public void setSteps(List<Step> steps) {
        this.steps = steps;
    }

    public static class InputGroup {

        private List<Input> inputGroup;

        /**
         * List of input in each step
         *
         */

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
        // type of CustomServicesConstants.InputType
        private String type;
        private String step;
        private String friendlyName;
        private String defaultValue;
        private String value;
        private String group;
        private String description;
        // type of the value e.g (STRING, INTEGER ...etc)
        private String inputFieldType;
        private String tableName;
        private boolean required = true;
        private boolean locked = false;
        // Use this to set "key,value" pairs for type "InputFromUserMulti"
        private Map<String, String> options;

        /**
         * Input name
         *
         */
        @XmlElement(name = "name", nillable = true)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        /**
         * Input type valid values - "InputFromUser", "InputFromUserMulti", "AssetOptionSingle", "AssetOptionMulti", "FromOtherStepInput", "FromOtherStepOutput", "Invalid", "Disabled"
         *
         */
        @XmlElement(name = "type", nillable = true)
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @XmlElement(name = "step", nillable = true)
        public String getStep() {
            return step;
        }

        public void setStep(String step) {
            this.step = step;
        }

        /**
         * Friendly name for the input
         *
         */
        @XmlElement(name = "friendly_name", nillable = true)
        public String getFriendlyName() {
            return friendlyName;
        }

        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        /**
         * DefaultValue for the input of type - "InputFromUser", "InputFromUserMulti",
         *
         */
        @XmlElement(name = "default_value", nillable = true)
        public String getDefaultValue() {
            return defaultValue;
        }

        public void setDefaultValue(String defaultValue) {
            this.defaultValue = defaultValue;
        }

        /**
         * DefaultValue for the input of type - "InputFromUser", "InputFromUserMulti",
         *
         */
        @XmlElement(name = "value", nillable = true)
        public String getValue() {
            return value;
        }

        public void setValue(String assetValue) {
            this.value = assetValue;
        }

        @XmlElement(name = "group", nillable = true)
        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        /**
         * Signifies if the input is mandatory / optional
         *
         */
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

        /**
         * Input description
         *
         */
        @XmlElement(name = "description", nillable = true)
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Input field type - for the input of type - "InputFromUser" - valid values - NUMBER, BOOLEAN, TEXT, PASSWORD
         *
         */
        @XmlElement(name = "input_field_type", nillable = true)
        public String getInputFieldType() {
            return inputFieldType;
        }

        public void setInputFieldType(String inputfieldtype) {
            this.inputFieldType = inputfieldtype;
        }

        /**
         * Table appears in the order page if a table name is given for a input in the workflow. If a table name is specified for the input it signifies that the input will be a column in the given table (name)
         *
         */
        @XmlElement(name = "table_name", nillable = true)
        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tablename) {
            this.tableName = tablename;
        }

        @XmlElementWrapper(name = "options")
        public Map<String, String> getOptions() {
            return options;
        }

        public void setOptions(Map<String, String> options) {
            this.options = options;
        }
    }

    public static class Output {

        private String name;
        private String type;
        private String table;

        @XmlElement(name = "name", nillable = true)
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement(name = "type", nillable = true)
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        @XmlElement(name = "table", nillable = true)
        public String getTable() {
            return table;
        }

        public void setTable(String table) {
            this.table = table;
        }
    }

    public static class StepAttribute {

        private boolean waitForTask = true;
        private long timeout = DEFAULT_STEP_TIMEOUT;

        /**
         * wait for task signifies that the step will wait until the task is complete
         *
         */
        @XmlElement(name = "wait_for_task")
        public boolean getWaitForTask() {
            return waitForTask;
        }

        public void setWaitForTask(boolean waitForTask) {
            this.waitForTask = waitForTask;
        }

        /**
         * Step timeout in milliseconds
         *
         */
        @XmlElement(name = "timeout")
        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

<<<<<<< HEAD
=======
        /**
         * Signifies that the step will be in polling state. Exit conditions: until the success or failure condition is met or the timeout is reached. checking
         * wait for task signifies that the step will wait until the task is complete
         *
         */
        @XmlElement(name = "polling")
        public boolean getPolling() {
            return polling;
        }

        public void setPolling(boolean polling) {
            this.polling = polling;
        }

        /**
         * Polling interval in milliseconds
         *
         */
        @XmlElement(name = "interval")
        public long getInterval() {
            return interval;
        }

        public void setInterval(long interval) {
            this.interval = interval;
        }

        /**
         * List of Success Condition for exiting polling in the step. Logical OR operation is performed between the success conditions
         *
         */
        @XmlElement(name = "success_condition")
        public List<Condition> getSuccessCondition() {
            return successCondition;
        }

        public void setSuccessCondition(List<Condition> successCondition) {
            this.successCondition = successCondition;
        }

        /**
         * List of Failure Condition for exiting polling in the step. Logical OR operation is performed between the failure conditions
         *
         */
        @XmlElement(name = "failure_condition")
        public List<Condition> getFailureCondition() {
            return failureCondition;
        }

        public void setFailureCondition(List<Condition> failureCondition) {
            this.failureCondition = failureCondition;
        }

    }

    public static class Condition {
        private String outputName;
        private String checkValue;

        /**
         * Output Name - from the list of outputs from the step. Used in Success/ Failure Condition evaluation for exiting polling in the step.
         *
         */
        @XmlElement(name = "output_name", nillable = true)
        public String getOutputName() {
            return outputName;
        }

        public void setOutputName(String outputName) {
            this.outputName = outputName;
        }

        /**
         * Check Value - Value to be validated against for the above outputName. Used in Success/ Failure Condition evaluation for exiting polling in the step.
         *
         */
        @XmlElement(name = "check_Value", nillable = true)
        public String getCheckValue() {
            return checkValue;
        }

        public void setCheckValue(String checkValue) {
            this.checkValue = checkValue;
        }
>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
    }

    public static class Step {

        private String id;
        private String friendlyName;
        private URI operation;
        private String description;
        private Integer positionX;
        private Integer positionY;
        private String type;
        private Map<String, InputGroup> inputGroups;
        private List<Output> output;
        private StepAttribute attributes;
        private String successCriteria;
        private NextStep next;

        /**
         * Unique Step id
         *
         */
        @XmlElement(name = "id", required = true)
        public String getId() {
            return id;
        }

        public void setId(String stepId) {
            this.id = stepId;
        }

<<<<<<< HEAD
        //ALl steps should have friendly_name
=======
        /**
         * Step Friendly_Name
         *
         */
        // ALl steps should have friendly_name
>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
        @XmlElement(name = "friendly_name", required = true)
        public String getFriendlyName() {
            return friendlyName;
        }

        public void setFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
        }

        /**
         * Step's Y Position in the UI builder
         *
         */
        @XmlElement(name = "position_y")
        public Integer getPositionY() {
            return positionY;
        }

        public void setPositionY(Integer positionY) {
            this.positionY = positionY;
        }

        /**
         * Step's X Position in the UI builder
         *
         */
        @XmlElement(name = "position_x")
        public Integer getPositionX() {
            return positionX;
        }

        public void setPositionX(Integer positionX) {
            this.positionX = positionX;
        }

        /**
         * Step's operation - URI for Shell Script, Local Ansible, Remote Ansible and Custom REST. For ViPR Operation the Custom Service generated classname
         *
         */
        @XmlElement(name = "operation")
        public URI getOperation() {
            return operation;
        }

        public void setOperation(URI operation) {
            this.operation = operation;
        }

        /**
         * Step description
         *
         */
        @XmlElement(name = "description", nillable = true)
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

<<<<<<< HEAD
        //Start and end does not have type
=======
        /**
         * Step's type - valid values - "vipr", "script", "ansible", "rest", "remote_ansible"
         *
         */
        // Start and end does not have type
>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
        @XmlElement(name = "type", nillable = true)
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        /**
         * Step input group
         *
         */
        @XmlElementWrapper(name = "inputGroups")
        public Map<String, InputGroup> getInputGroups() {
            return inputGroups;
        }

        public void setInputGroups(Map<String, InputGroup> inputGroups) {
            this.inputGroups = inputGroups;
        }

        /**
         * Step output
         *
         */
        @XmlElement(name = "output")
        public List<Output> getOutput() {
            return output;
        }

        public void setOutput(List<Output> output) {
            this.output = output;
        }

        /**
         * Step attributes
         *
         */
        @XmlElement(name = "attributes")
        public StepAttribute getAttributes() {
            return attributes;
        }

        public void setAttributes(StepAttribute attributes) {
            this.attributes = attributes;
        }

<<<<<<< HEAD
        @XmlElement(name = "success_criteria", nillable = true)
        public String getSuccessCriteria() {
            return successCriteria;
        }

        public void setSuccessCriteria(String successCriteria) {
            this.successCriteria = successCriteria;
        }

=======
        /**
         * path to next step
         *
         */
>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
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

<<<<<<< HEAD
        //End does not have next steps
=======
        // End does not have next steps
        /**
         * The next step in the success path
         *
         */
>>>>>>> ffb37ce... Merge branch 'master' into feature-COP-22537-VMAX-NDM-feature
        @XmlElement(name = "default", nillable = true)
        public String getDefaultStep() {
            return defaultStep;
        }

        public void setDefaultStep(String defaultStep) {
            this.defaultStep = defaultStep;
        }

        /**
         * The next step in the failure path
         *
         */
        @XmlElement(name = "failed", nillable = true)
        public String getFailedStep() {
            return failedStep;
        }

        public void setFailedStep(String failedStep) {
            this.failedStep = failedStep;
        }
    }
}
