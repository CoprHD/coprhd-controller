/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.geomodel;

import javax.xml.bind.annotation.XmlRootElement;

import com.emc.vipr.model.sys.NatCheckParam;

/**
 * We hold this empty marker class because:
 *  1. VDC NAT check may send more extra VDS-specified fields
 *  2. Back-compatible, make sure XML has same root tag as previous version
 */
@XmlRootElement(name = "vdc-nat-check")
public class VdcNatCheckParam extends NatCheckParam{
}
