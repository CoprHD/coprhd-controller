/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.vipr.model.sys.licensing;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "license")
public class License implements Serializable {

    private static final long serialVersionUID = -8988061524265940029L;

    private List<LicenseFeature> licenseFeatures;
    private String licenseText;

    public License() {}

    public License(List<LicenseFeature> licenseFeatures, String licenseText) {
        this.licenseFeatures = licenseFeatures;
        this.licenseText = licenseText;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "license_feature")
    public List<LicenseFeature> getLicenseFeatures() {
        if (licenseFeatures == null) {
            licenseFeatures = new ArrayList<LicenseFeature>();
        }
        return licenseFeatures;
    }    
    
    public void setLicenseFeatures(List<LicenseFeature> licenseFeatures) {
        this.licenseFeatures = licenseFeatures;
    }

    /**
     * 
     * @return
     */
    @XmlElement(name = "license_text", required = true)
    public String getLicenseText() {
        return licenseText;
    }
    
    /**
     * 
     * @param licenseText
     */
    public void setLicenseText(String licenseText) {
        this.licenseText = licenseText;
    }
    
    /**
     * Add a FeatureList to the collection.
     * 
     * @param licenseFeature
     */
    public void addLicenseFeature(LicenseFeature licenseFeature) {
        if (licenseFeatures == null) {
            licenseFeatures = new ArrayList<LicenseFeature>();
        }

        licenseFeatures.add(licenseFeature);
    }
}
