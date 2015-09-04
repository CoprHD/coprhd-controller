/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package util.support;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.DirectoryWalker;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

/** A selection of Zip File based utilities */
public class ZipUtils {

    public static void zipDirectoryToFile(File directory, final File targetFile) throws IOException {
        zipDirectoryToFile(directory, targetFile, null);
    }

    public static void zipDirectoryToFile(File directory, final File targetFile, String zipPrefix) throws IOException {
        zipDirectoryToFile(directory, targetFile, new FileFilter() {
            @Override
            public boolean accept(File file) {
                return !file.equals(targetFile);
            }
        }, zipPrefix);
    }

    private static void zipDirectoryToFile(File directory, final File targetFile, FileFilter filter, String zipPrefix) throws IOException {
        OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile));
        ZipStreamCreator zipCreator = new ZipStreamCreator(filter, zipPrefix);
        zipCreator.traverseDirectory(directory, out);
    }

    /** @return The contents of the directory Zipped up and converted into an input stream */
    public static InputStream directoryToZipInputStream(File directory) throws IOException {
        return new ByteArrayInputStream(directoryToZipByteArray(directory));
    }

    public static byte[] directoryToZipByteArray(File directory) throws IOException {
        ZipStreamCreator zipCreator = new ZipStreamCreator();
        ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
        zipCreator.traverseDirectory(directory, bytesOut);
        return bytesOut.toByteArray();
    }

    /** Unpacks a Zip into the given directory */
    public static void zipInputStreamToDirectory(InputStream zipInputStream, File directory) throws IOException {
        ZipInputStream zipStream = new ZipInputStream(zipInputStream);

        try {
            ZipEntry entry = null;
            while ((entry = zipStream.getNextEntry()) != null) {
                File newFile = new File(FilenameUtils.concat(directory.getAbsolutePath(), entry.getName()));
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.createNewFile();
                    IOUtils.copy(zipStream, new FileOutputStream(newFile));
                }
                newFile.setLastModified(entry.getTime());
            }
        } finally {
            IOUtils.closeQuietly(zipStream);
        }
    }

    /** Class used to walk the directory structure and build up the Zip File */
    private static class ZipStreamCreator extends DirectoryWalker {
        private ZipOutputStream outStream;
        private String startPath;
        final private String zipPrefix;

        public ZipStreamCreator() {
            super();
            this.zipPrefix = "";
        }

        public ZipStreamCreator(FileFilter filter, String zipPrefix) {
            super(filter, -1);
            if (zipPrefix == null || zipPrefix.equals("")) {
                this.zipPrefix = "";
            }
            else if (zipPrefix.endsWith("/")) {
                this.zipPrefix = zipPrefix;
            }
            else {
                this.zipPrefix = zipPrefix + "/";
            }
        }

        /**
         * Traverses the directory creating the output zip. The stream is closed
         * after the zip is created.
         * 
         * @param startDirectory Starting directory to walk
         * @param out Output stream..
         * @throws java.io.IOException
         */
        public void traverseDirectory(File startDirectory, OutputStream out) throws IOException {
            startPath = startDirectory.getAbsolutePath();
            outStream = new ZipOutputStream(out);

            try {
                walk(startDirectory, new ArrayList());
            } finally {
                outStream.flush();
                outStream.close();
            }
        }

        @Override
        public boolean handleDirectory(File directory, int depth, Collection results) {
            if (directory.getAbsolutePath().equals(startPath))
                return true;

            ZipEntry entry = new ZipEntry(createEntryName(directory) + "/");
            try {
                entry.setTime(directory.lastModified());
                outStream.putNextEntry(entry);
                outStream.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return true;
        }

        @Override
        public void handleFile(File file, int depth, Collection results) {
            // Ignore the target file
            ZipEntry entry = new ZipEntry(createEntryName(file));
            try {
                entry.setTime(file.lastModified());
                outStream.putNextEntry(entry);

                FileInputStream in = new FileInputStream(file);
                IOUtils.copy(in, outStream);
                in.close();

                outStream.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private String createEntryName(File file) {
            return zipPrefix + file.getAbsolutePath().substring(startPath.length() + 1).replace('\\', '/');
        }
    }
}
