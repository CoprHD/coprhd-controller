/*
 * Copyright 2017 Dell Inc. or its subsidiaries.
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
package com.emc.storageos.primitives.db;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.ModelObject;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.uimodels.CustomServicesDBPrimitive;
import com.emc.storageos.primitives.CustomServicesPrimitiveType;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.OutputParameter;


/**
 * Class that respresents an instance of a primitive stored in the database as a java object
 *
 */
public abstract class CustomServicesDBPrimitiveType extends CustomServicesPrimitiveType {

    private final Map<String, List<InputParameter>> input;
    private final List<OutputParameter> output;
    private final Map<String,String> attributes;
    private final CustomServicesDBPrimitive primitive;
    
    public CustomServicesDBPrimitiveType(final CustomServicesDBPrimitive primitive, 
            final Map<String, List<InputParameter>> input, final Map<String,String> attributes,
            final List<OutputParameter> output) {
        this.input = input;
        this.output = output;
        this.attributes = attributes;
        this.primitive = primitive;
        
    }
    
    @Override 
    public URI id() {
        return primitive.getId();
    }
    
    @Override 
    public String name() {
        return primitive.getLabel();
    }
    
    @Override
    public String friendlyName() {
        return primitive.getFriendlyName();
    }

    @Override
    public String description() {
        return primitive.getDescription();
    }

    @Override
    public String successCriteria() {
        return primitive.getSuccessCriteria();
    }

    @Override
    public Map<String, List<InputParameter>> input() {
        return input;
    }

    @Override
    public List<OutputParameter> output() {
        return output;
    }
    
    @Override
    public Map<String, String> attributes() {
        return attributes;
    }

    @Override
    public NamedURI resource() {
        return primitive.getResource();
    }
    
    @Override
    public ModelObject asModelObject() {
        return primitive;
    }
}
