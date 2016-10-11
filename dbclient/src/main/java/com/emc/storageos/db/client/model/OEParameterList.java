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
 * DB model class that represents a list of parameters
 */
@Cf("OEParameterList")
public class OEParameterList extends OEAbstractParameter {

    private StringSet _parameters;

    @Name("paremeters")
    @RelationIndex(cf = "ParametersRelation", types = { OEParameter.class,
            OEParameterList.class }, type = DataObject.class)
    public StringSet getParameters() {
        return _parameters;
    }

    public void setParameters(final StringSet parameters) {
        _parameters = parameters;
    }
}
