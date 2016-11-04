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
package com.emc.storageos.model.orchestration.internal;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.storageos.model.orchestration.internal.BasicInputParameter.IntegerParameter;
import com.emc.storageos.model.orchestration.internal.BasicInputParameter.NameValueListParameter;
import com.emc.storageos.model.orchestration.internal.BasicInputParameter.StringParameter;
import com.emc.storageos.model.orchestration.internal.BasicInputParameter.URIParameter;
import com.emc.storageos.model.orchestration.internal.BasicOutputParameter.NameValueListOutputParameter;
import com.emc.storageos.model.orchestration.internal.BasicOutputParameter.StringOutputParameter;

/**
 * Class to contains the meta data for a generic rest call primitive
 */
@XmlRootElement(name = "primitive")
public class RestPrimitive extends Primitive {

    private final static String FRIENDLY_NAME = "REST API";
    private final static String DESCRIPTION = "Execute a REST API method";
    private final static String SUCCESS_CRITERIA = "code > 199 or code < 300";
    
    private final static StringParameter HOSTNAME = new StringParameter("hostname", true, null);
    private final static IntegerParameter PORT = new IntegerParameter("port", true, null);
    private final static URIParameter URI = new URIParameter("uri", true, null);
    private final static StringParameter METHOD = new StringParameter("method", true, null);
    private final static StringParameter SCHEME = new StringParameter("scheme", true, null);
    private final static StringParameter CONTENT_TYPE = new StringParameter("contentType", false, null);
    private final static StringParameter ACCEPT = new StringParameter("accept", false, null);
    private final static NameValueListParameter EXTRA_HEADERS = new NameValueListParameter("extraHeaders", false, null);
    private final static StringParameter BODY = new StringParameter("port", true, null);
    private final static NameValueListParameter QUERY = new NameValueListParameter("query", false, null);
    
    private final static InputParameter INPUT[] = {HOSTNAME, PORT, URI, METHOD, SCHEME, CONTENT_TYPE, ACCEPT, EXTRA_HEADERS, BODY, QUERY};

    private final static NameValueListOutputParameter HEADERS = new NameValueListOutputParameter("headers");
    private final static StringOutputParameter ENTITY = new StringOutputParameter("entity");
    
    private final static OutputParameter OUTPUT[] = {HEADERS,ENTITY};
    
    public RestPrimitive( ) {
        super(RestPrimitive.class.getName(), FRIENDLY_NAME, DESCRIPTION, SUCCESS_CRITERIA, INPUT, OUTPUT);
    }
}
