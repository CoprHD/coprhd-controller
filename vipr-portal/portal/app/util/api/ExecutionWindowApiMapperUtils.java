/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package util.api;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static util.api.ApiMapperUtils.reverse;

import java.net.URI;
import java.util.List;

import com.emc.storageos.api.service.impl.response.RestLinkFactory;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.RelatedResourceRep;
import com.emc.storageos.model.ResourceTypeEnum;
import com.emc.storageos.model.RestLinkRep;
import com.emc.vipr.model.catalog.ExecutionWindowInfo;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.Reference;
import com.google.common.collect.Lists;

public class ExecutionWindowApiMapperUtils {

    public static ExecutionWindowInfo newExecutionWindowInfo(ExecutionWindowRestRep executionWindow) {
        ExecutionWindowInfo result = null;
        if (executionWindow != null) {
            result = new ExecutionWindowInfo();
            if (executionWindow.getId() != null) {
                result.setId(executionWindow.getId().toString());
            }

            result.setLabel(executionWindow.getName());
            result.setDayOfMonth(executionWindow.getDayOfMonth());
            result.setDayOfWeek(executionWindow.getDayOfWeek());
            result.setExecutionWindowLength(executionWindow.getExecutionWindowLength());
            result.setExecutionWindowLengthType(executionWindow.getExecutionWindowLengthType());
            result.setExecutionWindowType(executionWindow.getExecutionWindowType());
            result.setHourOfDayInUTC(executionWindow.getHourOfDayInUTC());
            result.setLastDayOfMonth(executionWindow.getLastDayOfMonth());
            result.setMinuteOfHourInUTC(executionWindow.getMinuteOfHourInUTC());
            result.setTenant(executionWindow.getTenant().getId().toString());
        }
        return result;
    }

    public static List<Reference> newExecutionWindowReferenceList(List<ExecutionWindowRestRep> executionWindows) {
        List<Reference> result = Lists.newArrayList();
        for (ExecutionWindowRestRep window : executionWindows) {
            result.add(new Reference(window.getId().toString(), executionWindowUrl(window.getId().toString())));
        }

        return result;
    }

    public static void copyExecutionWindowInfoToExecutionWindow(ExecutionWindowInfo info, ExecutionWindowRestRep window) {
        if (window != null) {
            if (info != null) {
                window.setName(info.getLabel());
                window.setDayOfMonth(info.getDayOfMonth());
                window.setDayOfWeek(info.getDayOfWeek());
                window.setExecutionWindowLength(info.getExecutionWindowLength());
                window.setExecutionWindowLengthType(info.getExecutionWindowLengthType());
                window.setExecutionWindowType(info.getExecutionWindowType());
                window.setHourOfDayInUTC(info.getHourOfDayInUTC());
                window.setLastDayOfMonth(info.getLastDayOfMonth());
                window.setMinuteOfHourInUTC(info.getMinuteOfHourInUTC());
                window.setTenant(toRelatedResource(ResourceTypeEnum.TENANT, uri(info.getTenant())));
            }
        } else {
            throw new IllegalArgumentException("ExecutionWindow cannot be null");
        }

    }

    public static RelatedResourceRep toRelatedResource(ResourceTypeEnum type, URI id) {
        if (NullColumnValueGetter.isNullURI(id)) {
            return null;
        }
        return new RelatedResourceRep(id, toLink(type, id));
    }

    public static RestLinkRep toLink(ResourceTypeEnum type, URI id) {
        return new RestLinkRep("self", RestLinkFactory.newLink(type, id));
    }

//    public static ExecutionWindowRestRep updateExecutionWindow(ExecutionWindowInfo info) {
//        ExecutionWindowRestRep window = new ExecutionWindowRestRep();
//        if (info != null) {
//
//            // window.setId(new URI(info.getId()));
//
//        }
//
//        return window;
//    }

    private static String executionWindowUrl(String id) {
        return reverse("api.ExecutionWindowsApi.executionwindow", "executionWindowId", id);
    }
}
