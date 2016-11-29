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
package com.emc.storageos.primitives.output;

import javax.xml.bind.annotation.XmlElement;

import com.emc.storageos.primitives.Parameter;
import com.emc.storageos.primitives.Parameter.ParameterType;

/**
 * Class that represents a simple output parameter
 */
public abstract class BasicOutputParameter extends OutputParameter {
    
    public BasicOutputParameter(final String name) {
        super(name);
    }
    
    @Override 
    public boolean isBasicOutputParameter() {
        return true;
    }
    
    @Override 
    public BasicOutputParameter asBasicOutputParameter() {
        return this;
    }

    @Override 
    public boolean isTableOutputParameter() {
        return false;
    }

    @Override 
    public TableOutputParameter asTableOutputParameter() {
        return null;
    }
    
    public static class StringOutputParameter extends BasicOutputParameter {

        public StringOutputParameter(String name) {
            super(name);
        }

        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.STRING;
        }
        
    }
    
    public static class NameValueListOutputParameter extends BasicOutputParameter {

        public NameValueListOutputParameter(String name) {
            super(name);
        }

        @Override 
        public ParameterType getType() {
            return ParameterType.NAME_VALUE_LIST;
        }
        
    }
}
