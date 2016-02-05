/*
 * Copyright 2016 Intel Corporation
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

package com.emc.storageos.db.client.model;


import java.net.URI;

/**
 * Quality of Service specification data object.
 */

@Cf("QosSpecs")
public class QosSpecification extends DataObject{
    private String consumer;
    private String name;
    private StringMap specs;
    private URI virtualPoolId;

    @Name("consumer")
    public String getConsumer() {
        return consumer;
    }

    public void setConsumer(String consumer) {
        this.consumer = consumer;
        this.setChanged("consumer");
    }

    @Name("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.setChanged("name");
    }

    @Name("specs")
    public StringMap getSpecs() {
        return specs;
    }

    public void setSpecs(StringMap specs) {
        this.specs = specs;
        this.setChanged("specs");
    }

    @Name("virtualPoolId")
    public URI getVirtualPoolId() {
        return virtualPoolId;
    }

    public void setVirtualPoolId(URI virtualPoolId) {
        this.virtualPoolId = virtualPoolId;
        this.setChanged("virtualPoolId");
    }
}
