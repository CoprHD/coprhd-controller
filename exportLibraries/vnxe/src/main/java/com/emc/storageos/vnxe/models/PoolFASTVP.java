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

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown=true)
public class PoolFASTVP {
	private int status;
	private int relocationRate;
	private boolean isScheduleEnabled;
	private String relocationDurationEstimate;
	private long sizeMovingDown;
	private long sizeMovingUp;
	private long sizeMovingWithin;
	private int percentComplete;
	private int type;
	
	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
	}

	public int getRelocationRate() {
		return relocationRate;
	}

	public void setRelocationRate(int relocationRate) {
		this.relocationRate = relocationRate;
	}

	public boolean getIsScheduleEnabled() {
		return isScheduleEnabled;
	}

	public void setIsScheduleEnabled(boolean isScheduleEnabled) {
		this.isScheduleEnabled = isScheduleEnabled;
	}

	public String getRelocationDurationEstimate() {
		return relocationDurationEstimate;
	}

	public void setRelocationDurationEstimate(String relocationDurationEstimate) {
		this.relocationDurationEstimate = relocationDurationEstimate;
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

	public int getPercentComplete() {
		return percentComplete;
	}

	public void setPercentComplete(int percentComplete) {
		this.percentComplete = percentComplete;
	}

	public int getType() {
		return type;
	}

	public void setType(int type) {
		this.type = type;
	}

	public static enum FastVPStatusEnum {
		NOT_APPLICABLE(1),
		PAUSED(2),
		RELOCATING(3),
		READY(4);
		
		private int value;

	    private FastVPStatusEnum(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return this.value;
	    }
	}
	
	public static enum FastVPRelocationRateEnum {
		HIGH(1),
		MEDIUM(2),
		LOW(3);
		
		private int value;

	    private FastVPRelocationRateEnum(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return this.value;
	    }
	}
	
	public static enum PoolDataRelocationTypeEnum {
		MANUAL(1),
		SCHEDULED(2),
		REBALANCE(3);
		
		private int value;

	    private PoolDataRelocationTypeEnum(int value) {
	        this.value = value;
	    }

	    public int getValue() {
	        return this.value;
	    }
	}
}
