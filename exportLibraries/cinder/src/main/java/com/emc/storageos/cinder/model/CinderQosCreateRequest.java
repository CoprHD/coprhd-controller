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

package com.emc.storageos.cinder.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Quality of Service object required for "Create QoS specification" call.
 */
public class CinderQosCreateRequest {

    /**
     * Json model for creating QoS request
     * {
     *  "qos_specs": {
     *      "specs": {
     *           "availability": "100",
     *           "numberOfFailures": "0"
     *       },
     *       "name": "reliability-spec",
     *  }
     * }
     */

    @SerializedName("qos_specs")
    QosSpecs qosSpecs = new QosSpecs();

    public class QosSpecs {
        public String name;
        public Map<String, String> specs;
    }

}
