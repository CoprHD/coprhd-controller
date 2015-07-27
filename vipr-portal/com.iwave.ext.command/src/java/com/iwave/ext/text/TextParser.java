/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * This is a utility class for parsing text into repeating blocks. A start and end pattern can be
 * specified to limit the scope of the blocks, and a repeating block pattern is used to break the
 * text into chunks. The typical text this can be used to parse is as follows:
 * 
 * <pre>
 * excluded text... (text before the start is ignored)
 * START PATTERN
 * excluded text... (text before the first repeating pattern is ignored)
 *   REPEATING PATTERN
 *   block 1
 *   REPEATING PATTERN
 *   block 2
 *   REPEATING PATTERN
 *   block 3
 * END PATTERN
 * excluded text... (text after the end pattern is ignored)
 * </pre>
 * 
 * @author jmiller
 */
public class TextParser {
    /** The pattern defining the overall start of the text blocks. */
    private Pattern startPattern;
    /** Whether a match on the start pattern is optional. */
    private boolean startMatchOptional;
    /** The pattern defining the overall end of the text blocks. */
    private Pattern endPattern;
    /** Whether a match on the end pattern is optional. */
    private boolean endMatchOptional;
    /** The repeating pattern that begins a block. */
    private Pattern repeatPattern;

    public Pattern getStartPattern() {
        return startPattern;
    }

    public void setStartPattern(Pattern startPattern) {
        this.startPattern = startPattern;
    }

    public boolean isStartMatchOptional() {
        return startMatchOptional;
    }

    public void setStartMatchOptional(boolean startMatchOptional) {
        this.startMatchOptional = startMatchOptional;
    }

    public Pattern getEndPattern() {
        return endPattern;
    }

    public void setEndPattern(Pattern endPattern) {
        this.endPattern = endPattern;
    }

    public boolean isEndMatchOptional() {
        return endMatchOptional;
    }

    public void setEndMatchOptional(boolean endMatchOptional) {
        this.endMatchOptional = endMatchOptional;
    }

    public Pattern getRepeatPattern() {
        return repeatPattern;
    }

    public void setRepeatPattern(Pattern repeatPattern) {
        this.repeatPattern = repeatPattern;
    }

    public void setRequiredStartPattern(Pattern startPattern) {
        setStartPattern(startPattern);
        setStartMatchOptional(false);
    }

    public void setOptionalStartPattern(Pattern startPattern) {
        setStartPattern(startPattern);
        setStartMatchOptional(true);
    }

    public void setRequiredEndPattern(Pattern endPattern) {
        setEndPattern(endPattern);
        setEndMatchOptional(false);
    }

    public void setOptionalEndPattern(Pattern endPattern) {
        setEndPattern(endPattern);
        setEndMatchOptional(true);
    }

    /**
     * Parses the text into repeating blocks.
     * 
     * @param text the text to parse.
     * @return the blocks.
     */
    public List<String> parseTextBlocks(String text) {
        List<String> blocks = new ArrayList<String>();

        String limitedText = limitText(text);
        if (limitedText != null) {
            int lastBlockIndex = -1;
            Matcher matcher = repeatPattern.matcher(limitedText);

            while (matcher.find()) {
                int currentBlockIndex = matcher.start();
                if (lastBlockIndex > -1) {
                    String block = limitedText.substring(lastBlockIndex, currentBlockIndex);
                    blocks.add(block);
                }
                lastBlockIndex = currentBlockIndex;
            }
            if (lastBlockIndex > -1) {
                String block = limitedText.substring(lastBlockIndex);
                blocks.add(block);
            }
        }

        return blocks;
    }

    /**
     * Limits text based on the start and end pattern.
     * 
     * @param text the text to limit.
     * @return the limited text.
     */
    protected String limitText(String text) {
        int startIndex = 0;
        int searchStart = 0;
        if (startPattern != null) {
            Matcher startMatcher = startPattern.matcher(text);
            if (startMatcher.find()) {
                startIndex = startMatcher.start();
                searchStart = startMatcher.end();
            }
            else if (!startMatchOptional) {
                return null;
            }
        }

        int endIndex = text.length();
        if (endPattern != null) {
            Matcher endMatcher = endPattern.matcher(text);
            if (endMatcher.find(searchStart)) {
                endIndex = endMatcher.start();
            }
            else if (!endMatchOptional) {
                return null;
            }
        }

        if ((startIndex > 0) || (endIndex < text.length())) {
            return StringUtils.substring(text, startIndex, endIndex);
        }
        else {
            return text;
        }
    }

    /**
     * Determines if the text has a match for the given pattern.
     * 
     * @param pattern the pattern to match.
     * @param text the text to search.
     * @return true if the text has a match.
     */
    public boolean hasMatch(Pattern pattern, String text) {
        return pattern.matcher(text).find();
    }

    /**
     * Finds the first match of the specified pattern and returns the first group.
     * 
     * @param pattern the pattern to match.
     * @param text the text to search.
     * @return the first group of the matched pattern.
     */
    public String findMatch(Pattern pattern, String text) {
        return findMatch(pattern, text, 1);
    }

    /**
     * Finds the first match of the specified pattern and returns the specified matched group.
     * 
     * @param pattern the pattern to match.
     * @param text the text to search.
     * @param groupNum the group number to match.
     * @return the matched group of the pattern.
     */
    public String findMatch(Pattern pattern, String text, int groupNum) {
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(groupNum);
        }
        else {
            return null;
        }
    }

    /**
     * Parses the text into name/value pairs.
     * 
     * @param text the text to parse.
     * @param separator the separator character between name and value.
     * @return the properties.
     */
    public Map<String, String> parseProperties(String text, char separator) {
        Map<String, String> properties = new HashMap<String, String>();

        String[] lines = text.split("\\r\\n|\\r|\\n");
        for (int i = 0; i < lines.length; i++) {
            int index = lines[i].indexOf(separator);
            if (index > -1) {
                String name = StringUtils.trimToNull(StringUtils.substring(lines[i], 0, index));
                String value = StringUtils.trimToNull(StringUtils.substring(lines[i], index + 1));

                if (name != null) {
                    properties.put(name, value);
                }
            }
        }

        return properties;
    }

    protected Integer getInteger(String value) {
        try {
            return Integer.parseInt(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    protected Long getLong(String value) {
        try {
            return Long.parseLong(value);
        }
        catch (Exception e) {
            return null;
        }
    }

    protected Boolean getYesNo(String value) {
        if ("YES".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        else if ("NO".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        else {
            return null;
        }
    }

    protected boolean getYesNo(String value, boolean defaultValue) {
        Boolean result = getYesNo(value);
        if (result != null) {
            return result.booleanValue();
        }
        else {
            return defaultValue;
        }
    }
}
