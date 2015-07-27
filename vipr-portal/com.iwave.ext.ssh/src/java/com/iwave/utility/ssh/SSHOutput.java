/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.utility.ssh;

import java.io.Serializable;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * Output data from an SSH execution.
 *  
 * @author CDail
 */
@XmlRootElement()
public class SSHOutput implements Serializable {
	private static final long serialVersionUID = 3489876410514339657L;
	
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
