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

public abstract class OEPrimitive extends DataObject {

    private static final long serialVersionUID = 1L;
    private NamedURI _name;
    private String _description;
    private NamedURI _parent;
    private String _successCriteria;
    private StringMap _input;
    private StringMap _output;

    @Name("name")
    public NamedURI getName() {
        return _name;
    }

    public void setName(final NamedURI name) {
        _name = name;
    }

    @Name("description")
    public String getDescription() {
        return _description;
    }

    public void setDescription(final String description) {
        _description = description;
    }

    @NamedRelationIndex(cf = "NamedRelation", type = OEPrimitive.class)
    @Name("parent")
    public NamedURI getParent() {
        return _parent;
    }

    public void setParent(final NamedURI parent) {
        _parent = parent;
    }

    @Name("successCriteria")
    public String getSuccessCriteria() {
        return _successCriteria;
    }

    public void setSuccessCriteria(final String successCriteria) {
        _successCriteria = successCriteria;
    }

    @Name("input")
    public StringMap getInput() {
        return _input;
    }

    public void setInput(final StringMap input) {
        _input = input;
    }

    @Name("output")
    public StringMap getOutput() {
        return _output;
    }

    public void setOutput(final StringMap output) {
        _output = output;
    }

    public abstract boolean isRestCall();

    public abstract OERestCall asRestCall();

}
