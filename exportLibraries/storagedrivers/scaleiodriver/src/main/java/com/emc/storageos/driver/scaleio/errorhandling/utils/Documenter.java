/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.scaleio.errorhandling.utils;

import com.emc.storageos.driver.scaleio.errorhandling.annotations.MessageBundle;
import com.emc.storageos.driver.scaleio.errorhandling.model.ExceptionMessagesProxy;
import com.emc.storageos.driver.scaleio.errorhandling.model.ServiceCoded;
import com.emc.storageos.driver.scaleio.errorhandling.model.StatusCoded;
import com.emc.storageos.driver.scaleio.errorhandling.resources.ServiceCode;
import com.sun.jersey.core.spi.scanning.PackageNamesScanner;
import com.sun.jersey.spi.scanning.AnnotationScannerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;

public class Documenter {
    private static final Logger _log = LoggerFactory.getLogger(Documenter.class);

    public static final String[] PACKAGES = new String[] { "com.emc.storageos.exceptions",
            "com.emc.storageos.isilon.restapi",
            "com.emc.storageos.datadomain.restapi.errorhandling",
            "com.emc.storageos.netapp",
            "com.emc.storageos.networkcontroller.exceptions",
            "com.emc.storageos.plugins",
            "com.emc.storageos.recoverpoint.exceptions",
            "com.emc.storageos.svcs.errorhandling.annotations",
            "com.emc.storageos.svcs.errorhandling.model",
            "com.emc.storageos.svcs.errorhandling.resources",
            "com.emc.storageos.svcs.errorhandling.utils",
            "com.emc.storageos.vnx.xmlapi",
            "com.emc.storageos.volumecontroller.impl.smis",
            "com.emc.storageos.vplex.api",
            "com.emc.storageos.exceptions",
            "com.emc.storageos.db.exceptions",
            "com.emc.storageos.coordinator.exceptions",
            "com.emc.storageos.security.exceptions",
            "com.emc.storageos.systemservices.exceptions",
            "com.emc.storageos.volumecontroller.placement",
            "com.emc.storageos.vnxe" };

    public static Collection<DocumenterEntry> createEntries() {
        final List<Class<?>> list = getMessageBundleClasses();
        _log.info("found these classes to document: {}", list);
        final Collection<DocumenterEntry> entries = new ArrayList<DocumenterEntry>();
        for (final Class<?> interfaze : list) {
            final Object proxy = ExceptionMessagesProxy.create(interfaze);

            final Method[] methods = interfaze.getDeclaredMethods();
            for (final Method method : methods) {
                StatusType status;
                try {
                    final Object[] parameters = sampleParameters(method);
                    final ServiceCoded sce = (ServiceCoded) method.invoke(proxy, parameters);
                    if (sce instanceof StatusCoded) {
                        status = ((StatusCoded) sce).getStatus();
                    } else {
                        status = sce.isRetryable() ? Status.SERVICE_UNAVAILABLE
                                : Status.INTERNAL_SERVER_ERROR;
                    }
                    entries.add(new DocumenterEntry(interfaze, method, status, sce, parameters));
                } catch (final Exception e) {
                    _log.error(String.format("Fail to create document entry for method: %s", method), e);
                }
            }
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    public static List<Class<?>> getMessageBundleClasses() {
        final PackageNamesScanner scanner = new PackageNamesScanner(PACKAGES);
        final AnnotationScannerListener scannerListener = new AnnotationScannerListener(
                MessageBundle.class);
        scanner.scan(scannerListener);
        final List<Class<?>> list = new ArrayList<Class<?>>();
        for (final Class<?> clazz : scannerListener.getAnnotatedClasses()) {
            if (clazz.isEnum()) {
                continue;
            }
            list.add(clazz);
        }
        return list;
    }

    public static void main(final String[] args) throws FileNotFoundException,
            NoSuchFieldException, SecurityException {
        PrintStream out = System.out;
        if (args.length != 0) {
            final File targetDir = new File(args[0]);
            final File targetFile = new File(targetDir, "errorhandling.txt");
            out = new PrintStream(new FileOutputStream(targetFile), true);
        }

        document(out);
    }

    private static void document(PrintStream out) throws NoSuchFieldException {
        final Map<ServiceCode, List<DocumenterEntry>> codeToEntries = new TreeMap<ServiceCode, List<DocumenterEntry>>();
        for (final ServiceCode value : ServiceCode.values()) {
            codeToEntries.put(value, new ArrayList<DocumenterEntry>());
        }

        for (final DocumenterEntry entry : createEntries()) {
            codeToEntries.get(entry.getCode()).add(entry);
        }

        for (final Entry<ServiceCode, List<DocumenterEntry>> pair : codeToEntries.entrySet()) {
            final ServiceCode code = pair.getKey();
            final List<DocumenterEntry> messages = pair.getValue();
            Collections.sort(messages);

            // no point documenting ServiceCodes that aren't in use
            if (messages.isEmpty()) {
                continue;
            }

            documentServiceCode(out, code, messages);
            documentMessages(out, messages);
            out.println();
        }
    }

    private static void documentServiceCode(PrintStream out, final ServiceCode code,
            final List<DocumenterEntry> messages) throws NoSuchFieldException {
        final StatusType status = messages.get(0).getStatus();
        final boolean retryable = status.getStatusCode() == 503;

        out.println("Service Code: " + code.getCode());
        out.println("Name:         " + code.name());
        out.println("Description:  " + code.getSummary(Locale.ENGLISH));
        out.println("Retryable:    " + retryable);
        out.println("Deprecated:   " + checkDeprecation(ServiceCode.class.getField(code.name())));
        out.println("HTTP Status:  " + status.getStatusCode() + " " + status.getReasonPhrase());
        out.println();
    }

    private static void documentMessages(PrintStream out, final List<DocumenterEntry> messages) {
        for (final DocumenterEntry message : messages) {
            documentMessage(out, message);
        }
    }

    private static void documentMessage(PrintStream out, final DocumenterEntry message) {
        final Class<?> interfaze = message.getInterfaze();
        final Method method = message.getMethod();
        final String bundle = MessageUtils.bundleNameForClass(interfaze);
        final String key = method.getName();
        final boolean deprecated = checkDeprecation(interfaze, method);
        final String pattern = Messages.getPattern(Locale.ENGLISH, key, bundle);
        final String example = message.getMessage();

        out.println("  Message Bundle:     " + bundle);
        out.println("  Message Key:        " + key);
        out.println("  Message Parameters: " + documentParameters(method));
        out.println("  Message Deprecated: " + deprecated);
        out.println("  Message Pattern:    " + pattern);
        out.println("  Message Example:    " + example);
        out.println();
    }

    private static boolean checkDeprecation(final AnnotatedElement... elements) {
        for (final AnnotatedElement element : elements) {
            if (element.isAnnotationPresent(Deprecated.class)) {
                return true;
            }
        }
        return false;
    }

    private static String documentParameters(final Method method) {
        final StringBuilder builder = new StringBuilder();
        for (final Class<?> type : method.getParameterTypes()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            if (type.isArray()) {
                builder.append(type.getComponentType().getSimpleName());
                builder.append("[]");
            } else {
                builder.append(type.getSimpleName());
            }
        }
        return builder.toString();
    }

    public static Object[] sampleParameters(final Method method) {
        final Class<?>[] types = method.getParameterTypes();
        final Object[] result = new Object[types.length];
        for (int i = 0; i < types.length; ++i) {
            result[i] = sampleParameter(method, types[i], i);
        }
        return result;
    }

    @SuppressWarnings("rawtypes")
    private static Object sampleParameter(final Method method, final Class<?> clazz, final int index) {
        if (clazz.equals(int.class)) {
            return index;
        } else if (clazz.equals(long.class)) {
            return (long) index;
        } else if (clazz.equals(double.class)) {
            return (double) index;
        } else if (clazz.equals(float.class)) {
            return (float) index;
        } else if (clazz.equals(char.class)) {
            return '0' + index;
        } else if (clazz.equals(String.class)) {
            return "string" + index;
        } else if (clazz.equals(URI.class)) {
            try {
                return new URI("sos:uri:" + index);
            } catch (final URISyntaxException e) {
                _log.error(String.format("Fail to instantiate URI with \"sos:uri:%s\"", index), e);
            }
        } else if (clazz.isAssignableFrom(Date.class)) {
            final Calendar calendar = Calendar.getInstance();
            calendar.set(2000, 1, 1, 0, 0, 0);
            return calendar.getTime();
        } else if (clazz.isAssignableFrom(Throwable.class)) {
            return new Throwable("throwable" + index);
        } else if (clazz.isAssignableFrom(Exception.class)) {
            return new Exception("exception" + index);
        } else if (clazz.isAssignableFrom(Set.class)) {
            return new HashSet();
        } else if (clazz.isAssignableFrom(List.class)) {
            return new ArrayList();
        } else if (clazz.isAssignableFrom(Map.class)) {
            return new HashMap();
        } else if (clazz.isAssignableFrom(StatusType.class)) {
            return Status.BAD_REQUEST;
        }
        try {
            return clazz.newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    protected static class DocumenterEntry implements Comparable<DocumenterEntry> {
        private final Class<?> interfaze;

        private final Method method;

        private final Object[] parameters;

        private final StatusType status;

        private final ServiceCode code;

        private final String summary;

        private final String message;

        @Override
        public String toString() {
            return "DocumenterEntry[" + interfaze.getCanonicalName() + "," + method.getName() + ","
                    + getParametersAsString() + "," + status + "," + code + "," + summary + ","
                    + message + "]";
        }

        @Override
        public int compareTo(final DocumenterEntry that) {
            return this.toString().compareTo(that.toString());
        }

        public Class<?> getInterfaze() {
            return interfaze;
        }

        public Method getMethod() {
            return method;
        }

        public StatusType getStatus() {
            return status;
        }

        public ServiceCode getCode() {
            return code;
        }

        public String getSummary() {
            return summary;
        }

        public String getMessage() {
            return message;
        }

        public Object[] getParameters() {
            return Arrays.copyOf(parameters, parameters.length);
        }

        public String getParametersAsString() {
            final StringBuilder builder = new StringBuilder();
            for (final Class<?> type : method.getParameterTypes()) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                if (type.isArray()) {
                    builder.append(type.getComponentType().getSimpleName());
                    builder.append("[]");
                } else {
                    builder.append(type.getSimpleName());
                }
            }
            return builder.toString();
        }

        public DocumenterEntry(final Class<?> interfaze, final Method method,
                final StatusType status, final ServiceCoded sce, final Object[] parameters) {
            this.interfaze = interfaze;
            this.method = method;
            this.status = status;
            this.code = sce.getServiceCode();
            this.summary = code.getSummary();
            this.message = sce.getMessage();
            this.parameters = (parameters != null) ? Arrays.copyOf(parameters, parameters.length) : null;
        }
    }
}
