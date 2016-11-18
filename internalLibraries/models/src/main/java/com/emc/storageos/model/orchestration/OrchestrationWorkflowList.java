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

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "orchestration_workflow_list")
public class OrchestrationWorkflowList {
    
    private List<URI> workflows;
    
    public OrchestrationWorkflowList() {}
    
    public OrchestrationWorkflowList(List<URI> workflows) {
        this.workflows = workflows;
    }

    @XmlElement(name = "workflows")
    public List<URI> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<URI> workflows) {
        this.workflows = workflows;
    }
    
}
