/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.api;

import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import static render.RenderApiModel.renderApi;
import static util.api.ApiMapperUtils.getValidationErrors;
import static util.api.ExecutionWindowApiMapperUtils.copyExecutionWindowInfoToExecutionWindow;
import static util.api.ExecutionWindowApiMapperUtils.newExecutionWindowInfo;
import static util.api.ExecutionWindowApiMapperUtils.newExecutionWindowReferenceList;

import java.net.URI;
import java.util.Date;
import java.util.List;

import org.apache.http.HttpStatus;

import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.With;
import util.ExecutionWindowUtils;
import util.TimeUtils;

import com.emc.vipr.model.catalog.ExecutionWindowCommonParam;
import com.emc.vipr.model.catalog.ExecutionWindowCreateParam;
import com.emc.vipr.model.catalog.ExecutionWindowInfo;
import com.emc.vipr.model.catalog.ExecutionWindowRestRep;
import com.emc.vipr.model.catalog.ExecutionWindowUpdateParam;
import com.emc.vipr.model.catalog.Reference;
import com.emc.vipr.model.catalog.ValidationError;

import controllers.Common;
import controllers.catalog.ExecutionWindows.ExecutionWindowForm;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class ExecutionWindowsApi extends Controller {

    // list all execution windows
    public static void executionwindows() {

        List<Reference> windows = newExecutionWindowReferenceList(ExecutionWindowUtils.getExecutionWindows());
        renderApi(windows);
    }

    public static void executionwindow(String executionWindowId) {

        ExecutionWindowInfo window = newExecutionWindowInfo(ExecutionWindowUtils
                .getExecutionWindow(uri(executionWindowId)));
        if (window != null) {
            renderApi(window);
        } else {
            notFound(Messages.get("ExecutionWindowsApi.windowWithId", executionWindowId));
        }

    }

    private static ExecutionWindowForm createForm(ExecutionWindowRestRep model) {
        ExecutionWindowForm form = new ExecutionWindowForm();

        if (model.getId() != null) {
            form.id = model.getId().toString();
        }

        Date d = new Date();
        form.timezoneOffsetInMinutes = d.getTimezoneOffset();
        form.name = model.getName();

        if (model.getHourOfDayInUTC() != null) {
            form.hourOfDay = TimeUtils.getLocalHourOfDay(model.getHourOfDayInUTC(), form.timezoneOffsetInMinutes);
        }

        form.length = model.getExecutionWindowLength();
        form.lengthType = model.getExecutionWindowLengthType();
        form.type = model.getExecutionWindowType();
        form.dayOfWeek = model.getDayOfWeek();
        if (model.getLastDayOfMonth() != null && model.getLastDayOfMonth().booleanValue() == true) {
            form.dayOfMonth = form.LAST_DAY_OF_MONTH;
        } else if (model.getDayOfMonth() != null) {
            form.dayOfMonth = model.getDayOfMonth().toString();
        }

        return form;
    }

    public static void create(ExecutionWindowInfo info) {

        ExecutionWindowRestRep window = new ExecutionWindowRestRep();
        copyExecutionWindowInfoToExecutionWindow(info, window);

        createForm(window).validate("executionWindowForm");
        if (validation.hasErrors()) {
            response.status = HttpStatus.SC_BAD_REQUEST;
            renderApi(getValidationErrors());
        } else {
            ExecutionWindowCreateParam createParam = new ExecutionWindowCreateParam();
            map(createParam, info);
            
            ExecutionWindowUtils.createExecutionWindow(createParam);
            info.setId(window.getId().toString());
            renderApi(info);
        }

    }

    public static void update(String executionWindowId, ExecutionWindowInfo info) {
        ExecutionWindowRestRep window = ExecutionWindowUtils.getExecutionWindow(uri(executionWindowId));
        if (window != null) {
            copyExecutionWindowInfoToExecutionWindow(info, window);
            window.setId(URI.create(executionWindowId));

            createForm(window).validate("executionWindowForm");
            if (validation.hasErrors()) {
                response.status = HttpStatus.SC_BAD_REQUEST;
                ValidationError validationError = null;
                renderApi(getValidationErrors());

            } else {
                ExecutionWindowUpdateParam updateParam = new ExecutionWindowUpdateParam();
                map(updateParam, info);
                
                ExecutionWindowUtils.updateExecutionWindow(uri(executionWindowId), updateParam);
                info.setId(window.getId().toString());
                renderApi(info);
            }
        } else {
            notFound(Messages.get("ExecutionWindowsApi.windowWithId", executionWindowId));
        }
    }

    public static void delete(String executionWindowId) {
        ExecutionWindowRestRep window = ExecutionWindowUtils.getExecutionWindow(uri(executionWindowId));
        if (window != null) {
            ExecutionWindowUtils.deleteExecutionWindow(window);
            renderApi(executionWindowId);
        } else {
            notFound(Messages.get("ExecutionWindowsApi.windowWithId", executionWindowId));
        }

    }
    
    private static void map(ExecutionWindowCommonParam commonParam, ExecutionWindowInfo info) {
        commonParam.setDayOfMonth(info.getDayOfMonth());
        commonParam.setDayOfWeek(info.getDayOfWeek());
        commonParam.setExecutionWindowLength(info.getExecutionWindowLength());
        commonParam.setExecutionWindowLengthType(info.getExecutionWindowLengthType());
        commonParam.setExecutionWindowType(info.getExecutionWindowType());
        commonParam.setHourOfDayInUTC(info.getHourOfDayInUTC());
        commonParam.setLastDayOfMonth(info.getLastDayOfMonth());
        commonParam.setMinuteOfHourInUTC(info.getMinuteOfHourInUTC());        
    }

}
