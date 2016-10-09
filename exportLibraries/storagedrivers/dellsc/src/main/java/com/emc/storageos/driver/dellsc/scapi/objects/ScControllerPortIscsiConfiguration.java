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
package com.emc.storageos.driver.dellsc.scapi.objects;

import java.util.ArrayList;
import java.util.List;

/**
 * Port configuration settings for iSCSI ports.
 */
public class ScControllerPortIscsiConfiguration extends ScControllerPortConfiguration {
    public boolean chapEnabled;
    public String chapName;
    public String chapSecret;
    public String ipAddress;
    public String macAddress;
    public long portNumber;
    public String subnetMask;
    public int vlanId;
    public boolean vlanTagging;

    /**
     * Gets the MAC address in the preferred format.
     * 
     * @return The formatted MAC address.
     */
    public String getFormattedMACAddress() {
        String defaultReturn = "00:00:00:00:00:00";

        // Validate the data
        if (macAddress == null) {
            return defaultReturn;
        }
        String mac = macAddress.replace("-", "");
        if (mac.length() != 12) {
            return defaultReturn;
        }

        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int offset = 2 * i;
            parts.add(mac.substring(offset, offset + 2));
        }

        return String.join(":", parts);
    }

    /**
     * Gets the network based on the IP address and subnet mask.
     * 
     * @return The network.
     */
    public String getNetwork() {
        String[] ipOctets = ipAddress.split("\\.");
        String[] subnetOctets = subnetMask.split("\\.");
        String[] result = ipOctets;

        for (int i = 0; i < 4; i++) {
            if (!"255".equals(subnetOctets[i])) {
                int sub = Integer.parseInt(subnetOctets[i]);
                int ip = Integer.parseInt(ipOctets[i]);
                result[i] = String.format("%s", (ip & sub));
            }
        }

        return String.join(".", result);
    }
}
