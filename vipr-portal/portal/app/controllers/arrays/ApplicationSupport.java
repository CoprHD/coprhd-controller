/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static controllers.Common.flashException;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import models.datatable.ApplicationSupportDataTable;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;

import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.AppSupportUtil;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.application.VolumeGroupRestRep;
import com.emc.vipr.client.exceptions.ViPRException;

import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.util.FlashException;

/**
 * @author hr2
 *
 */

@With(Common.class)
@Restrictions({ @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class ApplicationSupport extends Controller {
    
    protected static final String SAVED_SUCCESS = "applicationGroup.save.success";
    protected static final String UNKNOWN = "applicationGroup.unknown";
    protected static final Set<String> ROLE = new HashSet(Arrays.asList(new String[] {"COPY"}));
    
    public static void list() {
        ApplicationSupportDataTable dataTable = new ApplicationSupportDataTable();
        render(dataTable);
    }
    
    public static void listJson() {
        List<ApplicationSupportDataTable.ApplicationSupport> applications = ApplicationSupportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(applications, params));
    }
    
    public static void create() {
        render();
    }

    public static void cancel() {
        list();
    }

    public static void edit(String id) {
        VolumeGroupRestRep application = AppSupportUtil.getApplication(id);
       if (application != null) {
           ApplicationForm applicationGroup = new ApplicationForm(application);
           edit(applicationGroup);
       }
       else {
           flash.error(MessagesUtils.get(UNKNOWN, id));
           list();
       }
    }
    
    private static void edit(ApplicationForm applicationGroup) {
        render("@create",applicationGroup);
    }

    public static void delete(@As(",") String[] ids) {
        try {
        		if (ids != null && ids.length > 0) {
        		boolean deleteExecuted = false;
        		for (String application : ids) {
        			AppSupportUtil.deleteApplication(uri(application));
        			deleteExecuted = true;
        		}
        		if (deleteExecuted == true) {
        			flash.success(MessagesUtils.get("applicationGroup.deleted"));
        		}
        	}
        } catch(ViPRException e) {
        	flashException(e);
        }
        list();
    }
    
    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ApplicationForm applicationGroup) {
        applicationGroup.validate("applicationGroup");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        try {
            applicationGroup.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, applicationGroup.name));
            backToReferrer();
        } catch (ViPRException e) {
            flashException(e);
            error(applicationGroup);
        }
    }
    
    private static void backToReferrer() {
        String referrer = Common.getReferrer();
        if (StringUtils.isNotBlank(referrer)) {
            redirect(referrer);
        } else {
            list();
        }
    }
    
    private static void error(ApplicationForm applicationGroup) {
        params.flash();
        Validation.keep();
        edit(applicationGroup);
    }
    
    public static class ApplicationForm {

        public String id;
        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        public String description;
        
        public Set<String> roles;
        

        public ApplicationForm(VolumeGroupRestRep applicationGroup) {
            this.id = applicationGroup.getId().toString();
            this.name = applicationGroup.getName();
            this.description = applicationGroup.getDescription();
            this.roles = applicationGroup.getRoles();
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldname) {
            Validation.valid(fieldname, this);
        }

        public void save() throws ViPRException {
            if (isNew()) {
                AppSupportUtil.createApplication(name, description, ROLE);
            } else {
                VolumeGroupRestRep oldApplication = AppSupportUtil.getApplication(id);
                if(oldApplication.getName().equals(name)) {
                    this.name = "";
                }
                if(oldApplication.getDescription() != null && oldApplication.getDescription().equals(description)) {
                    this.description = "";
                }
                AppSupportUtil.updateApplication(name, description, id);
            }

        }
        
    }
}
