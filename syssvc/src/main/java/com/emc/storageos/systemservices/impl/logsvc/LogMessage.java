/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.emc.vipr.model.sys.logging.LogSeverity;

// Suppress the following two sonar warnings. Passing byte arrays directly for performance considerations
@SuppressWarnings({ "pmd:ArrayIsStoredDirectly", "pmd:MethodReturnsInternalArray" })
public class LogMessage {

    private Status status;
    private static final byte[] NULL_BYTES = "null".getBytes();

    // nodeId and service are not set in the parser, and nodeId doesn't need to be
    // serialized in LogNetworkWriter.
    private byte[] nodeId;// node id

    // nodeName and service are not set in the parser, and nodeName doesn't need to be
    // serialized in LogNetworkWriter.
    private byte[] nodeName;// node name

    // the following fields need to be serialized in LogNetworkWriter
    private byte[] service;
    private long time;
    // class file name
    // the following fields should be retrieved from firstLine
    private int fileNameOffset = -1;
    private int fileNameLen;
    // running thread
    private int threadNameOffset = -1;
    private int threadNameLen;
    private int level = -1;
    private int lineNumberOffset = -1;
    private int lineNumberLen;
    private int timeBytesOffset = -1;
    private int timeBytesLen;
    private int logOffset = -1;

    private byte[] firstLine;
    private List<byte[]> followingLines;

    enum Status {
        ACCEPTED, CONTINUATION, REJECTED, REJECTED_LAST, HEADER
    }

    public final static LogMessage CONTINUATION_LOGMESSAGE = new LogMessage(Status.CONTINUATION);
    public final static LogMessage REJECTED_LOGMESSAGE = new LogMessage(Status.REJECTED);
    public final static LogMessage REJECTED_LAST_LOGMESSAGE = new LogMessage(Status.REJECTED_LAST);

    public boolean isContinuation() {
        return status == Status.CONTINUATION;
    }

    public boolean isRejected() {
        return status == Status.REJECTED;
    }

    public boolean isRejectedLast() {
        return status == Status.REJECTED_LAST;
    }

    public boolean isHeader() {
        return status == Status.HEADER;
    }

    private LogMessage(Status status) {
        this.status = status;
    }

    private LogMessage() {
    }

    public LogMessage(long date, byte[] firstline) {
        this.status = Status.ACCEPTED;
        this.firstLine = firstline;
        this.time = date;
    }

    public static LogMessage makeHeaderLog(long time) {
        LogMessage log = new LogMessage(Status.HEADER);
        log.setLogOffset(0);
        log.setTime(time);
        return log;
    }

    public byte[] getNodeId() {
        if (nodeId == null) {
            return NULL_BYTES;
        }

        return nodeId;
    }

    public void setNodeId(byte[] nodeId) {
        this.nodeId = nodeId;
    }

    public byte[] getNodeName() {
        if (nodeName == null)
            return NULL_BYTES;

        return nodeName;
    }

    public void setNodeName(byte[] nodeName) {
        this.nodeName = nodeName;
    }

    public void setService(byte[] service) {
        this.service = service;
    }

    public byte[] getService() {
        if (service == null) {
            return NULL_BYTES;
        }

        return this.service;
    }

    public void setTimeBytes(int offset, int len) {
        timeBytesOffset = offset;
        timeBytesLen = len;
    }

    public int getTimeBytesOffset() {
        return timeBytesOffset;
    }

    public int getTimeBytesLen() {
        return timeBytesLen;
    }

    public void setThreadName(int offset, int len) {
        threadNameOffset = offset;
        threadNameLen = len;
    }

    public int getThreadNameOffset() {
        return threadNameOffset;
    }

    public int getThreadNameLen() {
        return threadNameLen;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setFileName(int offset, int len) {
        fileNameOffset = offset;
        fileNameLen = len;
    }

    public int getFileNameOffset() {
        return fileNameOffset;
    }

    public int getFileNameLen() {
        return fileNameLen;
    }

    public void setLineNumber(int offset, int len) {
        lineNumberOffset = offset;
        lineNumberLen = len;
    }

    public int getLineNumberOffset() {
        return lineNumberOffset;
    }

    public int getLineNumberLen() {
        return lineNumberLen;
    }

    public void setLogOffset(int offset) {
        logOffset = offset;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long t) {
        time = t;
    }

    public byte[] getFileName() {
        if (fileNameOffset == -1 || fileNameLen == 0) {
            return NULL_BYTES;
        }

        return Arrays.copyOfRange(firstLine, fileNameOffset, fileNameOffset + fileNameLen);
    }

    public byte[] getThreadName() {
        if (threadNameOffset == -1 || threadNameLen == 0) {
            return NULL_BYTES;
        }

        return Arrays.copyOfRange(firstLine, threadNameOffset, threadNameOffset +
                threadNameLen);
    }

    public byte[] getLevel() {
        if (level < 0 || level >= LogSeverity.MAX_LEVEL) {
            return NULL_BYTES;
        }

        return LogSeverity.values()[level].name().getBytes();
    }

    public byte[] getLineNumber() {
        if (lineNumberOffset == -1 || lineNumberLen == 0) {
            return "-1".getBytes();
        }

        return Arrays.copyOfRange(firstLine, lineNumberOffset, lineNumberOffset +
                lineNumberLen);
    }

    public byte[] getTimeBytes() {
        if (timeBytesOffset == -1 || timeBytesLen == 0) {
            return NULL_BYTES;
        }

        return Arrays.copyOfRange(firstLine, timeBytesOffset, timeBytesOffset +
                timeBytesLen);
    }

    public byte[] getRawLogContent() {
        return getLogContent(0);
    }

    public byte[] getLogContent() {
        return getLogContent(logOffset);
    }

    private byte[] getLogContent(int offset) {
        int sum = 0;

        if (firstLine != null) {
            sum = firstLine.length - offset;
        }
        if (followingLines != null && !followingLines.isEmpty()) {
            for (byte[] line : followingLines) {
                // trailing \n
                sum += line.length + 1;
            }
        }

        byte[] result = new byte[sum];

        if (firstLine != null && offset < firstLine.length) {
            sum = firstLine.length - offset;
            System.arraycopy(firstLine, offset, result, 0, firstLine.length - offset);
        } else {
            sum = 0;
        }
        if (followingLines != null && !followingLines.isEmpty()) {
            for (byte[] line : followingLines) {
                result[sum++] = (byte) '\n';
                System.arraycopy(line, 0, result, sum, line.length);
                sum += line.length;
            }
        }
        return result;
    }

    public void setFirstLine(byte[] firstLine) {
        this.firstLine = firstLine;
    }

    public byte[] getFirstLine() {
        return firstLine;
    }

    public void appendMessage(byte[] msg) {
        if (msg == null || msg.length == 0) {
            return;
        }

        if (followingLines == null) {
            followingLines = new ArrayList<>();
        }

        followingLines.add(msg);
    }

    /**
     * Write the LogMessage object to a DataOutputStream
     * The serialized form of the object is:
     * =============START==============
     * service length (byte)
     * service (byte[])
     * file name offset (short)
     * file name length (short)
     * thread name offset (short)
     * thread name length (short)
     * level (byte)
     * line number offset (short)
     * line number length (byte)
     * time (long)
     * timeBytes offset (short)
     * timeBytes length (byte)
     * message offset (short)
     * message length (int)
     * message (byte[])
     * ==============END===============
     * 
     * Note that node id is set in LogNetworkReader, which is above network serialization.
     * 
     * @param outputStream
     */
    public void write(final DataOutputStream outputStream) throws IOException {
        // 1 service
        if (service != null && service.length != 0) {
            outputStream.write((byte) service.length);
            outputStream.write(service);
        } else {
            outputStream.write((byte) 0);
        }

        // 2 file name
        outputStream.writeShort((short) fileNameOffset);
        outputStream.writeShort((short) fileNameLen);

        // 3 thread name
        outputStream.writeShort((short) threadNameOffset);
        outputStream.writeShort((short) threadNameLen);

        // 4 level value
        outputStream.write((byte) level);

        // 5 lineNumber
        outputStream.writeShort((short) lineNumberOffset);
        outputStream.write((byte) lineNumberLen);

        // 6 time
        outputStream.writeLong(time);

        // 7 timeBytes
        outputStream.writeShort((short) timeBytesOffset);
        outputStream.write((byte) timeBytesLen);

        // 8 msg combined together
        outputStream.writeShort((short) logOffset);
        byte[] combinedMsg = getRawLogContent();
        if (combinedMsg != null && combinedMsg.length != 0) {
            outputStream.writeInt(combinedMsg.length);
            outputStream.write(combinedMsg);
        } else {
            outputStream.writeInt(0);
        }
    }

    /**
     * read object back from buffer
     * refer to the serialization format in the write method.
     * 
     * @param inputStream
     * @return
     */
    public static LogMessage read(final DataInputStream inputStream) throws IOException {
        LogMessage entry = new LogMessage();

        // 1 service
        int svclen = inputStream.read();
        if (svclen > 0) {
            byte[] svcArr = new byte[svclen];
            inputStream.readFully(svcArr);
            entry.setService(svcArr);
        }

        // 2 file name
        int fnameOffset = inputStream.readShort();
        int fnameLen = inputStream.readShort();
        entry.setFileName(fnameOffset, fnameLen);

        // 3 thread name
        int tnameOffset = inputStream.readShort();
        int tnameLen = inputStream.readShort();
        entry.setThreadName(tnameOffset, tnameLen);

        // 4 level value
        entry.setLevel(inputStream.read());

        // 5 lineNumber
        int lineNoOffset = inputStream.readShort();
        int lineNoLen = inputStream.read();
        entry.setLineNumber(lineNoOffset, lineNoLen);

        // 6 time
        entry.setTime(inputStream.readLong());

        // 7 timeBytes
        int timeOffset = inputStream.readShort();
        int timeLen = inputStream.read();
        entry.setTimeBytes(timeOffset, timeLen);

        // 8 msg, all saved to the firstline
        entry.setLogOffset(inputStream.readShort());
        int msglen = inputStream.readInt();
        if (msglen > 0) {
            byte[] msgArr = new byte[msglen];
            inputStream.readFully(msgArr);
            entry.setFirstLine(msgArr);
        }

        return entry;
    }

    public String toStringForTest() {
        return "[" + getThreadName() + "]" + " " + getLevel() + " "
                + getFileName() + " " + getLineNumber() + " " + getLogContent();
    }

    /**
     * Return original format
     * Attention: not exactly same. The begging tab is different
     * when first line firstLine is "", then returned string's first line firstLine is
     * the second line content if applicable
     * 
     * @return
     */
    public String toStringOriginalFormat() {
        StringBuilder sb = new StringBuilder();

        sb.append(getTimeBytes()).append(" ").append("[").append(getThreadName()).append("]")
                .append(" ").append(getLevel()).append(" ")
                .append(getFileName()).append(" ").append("(line ").append(getLineNumber())
                .append(") ").append(" service ").append(getService()).append(" ")
                .append(getLogContent());
        return sb.toString();
    }

    /**
     * Return original format
     * Attention: not exactly same. The begging tab is different
     * when first line firstLine is "", then returned string's first line firstLine is
     * the second line content if applicable
     * 
     * @return
     */
    public String toStringOriginalFormatSysLog() {
        StringBuilder sb = new StringBuilder();

        sb.append(getTimeBytes()).append(" ").append("[").append(getThreadName()).append("]")
                .append(" ").append(getLevel()).append(" ").append(getLogContent());
        return sb.toString();
    }
}
