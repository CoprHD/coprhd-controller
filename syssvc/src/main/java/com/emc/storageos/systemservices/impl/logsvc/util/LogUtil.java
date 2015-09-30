/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorInputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.systemservices.impl.logsvc.LogConstants;

// NonInstantiable utility class
public class LogUtil {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogUtil.class);

    // suppress default constructor for noninstantiability
    private LogUtil() {
        throw new AssertionError("This class should not be instantiated");
    }

    /**
     * Find files with regex matching and time range matching return a list of files sorted by last
     * modification time.
     * 
     * @param files
     * @param start
     * @param end
     * @return
     * @throws IOException
     */
    public static List<String> fileNameWildCardSearch(List<File> files, Date start,
            Date end) throws IOException {
        List<String> nameList = new ArrayList<>();

        for (File f : files) {
            logger.debug("file path: " + f.getAbsolutePath());
        }
        if (!files.isEmpty()) {
            Collections.sort(files, new LogFileComparator());
            for (File file : files) {
                if (fileInTimeRange(file, start, end) < 0) {
                    // too early
                    continue;
                } else if ((fileInTimeRange(file, start, end) == 0)) {
                    // good fit
                    String name = file.getCanonicalPath();
                    nameList.add(name);
                } else {
                    // filed last modified later than end time filter,
                    // still possible has logs earlier than start time filter
                    String name = file.getCanonicalPath();
                    nameList.add(name);
                    break;
                }
            }
        }
        logger.debug("after filter fileNames {}", nameList);
        return nameList;
    }

    /**
     * Check if file is modified during that period
     * 
     * @param f
     * @param start
     * @param end
     * @return
     */
    public static int fileInTimeRange(File f, Date start, Date end) {
        long time = getFileTime(f);
        if (start != null && time < start.getTime()) {
            return -1;
        }
        if (end != null && time > end.getTime()) {
            return 1;
        }
        return 0;
    }

    private static long getFileTime(File file) {
        String fileName = file.getName();
        String[] fields = fileName.split("\\.");
        if (fields.length == 4) {
            String time = fields[2];
            int year = Integer.parseInt(time.substring(0, 4));
            int month = Integer.parseInt(time.substring(4, 6));
            int date = Integer.parseInt(time.substring(6, 8));
            int hour = Integer.parseInt(time.substring(9, 11));
            int minute = Integer.parseInt(time.substring(11, 13));
            int second = Integer.parseInt(time.substring(13, 15));
            Calendar calendar = Calendar.getInstance();
            calendar.set(year, month - 1, date, hour, minute, second);
            return calendar.getTime().getTime();
        } else {
            return file.lastModified();
        }

    }

    /**
     * Test if current log count is fewer than max count limit
     * However, if the current log has the same timestamp with the previous log,
     * we shouldn't stop even if max count limit has been exceeded.
     * The upper layer component will decide if the last batch of logs should be discarded
     * as a whole.
     * 
     * This method is used by classes that read one log each time, e.g. LogFileStream,
     * LogStreamMerger
     * 
     * @param maxCount
     * @param logCount logCount INCLUDING the current log
     * @param currentLogTime
     * @param prevLogTime
     * @return whether the current log should be returned to the upper layer.
     */
    public static boolean permitCurrentLog(long maxCount, long logCount, long currentLogTime,
            long prevLogTime) {
        if (maxCount == 0) {
            return true;
        } else if (currentLogTime == prevLogTime) {
            return true;
        }
        // the current log has a new timestamp
        // be careful though it might be a start of new batch
        return logCount <= maxCount;
    }

    /**
     * Whether next batch of logs should be accepted according to maxCount and MAXCOUNT_OVERFLOW.
     * A batch of logs have the same timestamp.
     * 
     * @param maxCount
     * @param currentLogSize logCount EXCLUDING the size of the next log batch
     * @param nextBatchSize
     * @return
     */

    public static boolean permitNextLogBatch(long maxCount, long currentLogSize, int nextBatchSize) {
        if (maxCount == 0) {
            // no maxCount limit
            return true;
        } else if (nextBatchSize == 1) {
            // next timestamp is unique, strictly follow the maxCount limit
            return currentLogSize < maxCount;
        } else {
            // either within MAXCOUNT_OVERFLOW, or there's only one timestamp
            return (currentLogSize + nextBatchSize <= maxCount + LogConstants.MAXCOUNT_OVERFLOW) ||
                    (currentLogSize == 0);
        }
    }

    // Earlier than start date reutrn -1;
    // later than end date return 1;
    // in range return 0
    public static int timeInRange(Date d, Date start, Date end) {
        int inRange = 0;
        if (start != null) {
            if (d.before(start)) {
                return -1;
            }
        }
        if (end != null) {
            if (d.after(end)) {
                return 1;
            }
        }
        return inRange;
    }

    /**
     * This compressed file utility can generate a reader to read zip(bz2/xz/gzip) streams.
     * 
     * @param fileIn
     * @return
     * @throws FileNotFoundException
     * @throws CompressorException
     */
    public static BufferedReader getBufferedReaderForBZ2File(String fileIn)
            throws FileNotFoundException, CompressorException {
        CompressorStreamFactory factory = new CompressorStreamFactory();

        FileInputStream fin = new FileInputStream(fileIn);
        BufferedInputStream bis = new BufferedInputStream(fin);
        CompressorInputStream input = factory.createCompressorInputStream(bis);

        BufferedReader br = new BufferedReader(new InputStreamReader(input));

        return br;
    }

    /**
     * Check for a compressed file extension - for now, .bz2, .xz and .gz
     * 
     * @param file
     * @return
     */
    public static boolean logFileZipped(String file) {
        return file.endsWith(".bz2") || file.endsWith(".gz") || file.endsWith(".xz");// TODO: have a list
    }

    public static byte[] stringToBytes(String str) {
        if (str == null) {
            return null;
        }
        return str.getBytes();
    }

    public static String bytesToString(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes);
    }

    private static Map<String, byte[]> nodeIdByteMap = new HashMap<>();

    public static byte[] nodeIdToBytes(String nodeId) {
        byte[] bytes = nodeIdByteMap.get(nodeId);
        if (bytes == null) {
            bytes = nodeId.getBytes();
            nodeIdByteMap.put(nodeId, bytes);
        }
        return bytes;
    }

    private static Map<String, byte[]> nodeNameByteMap = new HashMap<>();

    public static byte[] nodeNameToBytes(String nodeName) {
        byte[] bytes = nodeNameByteMap.get(nodeName);
        if (bytes == null) {
            bytes = nodeName.getBytes();
            nodeNameByteMap.put(nodeName, bytes);
            logger.info("Added "+nodeName+" to bytemap :"+nodeIdByteMap.keySet().toString());
        }
        return bytes;
    }

    private static Map<String, byte[]> serviceByteMap = new HashMap<>();

    public static byte[] serviceToBytes(String service) {
        byte[] bytes = serviceByteMap.get(service);
        if (bytes == null) {
            bytes = service.getBytes();
            serviceByteMap.put(service, bytes);
        }
        return bytes;
    }

    /**
     * Compare file by date
     * 
     * @author luoq1
     * 
     */
    static class LogFileComparator implements Comparator<File> {

        @Override
        public int compare(File f1, File f2) {
            if (getFileTime(f1) > getFileTime(f2)) {
                return 1;
            } else if (getFileTime(f1) < getFileTime(f2)) {
                return -1;
            }
            return 0;
        }
    }

    public static byte[] escapeXml(byte[] input) {
        return escapeXml(bytesToString(input));
    }

    public static byte[] escapeXml(String input) {
        return stringToBytes(StringEscapeUtils.escapeXml(input));
    }
}
