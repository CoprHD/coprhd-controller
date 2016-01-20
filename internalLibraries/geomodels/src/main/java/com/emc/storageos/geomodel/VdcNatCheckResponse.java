/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.vipr.model.sys.NatCheckResponse;

/**
 * We hold this empty marker class because:
 *  1. VDC NAT check may return more extra VDS-specified fields
 *  2. Back-compatible, make sure return XML has same root tag as previous version
 */
@XmlRootElement
public class VdcNatCheckResponse extends NatCheckResponse {
    
}
