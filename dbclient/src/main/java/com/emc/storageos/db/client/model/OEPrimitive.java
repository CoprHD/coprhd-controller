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

import java.net.URI;

/**
 * DB model class that contains the base properties of a primitive. A primitive
 * mostly only has references to attributes/parameters since a primitive is
 * built based on an inheritance model
 **/
public abstract class OEPrimitive extends DataObject {

    private static final long serialVersionUID = 1L;
    private String _name;
    private URI _description;
    private URI _parent;
    private URI _successCriteria;
    private StringSet _input;
    private StringSet _output;

    @Name("name")
    @AlternateId("PrimitiveNameIndex")
    public String getName() {
        return _name;
    }

    public void setName(final String name) {
        _name = name;
        setChanged("name");
    }

    @Name("description")
    public URI getDescription() {
        return _description;
    }

    public void setDescription(final URI description) {
        _description = description;
        setChanged("description");
    }

    @RelationIndex(cf = "PrimitiveParentIndex", type = OEPrimitive.class)
    @Name("parent")
    public URI getParent() {
        return _parent;
    }

    public void setParent(final URI parent) {
        _parent = parent;
        setChanged("parent");
    }

    @Name("successCriteria")
    public URI getSuccessCriteria() {
        return _successCriteria;
    }

    public void setSuccessCriteria(final URI successCriteria) {
        _successCriteria = successCriteria;
        setChanged("successCriteria");
    }

    @Name("input")
    public StringSet getInput() {
        return _input;
    }

    public void setInput(final StringSet input) {
        _input = input;
        setChanged("input");
    }

    @Name("output")
    public StringSet getOutput() {
        return _output;
    }

    public void setOutput(final StringSet output) {
        _output = output;
        setChanged("output");
    }

    public abstract boolean isRestCall();

    public abstract OERestCall asRestCall();

}
