/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.services.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);
    private static final String tmpDir;

    static {
        // Initialize tmp directory, remove possible existing separator at last position.
        String rawDir = System.getProperty("java.io.tmpdir");
        tmpDir = rawDir.endsWith(File.separator) ? rawDir.substring(0, rawDir.length() - 1) : rawDir;
    }

    public static String generateTmpFileName(String fileName) {
        if (fileName == null || fileName.contains(File.separator)) {
            throw new RuntimeException("File name can't be null or contain file separator");
        }
        return StringUtils.join(new String[] {tmpDir, fileName}, File.separator);
    }

    /**
     * Read serialized object from a file
     * 
     * @param name
     * @return
     * @throws ClassNotFoundException
     * @throws IOException
     */
    public static Object readObjectFromFile(String name)
            throws ClassNotFoundException, IOException {
        byte[] data = readDataFromFile(name);
        return deserialize(data);
    }

    /**
     * Write serialized object into a file
     * 
     * @param obj
     * @param name
     * @throws IOException
     */
    public static void writeObjectToFile(Object obj, String name) throws IOException {
        File file = new File(name);
        // if file doesn't exists, then create it
        if (!file.exists()) {
            File dir = new File(file.getParentFile().getAbsolutePath());
            if (!dir.exists()) {
                dir.mkdirs();
            }
            file.createNewFile();
        }

        byte[] data = serialize(obj);
        try (FileOutputStream fop = new FileOutputStream(file)) {
            fop.write(data);
            fop.flush();
            fop.close();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static byte[] readDataFromFile(String name) throws IOException {
        Path path = Paths.get(name);
        return Files.readAllBytes(path);
    }

    private static byte[] serialize(Object o) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        try {
            out.writeObject(o);
        } finally {
            out.close();
        }
        return bos.toByteArray();
    }

    private static Object deserialize(byte[] data) throws IOException,
            ClassNotFoundException {
        Object obj = null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream in = new ObjectInputStream(bis);
        try {
            obj = in.readObject();
        } finally {
            in.close();
        }
        return obj;
    }

    /**
     * Write byte array into a regular file
     * 
     * @param filePath
     * @param content
     * @throws IOException
     */
    public static void writePlainFile(String filePath, byte[] content) throws IOException {
        FileOutputStream fileOuputStream = new FileOutputStream(filePath);
        fileOuputStream.write(content);
        fileOuputStream.close();
    }

    /**
     * check if a file exists.
     * 
     * @param filepath
     * @return
     */
    public static boolean exists(String filepath) {
        File f = new File(filepath);
        if (f.exists()) {
            return true;
        }
        return false;
    }

    /**
     * Delete a file
     * 
     * @param filePath
     * @throws IOException
     */
    public static void deleteFile(String filePath) throws IOException {
        try {
            File file = new File(filePath);
            file.delete();
        } catch (Exception e) {
            log.error("Failed to delete {}.", filePath, e);
        }
    }

    /**
     * Get the value of property with specific key
     * 
     * @param file
     * @param key
     * @return value of the key
     * @throws IOException
     */
    public static String readValueFromFile(File file, String key) throws IOException {
        String value = null;
        if (file.exists()) {
            Properties prop = new Properties();
            FileReader reader = new FileReader(file);
            prop.load(reader);
            reader.close();
            value = prop.getProperty(key);
            log.info("The value of property with key({}) is: {}", key, value);
            return value;
        }
        log.info("File({}) doesn't exist", file.getAbsoluteFile());
        return null;
    }

    public static void chmod(File file, String perms) {
        if (file == null || file.exists() == false) {
            return;
        }
        String[] cmds = { "/bin/chmod", "-R", perms, file.getAbsolutePath() };
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
        if (result.execFailed() || result.getExitValue() != 0) {
            throw new IllegalStateException(String.format("Execute command failed: %s", result));
        }
    }

    public static void chown(File file, String owner, String group) {
        if (file == null || file.exists() == false) {
            return;
        }
        String[] cmds = { "/bin/chown", "-R", owner + ":" + group, file.getAbsolutePath() };
        Exec.Result result = Exec.exec(Exec.DEFAULT_CMD_TIMEOUT, cmds);
        if (result.execFailed() || result.getExitValue() != 0) {
            throw new IllegalStateException(String.format("Execute command failed: %s", result));
        }
    }

    /**
     * Get file by regEx under dir.
     * 
     * @param dir the directory which file resides in
     * @param regEx the regular expression of file name
     * @throws IOException
     */
    public static List<File> getFileByRegEx(File dir, String regEx) {
        final Pattern pattern = Pattern.compile(regEx);
        File[] files = dir.listFiles(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                return pattern.matcher(filename).matches();
            }

        });

        return Collections.unmodifiableList(Arrays.asList(files));
    }

    /**
     * Get the latest modified date of files under a directory.
     *
     * @param directory the directory which file resides in
     */
    public static Date getLastModified(File directory) {
        File[] files = listAllFiles(directory);
        if (files.length == 0) {
            return null;
        }
        Arrays.sort(files, new Comparator<File>() {
            public int compare(File o1, File o2) {
                return new Long(o2.lastModified()).compareTo(o1.lastModified()); //latest 1st
            }
        });
        log.info("Last modified file:{}, time:{}", files[0], files[0].lastModified());
        return new Date(files[0].lastModified());
    }

    /**
     * Returns an array of abstract pathnames denoting the files in the
     * directory denoted by this abstract pathname and its sub directories
     *
     * @param directory the directory which file resides in
     */
    private static File[] listAllFiles(File directory) {
        if (directory == null || !directory.exists()) {
            return new File[0];
        }
        List<File> fileList = new ArrayList<File>();
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                fileList.addAll(Arrays.asList(listAllFiles(file)));
            } else {
                fileList.add(file);
            }
        }
        return fileList.toArray(new File[0]);
    }
}
