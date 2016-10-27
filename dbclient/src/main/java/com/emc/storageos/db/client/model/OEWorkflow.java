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
package com.emc.storageos.db.client.model;

/**
 * DB model to represent an orchestration engine workflow document
 */
@Cf("OEWorkflow")
public class OEWorkflow extends DataObject {

    private String _name;
    private String _document;

    @Name("name")
    @AlternateId("OEWorkflowNameIndex")
    public String getName() {
        return _name;
    }

    public void setName(final String name) {
        _name = name;
        setChanged("name");
    }
    
    @Name("document")
    public String getDocument() {
        return _document;
    }

    public void setDocument(final String document) {
        _document = document;
        setChanged("document");
    }
    
}
