/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.vnxe.models;

import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class PoolTier {
	private String name;
	private int  tierType;
	private int stripeWidth;
	private int raidType;
	private long sizeTotal;
	private long sizeUsed;
	private long sizeFree;
	private long sizeMovingDown;
	private long sizeMovingUp;
	private long sizeMovingWithin;
	private List<RaidGroup> raidGroups;
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}

	public int getRaidType() {
		return raidType;
	}

	public void setRaidType(int raidLevel) {
		this.raidType = raidLevel;
	}

	public RaidTypeEnum getRaidTypeEnum() {
	    return RaidTypeEnum.getEnumValue(raidType);
	}
	
	public long getSizeTotal() {
		return sizeTotal;
	}

	public void setSizeTotal(long sizeTotal) {
		this.sizeTotal = sizeTotal;
	}

	public long getSizeUsed() {
		return sizeUsed;
	}

	public void setSizeUsed(long sizeUsed) {
		this.sizeUsed = sizeUsed;
	}

	public long getSizeFree() {
		return sizeFree;
	}

	public void setSizeFree(long sizeFree) {
		this.sizeFree = sizeFree;
	}

	public long getSizeMovingDown() {
		return sizeMovingDown;
	}

	public void setSizeMovingDown(long sizeMovingDown) {
		this.sizeMovingDown = sizeMovingDown;
	}

	public long getSizeMovingUp() {
		return sizeMovingUp;
	}

	public void setSizeMovingUp(long sizeMovingUp) {
		this.sizeMovingUp = sizeMovingUp;
	}

	public long getSizeMovingWithin() {
		return sizeMovingWithin;
	}

	public void setSizeMovingWithin(long sizeMovingWithin) {
		this.sizeMovingWithin = sizeMovingWithin;
	}
    public int getTierType() {
        return tierType;
    }
    public void setTierType(int tierType) {
        this.tierType = tierType;
    }
    public int getStripeWidth() {
        return stripeWidth;
    }
    public void setStripeWidth(int stripeWidth) {
        this.stripeWidth = stripeWidth;
    }
    public List<RaidGroup> getRaidGroups() {
        return raidGroups;
    }
    public void setRaidGroups(List<RaidGroup> raidGroups) {
        this.raidGroups = raidGroups;
    }
	
	
}
