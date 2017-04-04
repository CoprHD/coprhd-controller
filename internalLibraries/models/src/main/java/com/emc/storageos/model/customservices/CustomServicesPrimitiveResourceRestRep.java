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
package com.emc.storageos.model.customservices;

import java.net.URI;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.DataObjectRestRep;

@XmlRootElement(name = "primitive_resource")
public class CustomServicesPrimitiveResourceRestRep extends DataObjectRestRep {

    private List<Attribute> attributes;
    private URI parentId;

    @XmlElement(name = "parent_id")
    public URI getParentId() {
        return parentId;
    }

    public void setParentId(final URI parentId) {
        this.parentId = parentId;
    }
    
    public List<Attribute> getAttributes() {
        return attributes;
    }
    
    public void setAttributes(final List<Attribute> attributes) {
        this.attributes = attributes;
    }
    
    public static class Attribute {
        private String name;
        private List<String> values;

        @XmlElement(name = "name")
        public String getName() {
            return name;
        }

        public void setName(final String name) {
            this.name = name;
        }

        @XmlElement(name = "value")
        public List<String> getValues() {
            return values;
        }

        public void setValues(final List<String> values) {
            this.values = values;
        }
    }
}
