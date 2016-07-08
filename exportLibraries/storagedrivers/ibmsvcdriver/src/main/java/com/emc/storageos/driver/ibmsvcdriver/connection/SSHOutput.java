package com.emc.storageos.driver.ibmsvcdriver.connection;

import java.io.Serializable;

/**
 * Output data from an SSH execution.
 *  
 */
public class SSHOutput implements Serializable {

    private static final long serialVersionUID = 6792754366832579447L;
    private int exitValue;
    private String stderr;
    private String stdout;
    
    public SSHOutput() { }
    
    public SSHOutput(String stdout, String stderr, int exitValue) {
        this.stdout = stdout;
        this.stderr = stderr;
        this.exitValue = exitValue;
    }
    
    public int getExitValue() {
        return exitValue;
    }
    
    public void setExitValue(int exitValue) {
        this.exitValue = exitValue;
    }
    
    public String getStderr() {
        return stderr;
    }
    
    public void setStderr(String stderr) {
        this.stderr = stderr;
    }
    
    public String getStdout() {
        return stdout;
    }
    
    public void setStdout(String stdout) {
        this.stdout = stdout;
    }
}
