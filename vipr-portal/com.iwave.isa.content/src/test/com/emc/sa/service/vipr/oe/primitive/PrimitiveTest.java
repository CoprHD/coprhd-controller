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
package com.emc.sa.service.vipr.oe.primitive;

import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.testng.Assert;

import com.emc.sa.service.vipr.oe.primitive.Parameter.Type;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.NamedURI;
import com.emc.storageos.db.client.model.OERestCall;

/**
 * @author ssulliva
 *
 */
public class PrimitiveTest {

    DbClient dbClient;

    @Before
    public void setUp() {
        dbClient = new DummyDbClient();
        dbClient.start();
    }

    @Test
    public void createPrimitive() {
        final NamedURI newPrimitive = new NamedURI();
        newPrimitive.setName("createVolume");
        newPrimitive.setURI(URIUtil.createId(OERestCall.class));
        final String description = "Sample primitive test";
        final NamedURI parent = new NamedURI();
        final String successCriteria = "status == 200";
        final String hostname = "localhost";
        final String port = "port";
        final String uri = "/createVolume";
        final String method = "POST";
        final String scheme = "http";
        final String contentType = "application/xml";
        final String accept = "application/json";
        final String body = "{volumeid=%volId%}";

        final Map<String, AbstractParameter<?>> input = new HashMap<String, AbstractParameter<?>>();
        final Map<String, AbstractParameter<?>> output = new HashMap<String, AbstractParameter<?>>();
        final Set<String> extraHeaders = new HashSet<String>();
        final Set<String> query = new HashSet<String>();
        extraHeaders.add("X-SDS-AUTH-TOKEN=%proxyToken%");
        input.put("volId", new Parameter("volId", "volume ID", "", Type.STRING,
                false, false));
        output.put("status", new Parameter("status", "status", "",
                Type.INTEGER, false, false));

        final RestPrimitive restPrimitive = new RestPrimitive(newPrimitive,
                parent, description, successCriteria, input, output, hostname,
                port, uri, method, scheme, contentType, accept, extraHeaders,
                body, query);

        PrimitiveHelper.savePrimitive(restPrimitive, dbClient);

        final Primitive primitive = PrimitiveHelper.loadPrimitive(newPrimitive,
                dbClient);
        Assert.assertEquals(primitive.name(), newPrimitive);
        Assert.assertEquals(primitive.description(), description);
        Assert.assertEquals(primitive.parent().getName(), parent.getName());
        Assert.assertEquals(primitive.parent().getURI(), parent.getURI());
        Assert.assertEquals(primitive.successCriteria(), successCriteria);
        Assert.assertEquals(primitive.input().keySet(), input.keySet());
        Assert.assertEquals(primitive.output().keySet(), output.keySet());
        if (primitive.isRestPrimitive()) {
            final RestPrimitive loadedRestPrimitive = primitive
                    .asRestPrimitive();
            Assert.assertEquals(loadedRestPrimitive.hostname(), hostname);
            Assert.assertEquals(loadedRestPrimitive.port(), port);
            Assert.assertEquals(loadedRestPrimitive.uri(), uri);
            Assert.assertEquals(loadedRestPrimitive.method(), method);
            Assert.assertEquals(loadedRestPrimitive.scheme(), scheme);
            Assert.assertEquals(loadedRestPrimitive.contentType(), contentType);
            Assert.assertEquals(loadedRestPrimitive.accept(), accept);
            Assert.assertEquals(loadedRestPrimitive.body(), body);
            Assert.assertEquals(loadedRestPrimitive.extraHeaders(),
                    extraHeaders);
            Assert.assertEquals(loadedRestPrimitive.query(), query);

        } else {
            fail("Primitive type not loaded");
        }
    }
}
