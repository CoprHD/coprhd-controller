package com.emc.storageosplugin.model.vce;

public class OperationResult {

	private boolean isSuccess;
	private String msg;
	
	public boolean isSuccess() {
		return isSuccess;
	}
	public void setSuccess(boolean isSuccess) {
		this.isSuccess = isSuccess;
	}
	public String getMsg() {
		return msg;
	}
	public void setMsg(String msg) {
		this.msg = msg;
	}
	
	@Override
	public String toString() {
		return "OperationResult [isSuccess=" + isSuccess + ", msg=" + msg + "]";
	}
	
	
	
	

}
