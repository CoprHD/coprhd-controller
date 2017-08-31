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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.valid.Range;
import com.emc.vipr.model.catalog.ServiceFieldGroupRestRep;
import com.emc.vipr.model.catalog.ServiceFieldModalRestRep;
import com.emc.vipr.model.catalog.ServiceFieldRestRep;
import com.emc.vipr.model.catalog.ServiceFieldTableRestRep;
import com.emc.vipr.model.catalog.ServiceItemRestRep;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonSubTypes;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonTypeName;

/**
 * JAXB model for Custom Services workflow Definition
 */

@XmlRootElement(name = "workflow_document")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomServicesWorkflowDocument {

    public static final long DEFAULT_STEP_TIMEOUT = 600000; // Setting default to 10 mins

    private String name;
    private String description;
    private Map<String, String> attributes;
    private List<Step> steps;
    private List<BaseItem> items;

    @XmlElementWrapper(name = "items")
    @XmlElements({
            @XmlElement(name = "field", type = FieldType.class), //name is needed to define what is the POJO class that element (in the list) will be mapped to
            @XmlElement(name = "field_list", type = TableOrModalType.class),
            @XmlElement(name = "group", type = GroupType.class)
    })
    public List<BaseItem> getItems() {
        if (items == null) {
            items = new ArrayList<>();
        }
        return items;
    }

    public void setItems(List<BaseItem> items) {
        this.items = items;
    }

//
    @JsonIgnoreProperties(ignoreUnknown = true)
//    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "type")
//    @JsonSubTypes({
//            @JsonSubTypes.Type(value = FieldType.class, name = "FieldType"),
//            @JsonSubTypes.Type(value = TableOrModalType.class, name = "TableOrModalType"),
//            @JsonSubTypes.Type(value = GroupType.class, name = "GroupType") }
//    )

    public static class BaseItem{
        //This will be the name that is displayed in the order page. ie., table name/ field name etc.,
        private String name;
        private String csType; //the type of the item such as: field, table, group, modal

        @XmlElement(name = "cs_type")
        public String getCsType() {
            return csType;
        }

        public void setCsType(String csType) {
            this.csType = csType;
        }


        @XmlElement(name = "name")
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class FieldType extends BaseItem {

//        @XmlElement(name = "label")
//        public String getLabel() {
//            return label;
//        }
//
//        public void setLabel(String label) {
//            this.label = label;
//        }

//        private String label;
        /**In this class we can add input params (the inputs defined in Step.Input
         *

         Eg.,private String name;
            private String type;
            private String step;
            private String friendlyName;
            private String defaultValue;
            private String value;
            private String group;
            private String description;
            // type of the value e.g (STRING, INTEGER ...etc)
            private String inputFieldType;

         For now leaving this empty
         **/

    }

//    @JsonTypeName("TableOrModalType")
    public static class TableOrModalType extends BaseItem {
//        @JsonProperty("@type")
//        private final String type = "TableOrModalType";

        private List<BaseItem> items;
        @XmlElementWrapper(name = "items")

        @XmlElements({
                @XmlElement(name = "field", type = FieldType.class)})
        public List<BaseItem> getItems() {
            if (items == null) {
                items = new ArrayList<>();
            }
            return items;
        }

        public void setItems(List<BaseItem> items) {
            this.items = items;
        }
    }

    public static class GroupType extends BaseItem {
        private List<BaseItem> items;
        private Boolean collapsible;

        private Boolean collapsed;

        @XmlElement(name = "collapsible")
        public Boolean getCollapsible() {
            return collapsible;
        }

        public void setCollapsible(Boolean collapsible) {
            this.collapsible = collapsible;
        }

        @XmlElement(name = "collapsed")
        public Boolean getCollapsed() {
            return collapsed;
        }

        public void setCollapsed(Boolean collapsed) {
            this.collapsed = collapsed;
        }

        @XmlElementWrapper(name = "items")
        @XmlElements({
                @XmlElement(name = "field", type = FieldType.class), //name is needed to define what is the POJO class that element (in the list) will be mapped to
                @XmlElement(name = "field_list", type = TableOrModalType.class),
                @XmlElement(name = "group", type = GroupType.class)
        })
        public List<BaseItem> getItems() {
            if (items == null) {
                items = new ArrayList<>();
            }
            return items;
        }

        public void setItems(List<BaseItem> items) {
            this.items = items;
        }
    }



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
        private boolean polling = false;
        private long interval;
        private List<Condition> successCondition;
        private List<Condition> failureCondition;

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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
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

        /**
         * Step Friendly_Name
         *
         */
        // ALl steps should have friendly_name
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

        /**
         * Step's type - valid values - "vipr", "script", "ansible", "rest", "remote_ansible"
         *
         */
        // Start and end does not have type
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

        /**
         * path to next step
         *
         */
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

        // End does not have next steps
        /**
         * The next step in the success path
         *
         */
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
