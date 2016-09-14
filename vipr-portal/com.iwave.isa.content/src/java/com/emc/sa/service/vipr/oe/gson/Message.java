package com.emc.sa.service.vipr.oe.gson;

public class Message {

	String message;
	String id;
	
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public boolean isValid() {
		return getMessage() != null && getId() != null;
	}
	
}
