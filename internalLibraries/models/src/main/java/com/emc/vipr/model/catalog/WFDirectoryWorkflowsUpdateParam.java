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

import com.emc.storageos.model.block.export.UpdateParam;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public class WFDirectoryWorkflowsUpdateParam extends UpdateParam{

    public WFDirectoryWorkflowsUpdateParam(){

    }

    public WFDirectoryWorkflowsUpdateParam(Set<URI> add, Set<URI> remove) {
        this.add = add;
        this.remove = remove;
    }

    /**
     * List of workflow URIs to be added to the WF Directory.
     */
    @XmlElementWrapper(required = false)
    @XmlElement(name = "workflow")
    public Set<URI> getAdd() {
        if (add == null) {
            add = new HashSet<URI>();
        }
        return add;
    }

    public void setAdd(final Set<URI> add) {
        this.add = add;
    }

    /**
     * List of workflow URIs to be removed from the WF Directory.
     */
    @XmlElementWrapper(required = false)
    @XmlElement(name = "workflow")
    public Set<URI> getRemove() {
        if (remove == null) {
            remove = new HashSet<URI>();
        }
        return remove;
    }

    public void setRemove(final Set<URI> remove) {
        this.remove = remove;
    }
}
