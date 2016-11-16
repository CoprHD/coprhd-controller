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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "orchestration_workflow_update_param")
public class OrchestrationWorkflowUpdateParam {
    private OrchestrationWorkflowDocument document;
    
    @XmlElement(name = "document")
    public OrchestrationWorkflowDocument getDocument() {
        return document;
    }

    public void setDocument(OrchestrationWorkflowDocument document) {
        this.document = document;
    }
}
