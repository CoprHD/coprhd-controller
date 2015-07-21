/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.recoverpoint.objectmodel;

import java.util.HashSet;
import java.util.Set;

import com.emc.fapiclient.ws.ConsistencyGroupCopyUID;
import com.emc.fapiclient.ws.RecoverPointTimeStamp;

/**
 * Representation of a RecoverPoint bookmark
 */

public class RPBookmark {
	private String _bookmarkName;
	private ConsistencyGroupCopyUID _cgGroupCopyUID;
	private RecoverPointTimeStamp _bookmarkTime;
	private ConsistencyGroupCopyUID _productionCopyUID;
	private Set<String> _wwnSet;
	
	public RPBookmark () {
		_wwnSet = new HashSet<String>();
	}
	public void setBookmarkTime(RecoverPointTimeStamp bookmarkTime) {
		_bookmarkTime = bookmarkTime;
	}

	public RecoverPointTimeStamp getBookmarkTime() {
		return _bookmarkTime;
	}

	public void setCGGroupCopyUID(ConsistencyGroupCopyUID cgGroupCopyUID) {
		_cgGroupCopyUID = cgGroupCopyUID;
	}

	public ConsistencyGroupCopyUID getCGGroupCopyUID() {
		return _cgGroupCopyUID;
	}

	
	public String getBookmarkName() {
		return _bookmarkName;
	}

	public void setBookmarkName(String bookmarkName) {
		_bookmarkName = bookmarkName;
	}
	
	public void setProductionCopyUID(ConsistencyGroupCopyUID productionCopyUID) {
		_productionCopyUID = productionCopyUID;
	}

	public ConsistencyGroupCopyUID getProductionCopyUID() {
		return _productionCopyUID;
	}
	
	public Set<String> getWWNSet() {
		return _wwnSet;
	}

	public void setWWNSet(Set<String> wWNSet) {
		_wwnSet = wWNSet;
	}

	public void cloneMe(RPBookmark clone) {
		_bookmarkName = clone._bookmarkName;
		_cgGroupCopyUID = clone._cgGroupCopyUID;
		_productionCopyUID = clone.getProductionCopyUID();
		_bookmarkTime = clone._bookmarkTime;
		_wwnSet = clone._wwnSet;
	}

	@Override
	public String toString() {
	    return _bookmarkName + ":" + _cgGroupCopyUID.getGlobalCopyUID().getCopyUID() + ":" + _bookmarkTime.getTimeInMicroSeconds();
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((_bookmarkTime == null) ? 0 : _bookmarkTime.hashCode());
		result = prime
				* result
				+ ((_cgGroupCopyUID == null) ? 0 : _cgGroupCopyUID.hashCode());
		result = prime * result + ((_bookmarkName == null) ? 0 : _bookmarkName.hashCode());
		result = prime
				* result
				+ ((_productionCopyUID == null) ? 0 : _productionCopyUID
						.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		RPBookmark other = (RPBookmark) obj;
		if (_bookmarkTime == null) {
			if (other._bookmarkTime != null)
				return false;
		} else if (!(_bookmarkTime.getTimeInMicroSeconds() == other._bookmarkTime.getTimeInMicroSeconds()))
			return false;
		if (_bookmarkName == null) {
			if (other._bookmarkName != null)
				return false;
		} else if (!_bookmarkName.equals(other._bookmarkName))
			return false;
		
		boolean cgGroupCopyEqual = false;
		if ((_cgGroupCopyUID.getGlobalCopyUID().getCopyUID() == other._cgGroupCopyUID.getGlobalCopyUID().getCopyUID()) &&
			(_cgGroupCopyUID.getGroupUID().getId() == other._cgGroupCopyUID.getGroupUID().getId()) &&
			(_cgGroupCopyUID.getGlobalCopyUID().getClusterUID().getId() == other._cgGroupCopyUID.getGlobalCopyUID().getClusterUID().getId())) {
			cgGroupCopyEqual = true;
		}
		if (!cgGroupCopyEqual)
			return false;
		
		return true;
	}
}

	
