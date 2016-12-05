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
package com.emc.storageos.primitives.input;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

/**
 * Class that represents the meta data for an simple input parameter
 * 
 * @param <T>
 *            - The type of this parameter
 */
public abstract class BasicInputParameter<T> extends InputParameter {
    
    private boolean required;
    private T defaultValue;
    public BasicInputParameter(final String name, final boolean required, final T value) {
        super(name);
        this.required = required;
        this.defaultValue = value;
    }
    
    public boolean getRequired() {
        return required;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
    

    @Override 
    public boolean isBasicInputParameter() {
        return true;
    }

    @Override 
    public BasicInputParameter<?> asBasicInputParameter() {
        return this;
    }

    @Override 
    public boolean isTableInputParameter() {
        return false;
    }


    @Override 
    public TableInputParameter asTableInputParameter() {
        return null;
    }
    
    public static class StringParameter extends BasicInputParameter<String> {
        
        public StringParameter(final String name, final boolean required, final String defaultValue) {
            super(name, required, defaultValue);
        }

        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.STRING;
        }
    }
    
    public static class IntegerParameter extends  BasicInputParameter<Integer> {
        
        public IntegerParameter(final String name, final boolean required, final Integer defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.INTEGER;
        }
    }
    
    public static class LongParameter extends  BasicInputParameter<Long> {
        
        public LongParameter(final String name, final boolean required, final Long defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.LONG;
        }
    }
    
    public static class URIParameter extends  BasicInputParameter<URI> {
        
        public URIParameter(final String name, final boolean required, final URI defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.URI;
        }
    }

    public static class BooleanParameter extends  BasicInputParameter<Boolean> {
        
        public BooleanParameter(final String name, final boolean required, final Boolean defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.BOOLEAN;
        }
    }

    public static class NameValueListParameter extends BasicInputParameter<Map<String, String>> {

        public NameValueListParameter(String name, boolean required,
                Map<String, String> defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.NAME_VALUE_LIST;
        }
        
    }
    
    public static class DateTimeParameter extends BasicInputParameter<Calendar> {

        public DateTimeParameter(String name, boolean required,
                Calendar defaultValue) {
            super(name, required, defaultValue);
        }
        
        @Override 
        @XmlElement(name = "type")
        public ParameterType getType() {
            return ParameterType.DATETIME;
        }
        
    }
    
    public static class InputColumn<X,Y extends List<X>> extends BasicInputParameter<Y> {

        private final BasicInputParameter<X> header;
        public InputColumn(String name, boolean required, BasicInputParameter<X> header, Y defaultValue) {
            super(name, required, defaultValue);
            this.header = header;
        }

        @XmlElement(name = "header")
        public BasicInputParameter<X> getHeader() {
            return header;
        }
        @Override 
        public ParameterType getType() {
            return header.getType();
        }

    }
    
    
}
