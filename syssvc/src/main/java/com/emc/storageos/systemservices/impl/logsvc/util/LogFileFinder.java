/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.systemservices.impl.logsvc.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Given the log file path information, this class returns all matching files grouped
 * by base name.
 */
public class LogFileFinder {
    // Logger reference.
    private static final Logger logger = LoggerFactory.getLogger(LogFileFinder.class);
    private List<String> _logFilePaths;
    private List<String> _excludedFilePaths;

    public LogFileFinder(List<String> logFilePaths, List<String> excludedLogFilePaths) {
        _logFilePaths = logFilePaths;
        _excludedFilePaths = excludedLogFilePaths;
    }

    /**
     * Groups files by their base name as key and rolled files as values.
     * 
     * @return Map of grouped files
     */
    public Map<String, List<File>> findFilesGroupedByBaseName() {
        Map<String, List<File>> baseLogFiles = new HashMap<String, List<File>>();
        String fileName;
        List<File> logFiles = getAllFilesMatchingGlobPattern();

        List<File> sameBaseFiles;
        for (File file : logFiles) {
            String baseName;
            fileName = file.getName();
            int dotIndex = fileName.indexOf(".");
            if (dotIndex > 0) {
                baseName = fileName.substring(0, dotIndex);
            } else {
                baseName = fileName;
            }
            logger.debug("fileName: {}. baseName: {}", fileName, baseName);
            if ((sameBaseFiles = baseLogFiles.get(baseName)) == null) {
                sameBaseFiles = new ArrayList<File>();
            }
            sameBaseFiles.add(file);
            baseLogFiles.put(baseName, sameBaseFiles);
        }
        return baseLogFiles;
    }

    /**
     * Retrieves all files matching global pattern
     * 
     * @return list of valid files
     */
    private List<File> getAllFilesMatchingGlobPattern() {
        List<File> logFiles = new ArrayList<File>();
        String logFileDirPath;
        String logFileNameGlob;
        File logFileDir;
        String fileRegEx;

        Map<String, List<String>> excludeRegexesInDir = new HashMap();
        String excludeGlob = null;
        String excludeFileDirPath = null;
        for (String excludedFilePath : _excludedFilePaths) {
            logger.debug("Found excluded log file path {}", excludedFilePath);
            excludeFileDirPath = getLogFileDir(excludedFilePath);
            excludeGlob = getGlobExpr(excludedFilePath);
            if (excludeFileDirPath != null) {
                if (excludeRegexesInDir.containsKey(excludeFileDirPath)) {
                    excludeRegexesInDir.get(excludeFileDirPath).add(convertGlobToRegEx(
                            excludeGlob.trim()));
                }
                else {
                    ArrayList<String> excludeRegexList = new ArrayList<String>();
                    excludeRegexList.add(convertGlobToRegEx(excludeGlob.trim()));
                    excludeRegexesInDir.put(excludeFileDirPath, excludeRegexList);
                }
            }
        }

        for (String logFilePath : _logFilePaths) {
            logger.debug("Looking for log files {}", logFilePath);
            // Since there really is no standard easy way in java
            // to get the list of files specified by a wild-carded
            // path such as that above, we need to split the path
            // into its directory path and file name glob.
            logFileNameGlob = getGlobExpr(logFilePath);
            logger.debug("logFileNameGlob: {}", logFileNameGlob);

            // We turn the file name glob into regular
            // expressions we can use to match the file
            // names in the directory.
            fileRegEx = convertGlobToRegEx(logFileNameGlob);

            // Now, we list the files in the directory
            // and those whose name matches a regular
            // expression is a log file.
            logFileDirPath = getLogFileDir(logFilePath);
            if (logFileDirPath != null) {
                logFileDir = new File(logFileDirPath);
                logger.debug("Getting files from dir: {} matching ex: {}", logFileDirPath,
                        fileRegEx);
                logFiles.addAll(getAllFilesInDirMatchingRegEx(logFileDir, fileRegEx,
                        excludeRegexesInDir.get(logFileDirPath)));
            }
        }

        return logFiles;
    }

    /**
     * Retrieves directory path from the log file path.
     * EX: if logFilePath: /opt/storageos/logs/*.log Returns: /opt/storageos/logs
     * 
     * @param logFilePath full log file path
     * @return directory path
     */
    private String getLogFileDir(String logFilePath) {
        String directoryPathComp = null;
        int indexOfLastSlash = logFilePath.lastIndexOf(File.separator);
        if ((indexOfLastSlash != -1) && (logFilePath.length() > indexOfLastSlash + 1)) {
            directoryPathComp = logFilePath.substring(0, indexOfLastSlash);
            if (directoryPathComp.length() == 0) {
                // The file must be in the root directory.
                directoryPathComp = File.separator;
            }
        }
        return directoryPathComp;
    }

    /**
     * Retrieves the glob expression part from the log file path
     * EX: if logFilePath: /opt/storageos/logs/*.log Returns: *.log
     * 
     * @param logFilePath log file path
     * @return glob expression
     */
    private String getGlobExpr(String logFilePath) {
        String logFileNameGlob = null;
        int indexOfLastSlash = logFilePath.lastIndexOf(File.separator);
        logFileNameGlob = logFilePath.substring(indexOfLastSlash + 1);
        return logFileNameGlob;
    }

    /**
     * Retrieves all files in a directory that are matching the reg-ex.
     * 
     * @param fileDir File directory from which log files are retrieved.
     * @param regEx Regular expression that file name should match
     * @return List of files.
     */
    private List<File> getAllFilesInDirMatchingRegEx(File fileDir, final String regEx,
            final List<String> excludeRegexList) {
        List<File> logFiles = new ArrayList<File>();
        if (fileDir.exists()) {
            logFiles.addAll(Arrays.asList(fileDir.listFiles(
                    new FilenameFilter() {
                        @Override
                        public boolean accept(File logFileDir,
                                String fileName) {
                            logger.debug("Checking file {}", fileName);
                            boolean match = false;
                            if (isLogFileMatchesRegEx(fileName, regEx)) {
                                match = true;
                                if (excludeRegexList != null) {
                                    for (String eRegex : excludeRegexList) {
                                        if (isLogFileMatchesRegEx(fileName, eRegex)) {
                                            match = false;
                                            break;
                                        }
                                    }
                                }
                                if (match) {
                                    logger.debug("File {} is a log file", fileName);
                                }
                            }
                            return match;
                        }
                    })));
        }
        return logFiles;
    }

    /**
     * Checks if the file matches the regular expression
     * 
     * @param fileName
     * @param regEx
     * @return true if matches, false otherwise.
     */
    private boolean isLogFileMatchesRegEx(String fileName, String regEx) {
        boolean matches;
        Pattern fileNamePattern = Pattern.compile(regEx);
        matches = fileNamePattern.matcher(fileName).matches();
        return matches;
    }

    /**
     * Converts global expression to regular expression
     * 
     * @param name global file name
     * @return regular expression.
     */
    private String convertGlobToRegEx(String name) {
        name = name.trim();
        int strLen = name.length();
        StringBuilder sb = new StringBuilder(strLen);

        boolean escaping = false;
        int inCurlies = 0;
        for (char currentChar : name.toCharArray()) {
            switch (currentChar) {
                case '*':
                    if (escaping) {
                        sb.append("\\*");
                    } else {
                        sb.append(".*");
                    }
                    escaping = false;
                    break;
                case '?':
                    if (escaping) {
                        sb.append("\\?");
                    } else {
                        sb.append('.');
                    }
                    escaping = false;
                    break;
                case '.':
                case '(':
                case ')':
                case '+':
                case '|':
                case '^':
                case '$':
                case '@':
                case '%':
                    sb.append('\\');
                    sb.append(currentChar);
                    escaping = false;
                    break;
                case '\\':
                    if (escaping) {
                        sb.append("\\\\");
                        escaping = false;
                    } else {
                        escaping = true;
                    }
                    break;
                case '{':
                    if (escaping) {
                        sb.append("\\{");
                    } else {
                        sb.append('(');
                        inCurlies++;
                    }
                    escaping = false;
                    break;
                case '}':
                    if (inCurlies > 0 && !escaping) {
                        sb.append(')');
                        inCurlies--;
                    } else if (escaping) {
                        sb.append("\\}");
                    } else {
                        sb.append("}");
                    }
                    escaping = false;
                    break;
                case ',':
                    if (inCurlies > 0 && !escaping) {
                        sb.append('|');
                    } else if (escaping) {
                        sb.append("\\,");
                    } else {
                        sb.append(",");
                    }
                    break;
                default:
                    escaping = false;
                    sb.append(currentChar);
            }
        }
        return sb.toString();
    }
}
