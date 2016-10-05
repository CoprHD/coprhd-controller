package com.emc.ctd.vipr.workflow.tasks;



public interface ExternalTaskApdapterInterface {

	
	public void init() throws Exception ;

	public void precheck() throws Exception ;


	public void preLaunch(String extenalTaskParam) throws Exception ;

	public TaskInfo executeExternal(String extenalTaskParam) throws Exception ;

	public void postcheck() throws Exception ;

	public void postLaunch(String extenalTaskParam) throws Exception;

	public void getStatus() throws Exception ;

	public void destroy() ;

}
