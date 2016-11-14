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
package com.emc.vipr.model.catalog;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.RelatedResourceRep;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.net.URI;
import java.util.List;

/**
 * Rest representation for WF directory records
 */
@XmlRootElement(name = "wf_directory")
public class WFDirectoryRestRep extends DataObjectRestRep{

    private RelatedResourceRep parent;
    private List<URI> workflows;

    @XmlElement(name = "parent")
    public RelatedResourceRep getParent() {
        return parent;
    }

    public void setParent(RelatedResourceRep parent) {
        this.parent = parent;
    }

    @XmlElementWrapper(name = "workflows")
    @XmlElement(name = "workflow")
    public List<URI> getWorkflows() {
        return workflows;
    }

    public void setWorkflows(List<URI> workflows) {
        this.workflows = workflows;
    }
}
