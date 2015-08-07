/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.vipr.client.system;

import static com.emc.vipr.client.system.impl.PathConstants.LICENSE_URL;

import com.emc.vipr.client.impl.RestClient;
import com.emc.vipr.model.sys.licensing.License;

public class Licensing {

    private RestClient client;

    public Licensing(RestClient client) {
        this.client = client;
    }

    /**
     * Return the license file as individual xml elements and also includes the
     * full license text.
     * <p>
     * API Call: GET /license
     * 
     * @return The license
     */
    public License get() {
        return client.get(License.class, LICENSE_URL);
    }

    /**
     * Add a license to the system.
     * <p>
     * API Call: POST /license
     * 
     * @param licenseText The text for the license file to upload.
     */
    public void set(String licenseText) {
        License license = new License();
        license.setLicenseText(licenseText);
        client.post(String.class, license, LICENSE_URL);
    }
}
