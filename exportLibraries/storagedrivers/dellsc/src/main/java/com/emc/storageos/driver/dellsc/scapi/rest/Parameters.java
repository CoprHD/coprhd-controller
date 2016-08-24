/*
 * Copyright 2016 Dell Inc.
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
package com.emc.storageos.driver.dellsc.scapi.rest;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;

/**
 * REST call parameters.
 */
public class Parameters {
    private Map<String, Object> payload;

    public Parameters() {
        payload = new HashMap<>();
    }

    public void add(String key, Object value) {
        payload.put(key, value);
    }

    public String toJson() {
        Gson gson = new Gson();
        return gson.toJson(payload);
    }

    public Map<String, Object> getRawPayload() {
        return payload;
    }
}
