/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.util;

import com.emc.storageos.model.DataObjectRestRep;
import com.emc.storageos.model.NamedRelatedResourceRep;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.vipr.client.core.util.ResourceUtils;

import static com.emc.vipr.client.core.util.ResourceUtils.name;
import static com.emc.vipr.client.core.util.ResourceUtils.uris;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import controllers.Common;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.libs.F.Promise;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Router;
import play.mvc.With;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

@With(Common.class)
public class ViprResourceController extends Controller {
    protected static final String DISCOVERY_STARTED = "PhysicalAssets.introspection";
    protected static final String DEREGISTER_SUCCESS = "PhysicalAssets.deregistration.success";
    protected static final String DEREGISTER_ERROR = "PhysicalAssets.deregistration.error";
    protected static final String REGISTER_SUCCESS = "PhysicalAssets.registration.success";
    protected static final String REGISTER_ERROR = "PhysicalAssets.registration.error";

    protected static boolean isTrue(Boolean value) {
        return Boolean.TRUE.equals(value);
    }

    protected static boolean isFalse(Boolean value) {
        return Boolean.FALSE.equals(value);
    }

    protected static boolean isNotTrue(Boolean value) {
        return !Boolean.TRUE.equals(value);
    }

    protected static boolean isNotFalse(Boolean value) {
        return !Boolean.FALSE.equals(value);
    }

    protected static <T> T defaultValue(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    protected static int defaultInt(Integer value) {
        return defaultInt(value, 0);
    }

    protected static int defaultInt(Integer value, int defaultValue) {
        return value != null ? value : defaultValue;
    }

    protected static boolean defaultBoolean(Boolean value) {
        return defaultBoolean(value, false);
    }

    protected static boolean defaultBoolean(Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }

    protected static <T> Set<T> defaultSet(Set<T> set) {
        if (set == null) {
            set = Sets.newHashSet();
        }
        return set;
    }

    protected static String asString(URI value) {
        return ResourceUtils.asString(value);
    }

    protected static String stringId(DataObjectRestRep value) {
        return ResourceUtils.stringId(value);
    }

    protected static String stringId(RelatedResourceRep ref) {
        return ResourceUtils.stringId(ref);
    }

    protected static List<String> stringIds(Collection<? extends DataObjectRestRep> values) {
        return ResourceUtils.stringIds(values);
    }

    protected static List<String> stringRefIds(Collection<? extends RelatedResourceRep> refs) {
        return ResourceUtils.stringRefIds(refs);
    }

    /**
     * RenderArgs safe method whereby the Play tags will automatically escape any labels
     */
    protected static List<StringOption> dataObjectOptions(Collection<? extends DataObjectRestRep> values) {
        return dataObjectOptions(values, true, false);
    }

    protected static List<StringOption> dataObjectOptions(Collection<? extends DataObjectRestRep> values, boolean sorted, boolean escaped) {
        List<StringOption> options = Lists.newArrayList();
        if (values != null) {
            for (DataObjectRestRep value : values) {
                String name = (escaped ? StringEscapeUtils.escapeHtml(name(value)) : name(value));
                options.add(new StringOption(stringId(value), name));
            }
        }
        if (sorted) {
            Collections.sort(options);
        }
        return options;
    }

    protected static <T extends DataObjectRestRep> void addDataObjectOptions(String name, Promise<List<T>> promise) {
        try {
            renderArgs.put(name, dataObjectOptions(promise.get()));
        } catch (Exception e) {
            Throwable cause = Common.unwrap(e);
            Logger.error(cause, "Failed to load '%s'", name);
            flash.now("error", MessagesUtils.get("ViprResourceController.failedToLoad", name, Common.getUserMessage(cause)));
            renderArgs.put(name, Collections.emptyList());
        }
    }

    protected static void addStringOptions(String name, Promise<List<String>> promise) {
        try {
            renderArgs.put(name, StringOption.options(promise.get()));
        } catch (Exception e) {
            Throwable cause = Common.unwrap(e);
            Logger.error(cause, "Failed to load '%s'", name);
            flash.now("error", MessagesUtils.get("ViprResourceController.failedToLoad", name, Common.getUserMessage(cause)));
            renderArgs.put(name, Collections.emptyList());
        }
    }

    protected static List<StringOption> namedRefOptions(Collection<? extends NamedRelatedResourceRep> refs) {
        return namedRefOptions(refs, true);
    }

    protected static List<StringOption> namedRefOptions(Collection<? extends NamedRelatedResourceRep> refs,
            boolean sorted) {
        List<StringOption> options = Lists.newArrayList();
        if (refs != null) {
            for (NamedRelatedResourceRep ref : refs) {
                options.add(new StringOption(stringId(ref), name(ref)));
            }
        }
        if (sorted) {
            Collections.sort(options);
        }
        return options;
    }

    protected static String actionUrl(Class<? extends Controller> controller, String action, String argName,
            Object argValue) {
        return actionUrl(controller.getSimpleName() + "." + action, argName, argValue);
    }

    protected static String actionUrl(String action, String argName, Object argValue) {
        Map<String, Object> args = Maps.newHashMap();
        if (argName != null) {
            args.put(argName, argValue);
        }
        return Router.reverse(action, args).url;
    }

    protected static String actionUrl(String action) {
        return actionUrl(action, null, null);
    }

    protected static String actionUrl(Class<? extends Controller> controller, String action) {
        return actionUrl(controller, action, null, null);
    }

    protected static <T, V> List<OperationResult<T, V>> getSuccessResults(List<OperationResult<T, V>> results) {
        List<OperationResult<T, V>> success = Lists.newArrayList();
        for (OperationResult<T, V> result : results) {
            if (result.isSuccess()) {
                success.add(result);
            }
        }
        return success;
    }

    protected static <T, V> List<OperationResult<T, V>> getFailedResults(List<OperationResult<T, V>> results) {
        List<OperationResult<T, V>> failed = Lists.newArrayList();
        for (OperationResult<T, V> result : results) {
            if (!result.isSuccess()) {
                failed.add(result);
            }
        }
        return failed;
    }

    protected static <T, V> List<T> resultValues(List<OperationResult<T, V>> results) {
        List<T> values = Lists.newArrayList();
        for (OperationResult<T, V> result : results) {
            values.add(result.getValue());
        }
        return values;
    }

    protected static <T, V> List<V> resultArgs(List<OperationResult<T, V>> results) {
        List<V> args = Lists.newArrayList();
        for (OperationResult<T, V> result : results) {
            args.add(result.getArg());
        }
        return args;
    }

    protected static <T, V> List<String> errorMessages(List<OperationResult<T, V>> results) {
        List<String> messages = Lists.newArrayList();
        for (OperationResult<T, V> result : results) {
            if (!result.isSuccess()) {
                messages.add(result.getErrorMessage());
            }
        }
        return messages;
    }

    protected static <T, V> OperationResult<T, V> perform(V arg, ResourceOperation<T, V> operation) {
        try {
            T result = operation.performOperation(arg);
            return new OperationResult<T, V>(arg, result);
        } catch (Exception e) {
            Logger.error(e, Common.getUserMessage(e));
            return new OperationResult<T, V>(arg, e);
        }
    }

    protected static <T, V> List<OperationResult<T, V>> perform(List<V> args, ResourceOperation<T, V> operation) {
        List<OperationResult<T, V>> results = Lists.newArrayList();
        for (V arg : args) {
            OperationResult<T, V> result = perform(arg, operation);
            results.add(result);
        }
        return results;
    }

    protected static <T> List<OperationResult<T, URI>> perform(String[] ids, ResourceIdOperation<T> operation) {
        if (ids != null) {
            return perform(uris(ids), operation);
        }
        else {
            return Lists.newArrayList();
        }
    }

    protected static <T, V> void performListJson(List<V> values, ResourceOperation<T, V> operation) {
        List<OperationResult<T, V>> results = perform(values, operation);

        List<T> items = resultValues(getSuccessResults(results));
        String errorMessage = StringUtils.join(errorMessages(results), "\n");
        renderJSON(DataTablesSupport.createJSON(items, params, errorMessage));
    }

    protected static <T, V> void performItemsJson(List<V> values, ResourceOperation<T, V> operation) {
        List<OperationResult<T, V>> results = perform(values, operation);

        List<T> items = resultValues(getSuccessResults(results));
        renderJSON(items);
    }

    /**
     * Performs an operation on a list of values and logs successes.
     *
     * @param values
     *            the values for the operation.
     * @param operation
     *            the operation to run.
     * @param successKey
     *            the success message key.
     */
    protected static <T, V> void performSuccess(List<V> values, ResourceOperation<T, V> operation, String successKey) {
        List<OperationResult<T, V>> results = perform(values, operation);
        List<OperationResult<T, V>> success = getSuccessResults(results);

        if (!success.isEmpty()) {
            flash.success(MessagesUtils.get(successKey, success.size(), results.size()));
        }
    }

    /**
     * Performs an operation on a list of values and logs success/failures.
     *
     * @param values
     *            the values for the operation.
     * @param operation
     *            the operation to run.
     * @param successKey
     *            the success message key.
     * @param failedKey
     *            the failed message key.
     */
    protected static <T, V> void performSuccessFail(List<V> values, ResourceOperation<T, V> operation,
            String successKey, String failedKey) {
        List<OperationResult<T, V>> results = perform(values, operation);
        List<OperationResult<T, V>> failed = getFailedResults(results);

        if (failed.isEmpty()) {
            flash.success(MessagesUtils.get(successKey, results.size(), results.size()));
        }
        else {
            int total = results.size();
            int success = total - failed.size();
            String errorMessage = StringUtils.join(errorMessages(results), "\n");
            flash.error(MessagesUtils.get(failedKey, success, total, errorMessage));
        }
    }

    public static interface ResourceOperation<T, V> {
        public T performOperation(V arg) throws Exception;
    }

    /**
     * Operation on a resource by ID.
     *
     * @param <T>
     *            the result type.
     */
    public static interface ResourceIdOperation<T> extends ResourceOperation<T, URI> {
    }

    /**
     * Operation on a resource by value.
     *
     * @param <T>
     *            the result type
     * @param <V>
     *            the value type.
     */
    public static interface ResourceValueOperation<T, V extends DataObjectRestRep> extends ResourceOperation<T, V> {
    }

    public static class OperationResult<T, V> {
        private V arg;
        private T value;
        private Exception error;

        public OperationResult(V arg, T value) {
            this.arg = arg;
            this.value = value;
        }

        public OperationResult(V arg, Exception error) {
            this.arg = arg;
            this.error = error;
        }

        public boolean isSuccess() {
            return error == null;
        }

        public boolean isFailure() {
            return error != null;
        }

        public V getArg() {
            return arg;
        }

        public T getValue() {
            return value;
        }

        public Exception getError() {
            return error;
        }

        public String getErrorMessage() {
            return Common.getUserMessage(error);
        }
    }

    protected static JsonObject getCookieAsJson(String cookieKey) {
        if (cookieKey != null) {
            try {
                Http.Cookie cookie = request.cookies.get(cookieKey);
                if (cookie != null) {
                    JsonElement jelement = new JsonParser().parse(URLDecoder.decode(cookie.value, "UTF-8"));
                    if (jelement != null) {
                        return jelement.getAsJsonObject();
                    }
                }
            } catch (Exception e) {
                Logger.error(e, "Failed to load '%s'", cookieKey);
            }
        }
        return new JsonObject();
    }

    @SuppressWarnings("deprecation")
	protected static void saveJsonAsCookie(String cookieKey,JsonObject jobject) {
        response.setCookie(cookieKey, URLEncoder.encode(jobject.toString()));
    }
}
