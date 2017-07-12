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


import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "primitive")
public class CustomServicesPrimitiveRestRep extends DataObjectRestRep {
    
    private String type;
    private String friendlyName;
    private String description;
    private String successCriteria;
    private Map<String, InputGroup> inputGroups;
    private List<OutputParameterRestRep> output;
    private Map<String, String> attributes;
    private NamedRelatedResourceRep resource;

    @XmlElement(name = "type")
    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }
    
    @XmlElement(name = "friendly_name")
    public String getFriendlyName() {
        return friendlyName;
    }

    public void setFriendlyName(final String friendlyName) {
        this.friendlyName = friendlyName;
    }

    @XmlElement(name = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @XmlElement(name = "success_criteria")
    public String getSuccessCriteria() {
        return successCriteria;
    }

    public void setSuccessCriteria(final String successCriteria) {
        this.successCriteria = successCriteria;
    }

    @XmlElement(name = "attributes")
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(final  Map<String, String> attributes) {
        this.attributes = attributes;
    }

    @XmlElementWrapper(name = "inputGroups")
    public Map<String, InputGroup> getInputGroups() {
        return inputGroups;
    }

    public void setInputGroups(final Map<String, InputGroup> inputGroups) {
        this.inputGroups = inputGroups;
    }

    @XmlElement(name = "output")
    public List<OutputParameterRestRep> getOutput() {
        return output;
    }

    public void setOutput(final List<OutputParameterRestRep> output) {
        this.output = output;
    }

    @XmlElement(name = "resource")
    public NamedRelatedResourceRep getResource() {
        return resource;
    }

    public void setResource(NamedRelatedResourceRep resource) {
        this.resource = resource;
    }

    public static class InputGroup {

        private List<InputParameterRestRep> inputGroup;

        public void setInputGroup(List<InputParameterRestRep> inputGroup) {
            this.inputGroup = inputGroup;
        }

        @XmlElement(name="input")
        public List<InputParameterRestRep> getInputGroup() {
            return inputGroup;
        }
    }
}
