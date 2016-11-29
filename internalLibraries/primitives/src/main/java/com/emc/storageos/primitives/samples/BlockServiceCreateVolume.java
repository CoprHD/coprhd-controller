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
package com.emc.storageos.primitives.samples;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.primitives.ViPRPrimitive;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.IntegerParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.StringParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.URIParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;
import com.emc.storageos.primitives.output.TableOutputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter.StringOutputParameter;

/**
 *  TODO remove this once primitive generation is done.  This is a 
 *  sample of a create volume primitive
 */
@XmlRootElement(name = "primitive")
public class BlockServiceCreateVolume extends ViPRPrimitive {
    
    private final static String  PATH = "/block/volumes";
    private final static String METHOD = "POST";
    private final static String FRIENDLY_NAME = "REST API";
    private final static String DESCRIPTION = "Execute a REST API method";
    private final static String SUCCESS_CRITERIA = "code > 199 or code < 300";
    
    private final static StringParameter VOLUME_NAME = new StringParameter("name", true, null);
    private final static StringParameter SIZE = new StringParameter("size", true, null);
    private final static IntegerParameter COUNT = new IntegerParameter("count", true, null);
    private final static URIParameter VPOOL = new URIParameter("vpool", true, null);
    private final static URIParameter VARRAY = new URIParameter("varray", true, null);
    private final static URIParameter PROJECT = new URIParameter("project", true, null);
    private final static URIParameter CONSISTENCY_GROUP = new URIParameter("consistency_group", false, null);
    private final static URIParameter COMPUTE_RESOURCE = new URIParameter("compute_resource", false, null);
    
    private final static InputParameter INPUT[] = {VOLUME_NAME, SIZE, COUNT, VPOOL, VARRAY, PROJECT, CONSISTENCY_GROUP, COMPUTE_RESOURCE};
    
    private final static StringOutputParameter RESOURCE = new  StringOutputParameter("resource");
    private final static BasicOutputParameter TABLE[] = {RESOURCE};
    private final static TableOutputParameter RESOURCE_LIST = new TableOutputParameter("taskList", TABLE);
    
    private final static OutputParameter OUTPUT[] = {RESOURCE_LIST};
    
    private final static String BODY = "{\n" +
            "  \"consistency_group\": \"$consistencyGroup\",\n" +
            "  \"computeResource\": \"$computeResource\",\n" +
            "  \"count\": \"$count\",\n" +
            "  \"name\": \"$name\",\n" +
            "  \"project\": \"$project\",\n" +
            "  \"size\": \"$size\",\n" +
            "  \"varray\": \"$varray\",\n" +
            "  \"vpool\": \"$vpool\"\n" +
            "}";
    
    public BlockServiceCreateVolume() {
        super(BlockServiceCreateVolume.class.getName(), FRIENDLY_NAME, DESCRIPTION, SUCCESS_CRITERIA, INPUT, OUTPUT);
    }
    
    @Override 
    public String path() {
        return PATH;
    } 
    
    @Override 
    public String method() {
        return METHOD;
    }

    @Override 
    public String body() {
        return BODY;
    }

}
