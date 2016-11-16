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
package com.emc.storageos.db.client.model.uimodels;

import com.emc.storageos.db.client.model.AlternateId;
import com.emc.storageos.db.client.model.Cf;
import com.emc.storageos.db.client.model.Name;

/**
 * DB model to represent an orchestration engine workflow document
 */
@Cf("OEWorkflow")
public class OEWorkflow extends ModelObjectWithACLs {

    public static final String DOCUMENT = "document";
    public static final String NAME = "name";
    
    private String name;
    private String document;
    private Integer sortedIndex;

    @Name(NAME)
    @AlternateId("OEWorkflowNameIndex")
    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
        setChanged(NAME);
    }
    
    @Name(DOCUMENT)
    public String getDocument() {
        return document;
    }

    public void setDocument(final String document) {
        this.document = document;
        setChanged(DOCUMENT);
    }

    @Override
    public Object[] auditParameters() {
        // TODO Auto-generated method stub
        return null;
    }
    
}
