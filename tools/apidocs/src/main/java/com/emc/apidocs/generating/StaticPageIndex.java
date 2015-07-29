/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.apidocs.generating;

import com.emc.apidocs.KnownPaths;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

/**
 * Responsible for loading and keeping track of a hierarchy of PageFile folders
 */
public class StaticPageIndex {
    private static final String COMMENT_MARKER = "#";
    private static final String INDEX_FILENAME = "index.txt";
    private static final String PAGE_TITLE_PROPERTY = "title:";
    public static final String ARTIFACTS_DIR = "artifacts";

    public static final String TOC_IGNORE_FILE_NAME = "toc_ignore";

    // List of ALL static pages
    List<PageFile> allPages = Lists.newArrayList();

    PageFolder rootFolder;

    // List of ALL artifacts found
    List<File> artifacts = Lists.newArrayList();

    public StaticPageIndex() {

        rootFolder = loadFolder(KnownPaths.getPageDir());
    }

    public List<PageFile> getAllStaticPages() {
        return allPages;
    }

    public List<File> getAllArtifacts() {
        return artifacts;
    }

    public PageFolder loadFolder(File directory) {
        List<String> indexContents = readFolderIndex(directory);

        // The first line is the title
        String title = directory.getName();
        if (!indexContents.isEmpty()) {
            title = indexContents.get(0);
            indexContents.remove(0);
        }

        // Load Pages/Child folders specified in the index
        PageFolder pageFolder = new PageFolder(title);
        for (String page : indexContents) {
            File indexEntryFile = new File(directory.getAbsolutePath() + "/" + page);
            if (indexEntryFile.exists()) {
                if (indexEntryFile.isDirectory()) {
                    pageFolder.children.add(loadFolder(indexEntryFile));
                }
                else {
                    PageFile newPageFile = new PageFile(indexEntryFile, pageFolder);
                    pageFolder.files.add(newPageFile);
                    allPages.add(newPageFile);
                }
            }
        }

        // Add any files not mentioned in the index
        List<PageFile> pagesNotInIndex = Lists.newArrayList();
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                if (file.getName().endsWith(".html") && !isFileInIndex(file, indexContents)) {
                    PageFile newPageFile = new PageFile(file, pageFolder);
                    ;
                    pagesNotInIndex.add(newPageFile);
                    allPages.add(newPageFile);
                }
            }
            else if (file.isDirectory()) {
                if (file.getName().equals(ARTIFACTS_DIR)) {
                    for (File artifact : file.listFiles()) {
                        if (artifact.isFile()) {
                            artifacts.add(artifact);
                        }
                    }
                }
                else if (!file.getName().startsWith(".") && !isFileInIndex(file, indexContents)) {
                    pageFolder.children.add(loadFolder(file));
                }
            }
        }

        // Sort list by title to be backward compatible
        Collections.sort(pagesNotInIndex, new Comparator<PageFile>() {
            @Override
            public int compare(PageFile pageFile, PageFile pageFile1) {
                return pageFile.getFileName().compareTo(pageFile1.getFileName());
            }
        });

        pageFolder.files.addAll(pagesNotInIndex);

        return pageFolder;
    }

    private List<String> readFolderIndex(File directory) {
        // See if there is an index.txt
        File indexFile = new File(directory.getAbsolutePath() + File.separator + INDEX_FILENAME);
        List<String> indexContents = Lists.newArrayList();
        if (indexFile.exists()) {
            try {
                indexContents = IOUtils.readLines(new FileInputStream(indexFile));
            } catch (IOException e) {
                throw new RuntimeException("Error reading index file " + indexFile.getAbsolutePath(), e);
            }
        }

        List<String> cleanList = Lists.newArrayList();

        // Remove any lines that start with comment marker
        Iterator<String> it = indexContents.iterator();
        while (it.hasNext()) {
            String currentLine = it.next();
            if (!currentLine.startsWith(COMMENT_MARKER) && !currentLine.equals("")) {
                cleanList.add(currentLine);
            }
        }

        return cleanList;
    }

    private boolean isFileInIndex(File file, List<String> index) {
        for (String indexEntry : index) {
            if (indexEntry.toLowerCase().equals(file.getName().toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /** Represents a directory on disk that holds static page files */
    public static class PageFolder {
        public final String title;
        public List<PageFile> files = Lists.newArrayList();
        public List<PageFolder> children = Lists.newArrayList();

        public PageFolder(String title) {
            this.title = title;
        }

        public String getId() {
            return title.replaceAll(" ", "_").replaceAll("\\(", "").replaceAll("\\)", "");
        }

        public boolean includeInTOC() {
            return !title.equals(TOC_IGNORE_FILE_NAME);
        }
    }

    /** Represents a file on disk that holds static page content */
    public static class PageFile {
        public File file;
        public String title;
        public String content;
        public String path;
        public PageFolder parentFolder;

        public PageFile(File file, PageFolder parentFolder) {
            this.file = file;
            this.parentFolder = parentFolder;
            this.path = "";
            // path = file.getParent().substring(pageFileDir.getAbsolutePath().length());
            if (path.startsWith("/")) {
                path = path.substring(1);
            }
            loadContent();
        }

        public String getFileName() {
            return file.getName();
        }

        public String getGeneratedFileName() {
            if (path.equals("")) {
                return getFileName();
            } else {
                return path.replaceAll("/", "_") + "_" + getFileName();
            }
        }

        private void loadContent() {
            String fileContent = "";
            try {
                fileContent = IOUtils.toString(new FileInputStream(file));
            } catch (IOException e) {
                throw new RuntimeException("Unable to read file " + file.getAbsolutePath(), e);
            }

            if (fileContent.startsWith(PAGE_TITLE_PROPERTY)) {
                int titleEnd = fileContent.indexOf("\n");

                title = fileContent.substring(PAGE_TITLE_PROPERTY.length(), titleEnd);
                content = fileContent.substring(titleEnd).trim();
            }
            else {
                title = file.getName();
                content = fileContent;
            }
        }
    }
}
