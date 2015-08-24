/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.marshaller;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.json.simple.JSONObject;

import com.emc.storageos.systemservices.impl.logsvc.LogMessage;

public class JSONMarshaller extends Marshaller {

    private boolean firstLog = true;

    private static final byte COMMA = (byte) ',';
    private static final byte COLON = (byte) ':';
    private static final byte TAB = (byte) '\t';
    private static final byte LEFT_CURLY = (byte) '{';
    private static final byte RIGHT_CURLY = (byte) '}';
    private static final byte QUOTE = (byte) '"';

    private static final byte[] CLASS = "class".getBytes();
    private static final byte[] LINE = "line".getBytes();
    private static final byte[] MESSAGE = "message".getBytes();
    private static final byte[] NODE_ID = "node".getBytes();
    private static final byte[] NODE_NAME = "node_name".getBytes();
    private static final byte[] SERVICE = "service".getBytes();
    private static final byte[] SEVERITY = "severity".getBytes();
    private static final byte[] THREAD = "thread".getBytes();
    private static final byte[] TIME = "time".getBytes();
    private static final byte[] TIME_MS = "time_ms".getBytes();

    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    JSONMarshaller(OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void head() throws IOException {
        outputStream.write(LEFT_BRACKET);
    }

    @Override
    public void tail() throws IOException {
        outputStream.write(RETURN);
        outputStream.write(RIGHT_BRACKET);
        outputStream.write(RETURN);
    }

    @Override
    public void marshall(LogMessage log) throws IOException {
        if (firstLog) {
            firstLog = false;
        } else {
            outputStream.write(COMMA);
        }

        final byte[] firstLine = log.getFirstLine();

        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(LEFT_CURLY);
        writeEntry(CLASS, firstLine, log.getFileNameOffset(), log.getFileNameLen(), true);
        outputStream.write(COMMA);
        writeEntry(LINE, firstLine, log.getLineNumberOffset(), log.getLineNumberLen(), false);
        outputStream.write(COMMA);

        // write the log message
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(TAB);
        outputStream.write(QUOTE);
        outputStream.write(MESSAGE);
        outputStream.write(QUOTE);
        outputStream.write(COLON);
        outputStream.write(SPACE);
        outputStream.write(QUOTE);
        escapeJSONChars(log.getLogContent());
        outputStream.write(QUOTE);

        outputStream.write(COMMA);
        writeEntry(NODE_ID, log.getNodeId(), true);
        outputStream.write(COMMA);
        writeEntry(NODE_NAME, log.getNodeName(), true);
        outputStream.write(COMMA);
        writeEntry(SERVICE, log.getService(), true);
        outputStream.write(COMMA);
        writeEntry(SEVERITY, log.getLevel(), true);
        outputStream.write(COMMA);
        writeEntry(THREAD, firstLine, log.getThreadNameOffset(), log.getThreadNameLen(), true);
        outputStream.write(COMMA);
        if (log.getTimeBytesOffset() == -1 || log.getTimeBytesLen() == 0) {
            writeFakeTimeStr(log);
        } else {
            writeEntry(TIME, firstLine, log.getTimeBytesOffset(), log.getTimeBytesLen(), true);
        }
        outputStream.write(COMMA);
        writeEntry(TIME_MS, String.valueOf(log.getTime()).getBytes(), false);
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(RIGHT_CURLY);
    }

    @Override
    public void marshall(String msg, LogMessage prevMsg) throws IOException {
        if (firstLog) {
            firstLog = false;
        } else {
            outputStream.write(COMMA);
        }

        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(LEFT_CURLY);
        writeEntry(MESSAGE, JSONObject.escape(msg).getBytes(), true);
        outputStream.write(COMMA);
        writeEntry(SERVICE, "internal".getBytes(), true);
        outputStream.write(COMMA);
        writeEntry(SEVERITY, "ERROR".getBytes(), true);
        outputStream.write(COMMA);
        writeEntry(TIME, prevMsg == null ? "null".getBytes() : prevMsg.getTimeBytes(), true);
        outputStream.write(COMMA);
        writeEntry(TIME_MS, prevMsg == null ? "null".getBytes() : String.valueOf(prevMsg.getTime()).getBytes(), false);
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(RIGHT_CURLY);
    }

    private void writeEntry(byte[] key, byte[] value, boolean isStr) throws IOException {
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(TAB);
        outputStream.write(QUOTE);
        outputStream.write(key);
        outputStream.write(QUOTE);
        outputStream.write(COLON);
        outputStream.write(SPACE);
        if (isStr) {
            outputStream.write(QUOTE);
        }
        outputStream.write(value);
        if (isStr) {
            outputStream.write(QUOTE);
        }
    }

    private void writeFakeTimeStr(LogMessage log) throws IOException {
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(TAB);
        outputStream.write(QUOTE);
        outputStream.write(TIME);
        outputStream.write(QUOTE);
        outputStream.write(COLON);
        outputStream.write(SPACE);
        outputStream.write(QUOTE);
        outputStream.write(sdf.format(new Date(log.getTime())).getBytes());
        outputStream.write(QUOTE);
    }

    private void writeEntry(byte[] key, byte[] firstLine, int valueOffset, int valueLength,
            boolean isStr) throws IOException {
        outputStream.write(RETURN);
        outputStream.write(TAB);
        outputStream.write(TAB);
        outputStream.write(QUOTE);
        outputStream.write(key);
        outputStream.write(QUOTE);
        outputStream.write(COLON);
        outputStream.write(SPACE);
        if (isStr) {
            outputStream.write(QUOTE);
        }
        if (valueOffset == -1 || valueLength == 0) {
            outputStream.write(isStr ? "null".getBytes() : "-1".getBytes());
        } else {
            outputStream.write(firstLine, valueOffset, valueLength);
        }
        if (isStr) {
            outputStream.write(QUOTE);
        }
    }

    /**
     * Escape the ", / and \ char from JSON string
     * 
     * @param msg
     * @throws IOException
     */
    private void escapeJSONChars(byte[] msg) throws IOException {
        int mark = 0;
        for (int i = 0; i < msg.length; i++) {
            if (msg[i] == (byte) '"' || msg[i] == (byte) '\\' || msg[i] == (byte) '/') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                mark = i;
            } else if (msg[i] == (byte) '\r') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                outputStream.write((char) 'r');
                mark = i + 1;
            } else if (msg[i] == (byte) '\n') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                outputStream.write((char) 'n');
                mark = i + 1;
            } else if (msg[i] == (byte) '\b') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                outputStream.write((char) 'b');
                mark = i + 1;
            } else if (msg[i] == (byte) '\f') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                outputStream.write((char) 'f');
                mark = i + 1;
            } else if (msg[i] == (byte) '\t') {
                outputStream.write(msg, mark, i - mark);
                outputStream.write((char) '\\');
                outputStream.write((char) 't');
                mark = i + 1;
            }
        }
        if (mark == msg.length) {
            return;
        }
        outputStream.write(msg, mark, msg.length - mark);
    }
}
