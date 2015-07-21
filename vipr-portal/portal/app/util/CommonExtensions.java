/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;

import play.Play;
import play.i18n.Lang;
import play.templates.JavaExtensions;

import com.emc.sa.util.SizeUtils;
import com.emc.sa.util.TextUtils;
import com.emc.storageos.model.DataObjectRestRep;

public class CommonExtensions extends JavaExtensions {
    private static final String ISA_NAMESPACE = "isa";

    public static String htmlId(Object o) {
        if (o != null) {
            return htmlId(o.toString());
        }
        return "";
    }

    public static String htmlId(String s) {
        if (StringUtils.isNotBlank(s)) {
            return s.replaceAll("[^\\w]", "_");
        }
        return "";
    }

    public static String abbreviate(String s, int maxWidth) {
        return StringUtils.abbreviate(s, maxWidth);
    }

    public static String formatRelative(Date d) {
        return formatRelative(d, new Date());
    }

    public static String formatRelative(Date d, Date rel) {
        Calendar c1 = Calendar.getInstance();
        c1.setTime(d);
        Calendar c2 = Calendar.getInstance();
        c2.setTime(rel);

        if (DateUtils.isSameDay(c1, c2)) {
            return format(d, MessagesUtils.get("formatRelative.sameDay"));
        }
        if (c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)) {
            return format(d, MessagesUtils.get("formatRelative.sameYear"));
        }
        else {
            return format(d, MessagesUtils.get("formatRelative.format"));
        }
    }

    public static String formatTime(Date d) {
        String localTimeFormat = Play.configuration.getProperty("time.format." + Lang.get());
        if (StringUtils.isNotEmpty(localTimeFormat)) {
            return new SimpleDateFormat(localTimeFormat, Lang.getLocale()).format(d);
        }
        String globalTimeFormat = Play.configuration.getProperty("time.format");
        if (StringUtils.isNotEmpty(globalTimeFormat)) {
            return new SimpleDateFormat(globalTimeFormat).format(d);
        }
        return new SimpleDateFormat("dd-MM-YYYY HH:mm:ss").format(d);
    }

    public static String elapsed(Date end, Date start) {
        if ((end == null) || (start == null) || end.before(start)) {
            return "";
        }
        return elapsed(end.getTime() - start.getTime());
    }

    public static String elapsed(Double millis) {
        return elapsed(millis.longValue());
    }

    public static String elapsed(Long millis) {
        if (millis < 1000) {
            return MessagesUtils.get("elapsed.minimum");
        }
        long delta = millis / 1000;
        if (delta < 60) {
            return MessagesUtils.get("elapsed.seconds", delta, pluralize(delta));
        }
        if (delta < 60 * 60) {
            long minutes = delta / 60;
            return MessagesUtils.get("elapsed.minutes", minutes, pluralize(minutes));
        }
        if (delta < 24 * 60 * 60) {
            long hours = delta / (60 * 60);
            return MessagesUtils.get("elapsed.hours", hours, pluralize(hours));
        }
        if (delta < 30 * 24 * 60 * 60) {
            long days = delta / (24 * 60 * 60);
            return MessagesUtils.get("elapsed.days", days, pluralize(days));
        }
        if (delta < 365 * 24 * 60 * 60) {
            long months = delta / (30 * 24 * 60 * 60);
            return MessagesUtils.get("elapsed.months", months, pluralize(months));
        }
        long years = delta / (365 * 24 * 60 * 60);
        return MessagesUtils.get("elapsed.years", years, pluralize(years));
    }

    public static List<String> parseCSV(String value) {
        return TextUtils.parseCSV(value);
    }

    public static List<String> split(String value) {
        return split(value, String.class);
    }

    public static <T> List<T> split(String value, Class<T> type) {
        return split(value, ",", type);
    }

    public static <T> List<T> split(String value, String separator, Class<T> type) {
        String[] values = StringUtils.split(value, separator);
        List<T> results = new ArrayList<T>();
        for (String s : values) {
            Object o = ConvertUtils.convert((Object) s, type);
            if (type.isInstance(o)) {
                results.add((T) o);
            }
        }
        return results;
    }

    public static String formatGBValueOnly(Double gb) {
        return SizeUtils.humanReadableValueOnly(gb * 1024 * 1024 * 1024, false);
    }
    
    public static String formatGBUnits(Double gb) {
        return SizeUtils.humanReadableUnits(gb * 1024 * 1024 * 1024, false);
    }

    public static String formatGBValueOnly(Long gb) {
        return SizeUtils.humanReadableValueOnly(gb * 1024 * 1024 * 1024, false);
    }

    public static String formatGBUnits(Long gb) {
        return SizeUtils.humanReadableUnits(gb * 1024 * 1024 * 1024, false);
    }

    public static String formatKBValueOnly(Double kb) {
        return formatByteValueOnly(kb * 1024);
    }
    
    public static String formatKBUnits(Double kb) {
        return formatByteUnits(kb * 1024);
    }
    
    public static String formatByteValueOnly(Double bytes) {
        return SizeUtils.humanReadableValueOnly(bytes, false);
    }
    
    public static String formatByteUnits(Double bytes) {
        return SizeUtils.humanReadableUnits(bytes, false);
    }        
    
    public static String formatMB(double megabytes) {
        return formatMB(megabytes, false);
    }

    public static String formatMB(double megabytes, boolean si) {
        return SizeUtils.humanReadableByteCount(megabytes * 1024 * 1024, si);
    }

    public static String humanReadableByteCount(double bytes) {
        return SizeUtils.humanReadableByteCount(bytes);
    }

    public static String machineTag(DataObjectRestRep obj, String name) {
        if (obj != null && obj.getTags() != null) {
            String tagPrefix = String.format("%s:%s=", ISA_NAMESPACE, name);
            for (String tag : obj.getTags()) {
                if (StringUtils.startsWith(tag, tagPrefix)) {
                    return StringUtils.substringAfter(tag, tagPrefix);
                }
            }
        }
        return null;
    }
}
