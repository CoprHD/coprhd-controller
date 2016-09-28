package com.emc.ctd.workflow.view.bpmn;

public class WorkflowKernel {
	
	public static final String UNIQUEID = "$uniqueid";
	public static final String MODELVERSION = "$modelversion";
	public static final String PROCESSID = "$processid";
	public static final String ACTIVITYID = "$activityid";
	public static final String ACTIVITYIDLIST = "$activityidlist";
	public static final String TYPE = "type";
	
	
	public static String generateUniqueID() {
		String sIDPart1 = Long.toHexString(System.currentTimeMillis());
		double d = Math.random() * 900000000;
		int i = new Double(d).intValue();
		String sIDPart2 = Integer.toHexString(i);
		String id = sIDPart1 + "-" + sIDPart2;

		return id;
	}

}
