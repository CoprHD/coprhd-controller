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

import com.google.gson.annotations.SerializedName;
import java.util.List;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "primitive")
public class PrimitiveRestRep {
    @SerializedName("name")
    private String name;
    @SerializedName("friendly_name")
    private String friendlyName;
    @SerializedName("description")
    private String description;
    @SerializedName("success_criteria")
    private String successCriteria;
    @SerializedName("input")
    private List<InputParameterRestRep> input;
    @SerializedName("output")
    private List<OutputParameterRestRep> output;

    @XmlElement(name = "name")
    public String getName() {
        return name;
    }
    
    public void setName(final String name) {
        this.name = name;
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

    @XmlElementWrapper(name="input")
    @XmlElement(name = "parameter")
    public List<InputParameterRestRep> getInput() {
        return input;
    }

    public void setInput(final List<InputParameterRestRep> input) {
        this.input = input;
    }
    
    @XmlElementWrapper(name="output")
    @XmlElement(name = "parameter")
    public List<OutputParameterRestRep> getOutput() {
        return output;
    }

    public void setOutput(final List<OutputParameterRestRep> output) {
        this.output = output;
    }
}
