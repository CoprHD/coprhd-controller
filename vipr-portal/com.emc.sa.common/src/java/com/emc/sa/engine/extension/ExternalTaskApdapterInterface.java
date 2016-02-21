package com.emc.sa.engine.extension;

import com.emc.storageos.vasa.async.TaskInfo;

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
