/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package plugin;

import java.io.File;
import java.io.Serializable;
import java.util.Date;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.lesscss.LessCompiler;
import org.lesscss.LessSource;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.cache.Cache;
import play.mvc.Http;
import play.mvc.Http.Request;
import play.mvc.Http.Response;
import play.utils.Utils;
import play.vfs.VirtualFile;

import com.google.common.collect.Sets;

public class LessPlugin extends PlayPlugin {
    private static final String ETAG = "ETag";
    private static final String LAST_MODIFIED = "Last-Modified";
    private LessCompiler compiler = new LessCompiler();
    /** The root path under which to process CSS requests (defaults to '/public/'). */
    private String rootPath = "/public/";
    /** Tracks whether the application is started (determines if caching is possible or not). */
    private boolean started;

    @Override
    public void onApplicationStart() {
        started = true;
        rootPath = Play.configuration.getProperty("less.rootPath", "/public/");
        compiler = new LessCompiler();
        compiler.setCompress(isCompressEnabled());
    }

    /**
     * Determines if compressing the CSS is enabled. This defaults to true in PROD mode and false otherwise.
     */
    private boolean isCompressEnabled() {
        String defaultValue = Play.mode.isProd() ? "true" : "false";
        return Play.configuration.getProperty("less.compress", defaultValue).equals("true");
    }

    /**
     * Gets the less source file for this request. If the request should be ignored by the plugin, null is returned.
     * 
     * @param request
     *            the raw request.
     * @return the less source, or null.
     */
    private VirtualFile getLessSourceFile(Request request) {
        // Only process .css requests within the configured root path
        if (!(request.path.startsWith(rootPath) && request.path.endsWith(".css"))) {
            return null;
        }
        VirtualFile cssFile = VirtualFile.fromRelativePath(request.path);
        // If the file exists, don't even check less files in prod mode
        if (Play.mode.isProd() && cssFile.exists()) {
            return null;
        }

        // Check for a .less file with the same base name
        VirtualFile lessFile = VirtualFile.fromRelativePath(StringUtils.removeEnd(request.path, ".css") + ".less");
        return (lessFile.exists() && !lessFile.isDirectory()) ? lessFile : null;
    }

    @Override
    public boolean rawInvocation(Request request, Response response) throws Exception {
        VirtualFile lessFile = getLessSourceFile(request);
        if (lessFile == null) {
            return false;
        }

        response.contentType = "text/css";
        try {
            processLess(lessFile, request, response);
        } catch (Exception e) {
            error(response, lessFile, e);
        }
        return true;
    }

    /**
     * Processes a less file and returns the compiled CSS.
     * 
     * @param file
     *            the less file.
     * @param request
     *            the HTTP request.
     * @param response
     *            the HTTP response.
     * 
     * @throws Exception
     *             if an error occurs (typically LESS compilation).
     */
    private void processLess(VirtualFile file, Request request, Response response) throws Exception {
        long lastModified = getLastModified(file);
        if (lastModified < 0) {
            sendOk(response, file);
        }
        else {
            String etag = getETag(file, lastModified);
            if (request.isModified(etag, lastModified)) {
                sendOk(response, file);
            }
            else {
                sendNotModified(response, etag, lastModified);
            }
        }
    }

    /**
     * Sends the compiled CSS for the given LESS file. If a compiled CSS exists for this path with the same modification
     * time it will be returned, otherwise the LESS will be compiled and cached for future requests.
     * 
     * @param response
     *            the HTTP response to write to.
     * @param file
     *            the LESS file.
     * 
     * @throws Exception
     *             if an error occurs (typically a LESS compilation).
     */
    private void sendOk(Response response, VirtualFile file) throws Exception {
        LessSource source = new LessSource(file.getRealFile());
        long lastModified = source.getLastModifiedIncludingImports();
        String etag = getETag(file, lastModified);
        String content = compileToCss(source, lastModified);

        response.status = Http.StatusCode.OK;
        response.setHeader(LAST_MODIFIED, Utils.getHttpDateFormatter().format(new Date(lastModified)));
        response.setHeader(ETAG, etag);
        response.print(content);
    }

    /**
     * Sends a Not Modified response.
     * 
     * @param response
     *            the HTTP response to write to.
     * @param etag
     *            the ETag for the CSS.
     * @param lastModified
     *            the last modified time of the CSS.
     */
    private void sendNotModified(Response response, String etag, long lastModified) {
        response.status = Http.StatusCode.NOT_MODIFIED;
        response.setHeader(ETAG, etag);
    }

    /**
     * Sends an Internal Server Error response.
     * 
     * @param response
     *            the HTTP response to write to.
     * @param file
     *            the requested LESS file.
     * @param e
     *            the error that occurred.
     */
    private void error(Response response, VirtualFile file, Exception e) {
        response.status = Http.StatusCode.INTERNAL_ERROR;
        response.print(StringUtils.defaultString(e.getMessage(), e.getClass().getName()));
        Logger.error(e, "Less Compilation Failed: %s", file.relativePath());
    }

    /**
     * Gets the last modified time for a LESS file. The cached related files for this LESS files are used to find the
     * most recently modified in the collection.
     * 
     * @param lessFile
     *            the LESS file.
     * @return the last modified time of all the LESS files, or -1 if no previous modified time is known.
     */
    private long getLastModified(VirtualFile lessFile) {
        long lastModified = -1;
        Set<File> relatedFiles = getCachedRelatedFiles(lessFile);
        if (relatedFiles != null) {
            for (File relatedFile : relatedFiles) {
                lastModified = Math.max(lastModified, relatedFile.lastModified());
            }
        }
        return lastModified;
    }

    /**
     * Gets the HTTP ETag to use for this LESS file.
     * 
     * @param lessFile
     *            the LESS file.
     * @param lastModified
     *            the last modified time of all the related LESS files.
     * @return the ETag header value.
     */
    private String getETag(VirtualFile lessFile, long lastModified) {
        return "\"" + lessFile.relativePath() + "-" + lastModified + "\"";
    }

    /**
     * Gets the related files for a given LESS file, if known.
     * 
     * @param lessFile
     *            the LESS file.
     * @return the set of related files, if cached.
     */
    private Set<File> getCachedRelatedFiles(VirtualFile lessFile) {
        return getCached("LESS:" + lessFile.getRealFile().getAbsolutePath());
    }

    /**
     * Caches all related files for this LESS source. This includes the LESS file and all imports.
     * 
     * @param source
     *            the LESS source.
     */
    private void cacheRelatedFiles(LessSource source) {
        Set<File> files = Sets.newHashSet();
        addRelatedFiles(source, files);
        setCached("LESS:" + source.getAbsolutePath(), files);
    }

    /**
     * Adds related files to the set of files. This recursively adds LESS sources and imports.
     * 
     * @param source
     *            the LESS source.
     * @param files
     *            the files to add to.
     */
    private void addRelatedFiles(LessSource source, Set<File> files) {
        files.add(new File(source.getAbsolutePath()));
        for (LessSource lessImport : source.getImports().values()) {
            addRelatedFiles(lessImport, files);
        }
    }

    /**
     * Compiles the LESS source into CSS, returning a cached version if possible.
     * 
     * @param source
     *            the LESS source.
     * @param lastModified
     *            the last modified time of the LESS files.
     * @return the compiled CSS.
     * 
     * @throws Exception
     *             if an error occurs (LESS compilation).
     */
    private String compileToCss(LessSource source, long lastModified) throws Exception {
        cacheRelatedFiles(source);

        String cacheKey = "CompiledCss:" + source.getAbsolutePath();
        CompiledCss compiledCss = getCached(cacheKey);
        if ((compiledCss != null) && (compiledCss.lastModified == lastModified)) {
            return compiledCss.content;
        }

        Logger.info("Compiling LESS: %s, lastModified: %s", source.getAbsolutePath(), lastModified);
        String content = compiler.compile(source);
        setCached(cacheKey, new CompiledCss(content, lastModified));

        return content;
    }

    @SuppressWarnings("unchecked")
    private <T> T getCached(String key) {
        if (started) {
            return (T) Cache.get(key);
        }
        return null;
    }

    private <T> T setCached(String key, T value) {
        if (started) {
            Cache.set(key, value);
        }
        return value;
    }

    /**
     * Class for caching the compiled CSS result of a LESS compilation.
     */
    @SuppressWarnings("serial")
    private static class CompiledCss implements Serializable {
        public String content;
        public long lastModified;

        public CompiledCss(String content, long lastModified) {
            this.content = content;
            this.lastModified = lastModified;
        }
    }
}
