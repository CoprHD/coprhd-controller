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

import java.net.URI;

import com.emc.storageos.primitives.Primitive;
import com.emc.storageos.primitives.input.BasicInputParameter.IntegerParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.NameValueListParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.StringParameter;
import com.emc.storageos.primitives.input.BasicInputParameter.URIParameter;
import com.emc.storageos.primitives.input.InputParameter;
import com.emc.storageos.primitives.output.BasicOutputParameter;
import com.emc.storageos.primitives.output.OutputParameter;

/**
 * Class to contains the meta data for a generic rest call primitive
 */
public class RestPrimitive extends Primitive {

    private final static URI id = URI.create(String.format(
            "urn:storageos:%1$s:%2$s:", RestPrimitive.class.getSimpleName(),
            RestPrimitive.class.getName()));
    private final static String FRIENDLY_NAME = "REST API";
    private final static StepType TYPE = StepType.REST;
    private final static String DESCRIPTION = "Execute a REST API method";
    private final static String SUCCESS_CRITERIA = "code > 199 or code < 300";
    private final static StringParameter HOSTNAME = new StringParameter("hostname", true, null);
    private final static IntegerParameter PORT = new IntegerParameter("port", true, null);
    private final static URIParameter _URI = new URIParameter("uri", true, null);
    private final static StringParameter METHOD = new StringParameter("method", true, null);
    private final static StringParameter SCHEME = new StringParameter("scheme", true, null);
    private final static StringParameter CONTENT_TYPE = new StringParameter("contentType", false, null);
    private final static StringParameter ACCEPT = new StringParameter("accept", false, null);
    private final static NameValueListParameter EXTRA_HEADERS = new NameValueListParameter("extraHeaders", false, null);
    private final static StringParameter BODY = new StringParameter("body", true, null);
    private final static NameValueListParameter QUERY = new NameValueListParameter("query", false, null);
    
    private final static InputParameter INPUT[] = {HOSTNAME, PORT, _URI, METHOD, SCHEME, CONTENT_TYPE, ACCEPT, EXTRA_HEADERS, BODY, QUERY};

    private final static BasicOutputParameter.NameValueListParameter HEADERS = new BasicOutputParameter.NameValueListParameter("headers");
    private final static BasicOutputParameter.StringOutputParameter ENTITY = new BasicOutputParameter.StringOutputParameter("entity");
    
    private final static OutputParameter OUTPUT[] = {HEADERS,ENTITY};
    
    public RestPrimitive( ) {
        super(id, RestPrimitive.class.getName(), FRIENDLY_NAME, DESCRIPTION, SUCCESS_CRITERIA, INPUT, OUTPUT,TYPE);
    }
}
