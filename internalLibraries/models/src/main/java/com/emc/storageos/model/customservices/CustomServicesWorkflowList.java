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

import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.NamedRelatedResourceRep;

@XmlRootElement(name = "custom_services_workflow_list")
public class CustomServicesWorkflowList {
    
    private List<NamedRelatedResourceRep> workflows;
    
    public CustomServicesWorkflowList() {}
    
    public CustomServicesWorkflowList(List<NamedRelatedResourceRep> workflows) {
        this.workflows = workflows;
    }

    @XmlElement(name = "workflows")
    public List<NamedRelatedResourceRep> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(final List<NamedRelatedResourceRep> workflows) {
        this.workflows = workflows;
    }
    
}
