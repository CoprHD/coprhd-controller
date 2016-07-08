package com.emc.storageos.driver.ibmsvcdriver.connection;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public class SSHUserInfo implements UserInfo, UIKeyboardInteractive {

    private String password;

    public SSHUserInfo(String password) {
        this.password = password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    public String getPassphrase() {
        return null;
    }

    public boolean promptPassphrase(String message) {
        return false;
    }

    public boolean promptPassword(String message) {
        return true;
    }

    public boolean promptYesNo(String message) {
        return true;
    }

    public void showMessage(String message) {

    }

    @Override
    public String[] promptKeyboardInteractive(String destination, String name, String instruction,
            String[] prompt, boolean[] echo) {
        if ((prompt.length != 1) || (echo[0] != false) || (this.password == null)) {
            return null;
        }
        String[] response = new String[1];
        response[0] = this.password;
        return response;
    }
}

