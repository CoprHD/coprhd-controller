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
package com.emc.sa.customservices;

import java.util.List;

public class WFDirectoryDef {

    private String label;
    private String id;
    private List<WFDirectoryDef> directories;
    private List<String> workflows;
    
    public String label() {
        return label;
    }
    
    public String id() {
        return id;
    }
    
    public List<WFDirectoryDef> directories() { 
        return directories;
    }
    
    public List<String> workflows() {
        return workflows;
    }
    
    
}
