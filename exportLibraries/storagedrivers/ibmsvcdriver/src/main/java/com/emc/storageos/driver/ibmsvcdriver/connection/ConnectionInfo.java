/**
 * Copyright (c) 2016 EMC Corporation
 * 
 * All Rights Reserved
 * 
 */
package com.emc.storageos.driver.ibmsvcdriver.connection;

public class ConnectionInfo {

    // The host to which the connection is made.
    private String _hostname;
    
    // The port for the connection.
    private int _port;
    
    // The username for the connection.
    private String _username;

    // The password for the connection.
    private String _password;

    // The namespace for the connection.
    private String _interopNS;

    // Whether or not SSL is used for the CIM connection.
    private boolean _useSSL;

    public ConnectionInfo(String _hostname, int _port, String _username, String _password) {
        super();
        this._hostname = _hostname;
        this._port = _port;
        this._username = _username;
        this._password = _password;
    }

    public ConnectionInfo(String _hostname, int _port, String _username, String _password, String _interopNS,
            boolean _useSSL) {
        super();
        this._hostname = _hostname;
        this._port = _port;
        this._username = _username;
        this._password = _password;
        this._interopNS = _interopNS;
        this._useSSL = _useSSL;
    }

    public String get_hostname() {
        return _hostname;
    }

    public void set_hostname(String _hostname) {
        this._hostname = _hostname;
    }

    public int get_port() {
        return _port;
    }

    public void set_port(int _port) {
        this._port = _port;
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

    public String get_interopNS() {
        return _interopNS;
    }

    public void set_interopNS(String _interopNS) {
        this._interopNS = _interopNS;
    }

    public boolean is_useSSL() {
        return _useSSL;
    }

    public void set_useSSL(boolean _useSSL) {
        this._useSSL = _useSSL;
    }

}
