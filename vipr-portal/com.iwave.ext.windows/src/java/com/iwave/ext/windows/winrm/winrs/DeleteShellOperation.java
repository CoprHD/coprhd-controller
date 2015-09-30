/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import org.w3c.dom.Element;

import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;

/**
 * Deletes the remote shell.
 * 
 * @author jonnymiller
 */
public class DeleteShellOperation extends WinRMInvokeOperation<Void> {
    public DeleteShellOperation(WinRMTarget target, String shellId) {
        super(target, WinRSConstants.WINRS_CMD_URI, WinRSConstants.WINRS_DELETE_URI);
        setSelector(WinRSConstants.SHELL_ID, shellId);
    }

    @Override
    protected String createInput() {
        return "";
    }

    @Override
    protected Void processOutput(Element output) {
        return null;
    }
}
