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

import java.util.List;
import java.util.Map;

import javax.xml.bind.annotation.XmlElement;

public class InputUpdateParam {
    private Map<String, InputUpdateList> add;
    private Map<String, InputUpdateList> remove;

    @XmlElement(name = "add")
    public Map<String, InputUpdateList> getAdd() {
        return add;
    }

    public void setAdd(final Map<String, InputUpdateList> add) {
        this.add = add;
    }

    @XmlElement(name = "remove")
    public Map<String, InputUpdateList> getRemove() {
        return remove;
    }

    public void setRemove(final Map<String, InputUpdateList> remove) {
        this.remove = remove;
    }
    
    public static class InputUpdateList {
        private List<String> input;

        @XmlElement(name = "input")
        public List<String> getInput() {
            return input;
        }

        
        public void setInput(List<String> input) {
            this.input = input;
        }
        
    }
}
