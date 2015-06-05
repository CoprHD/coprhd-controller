/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.aix.command;

/**
 * Executes an 'echo' command to add an entry to the /etc/filesystems file. Commands are in the format:
 * 
 * 
 */
public class AddToFilesystemsCommand extends AixCommand {
	
    public AddToFilesystemsCommand() {
        setCommand("echo");
        setRunAsRoot(true);
    }
    
    private String newline(){
    	return "\\n";
    }

    public void setOptions(String device, String mountPoint, String fsType) {
        StringBuilder sb = new StringBuilder();

        sb.append(newline()).append(mountPoint).append(":");
        sb.append(newline()).append("\\t").append("dev\\t\\t= ").append(device);
        sb.append(newline()).append("\\t").append("vfs\\t\\t= ").append(fsType);
        sb.append(newline()).append("\\t").append("mount\\t\\t= ").append("true");
        sb.append(newline()).append("\\t").append("log\\t\\t= ").append("INLINE");
        sb.append(newline()).append("\\t").append("account\\t\\t= ").append("false");

        addArguments("'"+sb.toString()+"'", ">>", "/etc/filesystems");
    }

}
