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

import com.emc.storageos.model.NamedRelatedResourceRep;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Rest representation for WF directory list
 */
@XmlRootElement(name = "wf_directories")
public class WFDirectoryList {

    private List<NamedRelatedResourceRep> wfDirectories;

    /**
     * List of approvals
     *
     */
    @XmlElement(name = "wf_directory")
    public List<NamedRelatedResourceRep> getWFDirectories() {
        if (wfDirectories == null) {
            wfDirectories = new ArrayList<>();
        }
        return wfDirectories;
    }

    public void setAs(List<NamedRelatedResourceRep> approvals) {
        this.wfDirectories = wfDirectories;
    }

}
