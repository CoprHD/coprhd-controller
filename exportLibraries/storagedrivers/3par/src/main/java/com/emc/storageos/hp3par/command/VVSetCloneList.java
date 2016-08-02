/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.command;

import java.util.ArrayList;

public class VVSetCloneList {
	
	private String Altered;
	private ArrayList<VVSetVolumeClone> clonesInfo;

	public ArrayList<VVSetVolumeClone> getClonesInfo() {
		return clonesInfo;
	}

	public void setClonesInfo(ArrayList<VVSetVolumeClone> clonesInfo) {
		this.clonesInfo = clonesInfo;
	}

	public class VVSetVolumeClone {

		private String child;
		private String parent;
		private int taskid;

		public String getChild() {
			return child;
		}

		public void setChild(String child) {
			this.child = child;
		}

		public String getParent() {
			return parent;
		}

		public void setParent(String parent) {
			this.parent = parent;
		}

		public int getTaskid() {
			return taskid;
		}

		public void setTaskid(int taskid) {
			this.taskid = taskid;
		}

		public String getValues() {
			return "Parent= "+parent+" Child = "+child+" TaskId = "+taskid;
		}

	}
}