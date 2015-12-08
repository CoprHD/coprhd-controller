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
