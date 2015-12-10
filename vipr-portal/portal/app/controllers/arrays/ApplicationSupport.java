/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.arrays;

import static controllers.Common.flashException;

import java.net.URI;
import java.util.List;
import java.util.Set;

import models.datatable.ApplicationSupportDataTable;
import static com.emc.vipr.client.core.util.ResourceUtils.uri;
import org.apache.commons.lang.StringUtils;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.data.validation.Valid;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.AppSupportUtil;
import util.MessagesUtils;
import util.StringOption;
import util.datatable.DataTablesSupport;

import com.emc.storageos.model.application.ApplicationRestRep;
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
    
    protected static final String SAVED_SUCCESS = "application.save.success";
    protected static final String UNKNOWN = "Applications.unknown";
    
    public static void list() {
        ApplicationSupportDataTable dataTable = new ApplicationSupportDataTable();
        render(dataTable);
    }
    
    public static void listJson() {
        List<ApplicationSupportDataTable.ApplicationSupport> applications = ApplicationSupportDataTable.fetch();
        renderJSON(DataTablesSupport.createJSON(applications, params));
    }
    
    public static void create() {
        renderArgs.put("roleOptions",StringOption.options(new String[] { "COPY", "DR",}));
        render();
    }

    public static void cancel() {
        list();
    }

    public static void edit(String id) {
       ApplicationRestRep application = AppSupportUtil.getApplication(uri(id));
       if (application != null) {
           ApplicationForm applicationForm = new ApplicationForm(application);
           edit(applicationForm);
       }
       else {
           flash.error(MessagesUtils.get(UNKNOWN, id));
           list();
       }
    }
    
    private static void edit(ApplicationForm applicationForm) {
        renderArgs.put("roleOptions",StringOption.options(new String[] { "COPY", "DR",}));
        render("@create",applicationForm);
    }

    public static void delete(@As(",") String[] ids) {
        if (ids != null && ids.length > 0) {
            boolean deleteExecuted = false;
            for (String application : ids) {
                AppSupportUtil.deleteApplication(uri(application));
                deleteExecuted = true;
            }
            if (deleteExecuted == true) {
                flash.success(MessagesUtils.get("applications.deleted"));
            }
        }
        list();
    }
    
    @FlashException(keep = true, referrer = { "create", "edit" })
    public static void save(ApplicationForm applicationForm) {
        applicationForm.validate("applicationForm");
        if (Validation.hasErrors()) {
            Common.handleError();
        }
        try {
            applicationForm.save();
            flash.success(MessagesUtils.get(SAVED_SUCCESS, applicationForm.name));
            backToReferrer();
        } catch (ViPRException e) {
            flashException(e);
            error(applicationForm);
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
    
    private static void error(ApplicationForm applicationForm) {
        params.flash();
        Validation.keep();
        edit(applicationForm);
    }
    
    public static class ApplicationForm {

        public String id;
        @MaxSize(128)
        @MinSize(2)
        @Required
        public String name;

        public String description;
        
        public Set<String> roles;
        

        public ApplicationForm(ApplicationRestRep applicationForm) {
            this.id = applicationForm.getId().toString();
            this.name = applicationForm.getName();
            this.description = applicationForm.getDescription();
            this.roles = applicationForm.getRoles();
        }

        public boolean isNew() {
            return StringUtils.isBlank(id);
        }

        public void validate(String fieldname) {
            Validation.valid(fieldname, this);
        }

        public void save() throws ViPRException {
            if (isNew()) {
                AppSupportUtil.createApplication(name, description, roles);
            } else {
                ApplicationRestRep oldApplication = AppSupportUtil.getApplication(uri(id));
                if(oldApplication.getName().equals(name)) {
                    this.name = "";
                }
                if(oldApplication.getDescription().equals(description)) {
                    this.description = "";
                }
                AppSupportUtil.updateApplication(name, description, id);
            }

        }
        
    }
}
