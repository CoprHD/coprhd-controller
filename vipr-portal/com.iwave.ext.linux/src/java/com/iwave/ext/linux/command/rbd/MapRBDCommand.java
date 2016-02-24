/*
 * Copyright (c) 2012-2015 EMC Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.rbd;


import java.io.IOException;

import org.apache.commons.io.IOUtils;

import com.iwave.ext.linux.command.LinuxResultsCommand;


public class MapRBDCommand extends LinuxResultsCommand<String> {
	private String _monitors;
	private String _user;
	private String _key;
	private String _template;

	public MapRBDCommand(String monitors, String user, String key) {
		_monitors = monitors;
		_user = user;
		_key = key;
		try {
            _template = IOUtils.toString(getClass().getResourceAsStream("map.sh"));
        } catch (IOException e) {
        }
        setRunAsRoot(true);
    }

    public void setVolume(String pool, String volume, String snapshot) {
    	if (snapshot == null || snapshot.isEmpty())
    		snapshot = "-";
        String cmd = String.format(_template, _monitors, _user, _key, pool, volume, snapshot);
        setCommand(cmd);
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        results = stdout.trim();
    }
}
