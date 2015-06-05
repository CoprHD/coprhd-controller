/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.systemservices.impl.jobs.backupscheduler;

import com.emc.storageos.coordinator.client.model.ProductName;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Class to manage backup file names (tags) using by Backup Scheduler
 */
public class ScheduledBackupTag {
    private static final String DATE_PATTERN = "yyyyMMddHHmmss";
    private static final ThreadLocal<SimpleDateFormat> dateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DATE_PATTERN);
        }
    };
    private static final Date MIN_DATE = new Date(0);
    private static final String ZIP_FILE_SURFIX = ".zip";

    public static Date parseTimestamp(String timestampStr) throws ParseException {
        return dateFormat.get().parse(timestampStr);
    }

    public static String toTimestamp(Date dt) {
        return dateFormat.get().format(dt);
    }

    public static String toBackupTag(Date dt, String ver, int nodeCount) {
        String timestamp = toTimestamp(dt);
        return String.format("%s-%s-%d-%s", ProductName.getName(), ver, nodeCount, timestamp);
    }

    public static Date parseBackupTag(String tag) throws ParseException {
        return parseTimestamp(tag.substring(tag.length() - DATE_PATTERN.length()));
    }

    public static List<String> pickScheduledBackupTags(Collection<String> tags) {
        ArrayList<String> scheduledTags = new ArrayList<>();
        StringBuilder backupNamePatternString = new StringBuilder();
        // Typically, this pattern String could match all tags produced by toBackupTag method
        // also in consideration of extension, version part could be longer and node count could bigger
        backupNamePatternString.append('^').append(ProductName.getName())
        .append("-(\\d+\\.)*\\d+-\\d+-\\d{").append(DATE_PATTERN.length()).append("}$");
        Pattern backupNamePattern = Pattern.compile(backupNamePatternString.toString());
        for (String tag : tags) {
            if(backupNamePattern.matcher(tag).find()) {
                scheduledTags.add(tag);
            }
        }

        return scheduledTags;
    }

    public static class TagComparator implements Comparator<String> {
        private Date parseTagFallback(String tag) {
            try {
                return ScheduledBackupTag.parseBackupTag(tag);
            } catch (ParseException e) {
                return MIN_DATE;
            }
        }

        @Override
        public int compare(String o1, String o2) {
            Date d1 = parseTagFallback(o1);
            Date d2 = parseTagFallback(o2);

            int ret = d1.compareTo(d2);
            return ret != 0 ? ret : o1.compareTo(o2);
        }
    }

    public static String toZipFileName(String tag, int nodeMask) {
        return String.format("%s-%s%s", tag, Integer.toHexString(nodeMask), ZIP_FILE_SURFIX);
    }
}
