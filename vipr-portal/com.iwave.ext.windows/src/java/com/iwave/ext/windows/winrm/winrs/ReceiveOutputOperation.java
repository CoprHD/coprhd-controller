/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.windows.winrm.winrs;

import javax.xml.xpath.XPathExpression;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.text.StrBuilder;
import org.w3c.dom.Element;

import com.iwave.ext.windows.winrm.WinRMInvokeOperation;
import com.iwave.ext.windows.winrm.WinRMTarget;
import com.iwave.ext.xml.XmlStringBuilder;
import com.iwave.ext.xml.XmlUtils;

/**
 * Receives a portion of the output of the remote command. The result of this operation is the
 * received data. This may need to be invoked multiple times to receive all output.
 * 
 * @author jonnymiller
 */
public class ReceiveOutputOperation extends WinRMInvokeOperation<ReceiveData> {
    private String commandId;
    private int sequenceId;

    public ReceiveOutputOperation(WinRMTarget target, String shellId, String commandId,
            int sequenceId) {
        super(target, WinRSConstants.WINRS_CMD_URI, WinRSConstants.WINRS_RECEIVE_URI);
        setSelector(WinRSConstants.SHELL_ID, shellId);
        this.commandId = commandId;
        this.sequenceId = sequenceId;
    }

    @Override
    protected String createInput() {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.start("Receive").attr("xmlns", WinRSConstants.WINRS_URI)
                .attr("SequenceId", String.valueOf(sequenceId));
        xml.start("DesiredStream").attr("CommandId", commandId).text("stdout stderr").end();
        xml.end();
        return xml.toString();
    }

    @Override
    protected ReceiveData processOutput(Element output) {
        ReceiveData data = new ReceiveData();
        data.setStdout(readStream(WinRSConstants.STDOUT, output));
        data.setStderr(readStream(WinRSConstants.STDERR, output));
        data.setCommandState(XmlUtils.selectText(WinRSConstants.COMMAND_STATE, output));
        String exitCode = XmlUtils.selectText(WinRSConstants.EXIT_CODE, output);
        if (StringUtils.isNotBlank(exitCode)) {
            long value = Long.parseLong(exitCode);
            data.setExitCode((int) value);
        }
        return data;
    }

    protected String readStream(XPathExpression expr, Element output) {
        StrBuilder stream = new StrBuilder();
        for (Element stderr : XmlUtils.selectElements(expr, output)) {
            String text = XmlUtils.getText(stderr);
            if (text != null) {
                byte[] data = Base64.decodeBase64(text);
                stream.append(new String(data));
            }
        }
        return stream.toString();
    }
}
