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

public abstract class OEPrimitive extends DataObject {

    private static final long serialVersionUID = 1L;
    private NamedURI _name;
    private URI _description;
    private NamedURI _parent;
    private URI _successCriteria;
    private StringSet _input;
    private StringSet _output;

    @Name("name")
    public NamedURI getName() {
        return _name;
    }

    public void setName(final NamedURI name) {
        _name = name;
    }

    @RelationIndex(cf = "DescriptionRelation", type = OEAttribute.class)
    public URI getDescription() {
        return _description;
    }

    public void setDescription(final URI description) {
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

    @RelationIndex(cf = "SuccessRelation", type = OEAttribute.class)
    public URI getSuccessCriteria() {
        return _successCriteria;
    }

    public void setSuccessCriteria(final URI successCriteria) {
        _successCriteria = successCriteria;
    }

    @Name("input")
    @RelationIndex(cf = "InputRelation", types = { OEParameter.class,
            OEParameterList.class }, type = DataObject.class)
    public StringSet getInput() {
        return _input;
    }

    public void setInput(final StringSet input) {
        _input = input;
    }

    @Name("output")
    @RelationIndex(cf = "OutputRelation", types = { OEParameter.class,
            OEParameterList.class }, type = DataObject.class)
    public StringSet getOutput() {
        return _output;
    }

    public void setOutput(final StringSet output) {
        _output = output;
    }

    public abstract boolean isRestCall();

    public abstract OERestCall asRestCall();

}
