package com.emc.storageosplugin.model.vce;

public class VolumeExpandResult extends OperationResult {

	private long newSize;
	private long oldSize;

	public long getNewSize() {
		return newSize;
	}
	
	public void setNewSize(long newSize) {
    	this.newSize = newSize;
    }
	
	public long getOldSize() {
		return oldSize;
	}
	
	public void setOldSize(long oldSize) {
    	this.newSize = oldSize;
    }	
	
}
