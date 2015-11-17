package com.emc.storageos.cinder.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public class CinderQosKeyUpdateRequest {

    /**
     * Json model for updating QoS keys
     * {
     *  "qos_specs": {
     *      "specs": {
     *           "availability": "100",
     *           "numberOfFailures": "0"
     *       },
     *  }
     * }
     */

    @SerializedName("qos_specs")
    QosSpecs qosSpecs = new QosSpecs();

    public class QosSpecs {
        public Map<String, String> specs;
    }

}
