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

package com.emc.sa.service.vipr.customservices.gson;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class ViprOperation {

    public ViprOperation(ViprTask t){
        task = new ViprTask[1];
        task[0] = t;
    }

    private ViprTask[] task;

    public ViprTask[] getTask() {
        return task;
    }
    
    public List<URI> getTaskIds() throws URISyntaxException {
        List<URI> idList = new ArrayList<>();
        if (task != null) {
            for(ViprTask aTask : task) {
                idList.add(new URI(aTask.getId()));
            }
        }
        return idList;
    }

    public boolean isValid() {
        return task != null;
    }

}
