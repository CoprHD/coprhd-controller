/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.connection;

public class ConnectionInfo {
    // management interface IP address
    private String _ipAddress;

    // management port number
    private Integer _portNumber;

    // management interface user
    private String _username;

    // management interface password
    private String _password;

    // Whether or not SSL is used for connection
    private boolean _useSSL;

    public ConnectionInfo(String _ipAddress, Integer _portNumber, String _username, String _password) {
    	this._ipAddress = _ipAddress; 
    	this._portNumber = _portNumber;
    	this._username = _username;
    	this._password = _password;
    }

	public String get_ipAddress() {
		return _ipAddress;
	}

	public void set_ipAddress(String _ipAddress) {
		this._ipAddress = _ipAddress;
	}

	public Integer get_portNumber() {
		return _portNumber;
	}

	public void set_portNumber(Integer _portNumber) {
		this._portNumber = _portNumber;
	}

	public String get_username() {
		return _username;
	}

	public void set_username(String _username) {
		this._username = _username;
	}

	public String get_password() {
		return _password;
	}

	public void set_password(String _password) {
		this._password = _password;
	}

	public boolean is_useSSL() {
		return _useSSL;
	}

	public void set_useSSL(boolean _useSSL) {
		this._useSSL = _useSSL;
	}  
}
