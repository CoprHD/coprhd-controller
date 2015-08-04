/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.infra;

import com.emc.storageos.model.TaskResourceRep;
import controllers.Tasks;
import play.Logger;
import play.data.validation.Email;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.data.validation.Validation;
import play.mvc.Controller;
import play.mvc.With;
import util.MessagesUtils;
import util.SupportUtils;
import util.UserPreferencesUtils;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN"), @Restrict("SYSTEM_ADMIN"), @Restrict("RESTRICTED_SYSTEM_ADMIN") })
public class SupportRequest extends Controller {

    private static String SUCCESS_KEY = "supportRequest.successful";

    private static String ERROR_KEY = "supportRequest.error";

    private static String COMMENT_TEMPLATE_KEY = "supportRequest.comment.template";

    /** Default time to include logs (15 minutes). */
    private static final long DEFAULT_LOG_TIME = 15 * 60 * 1000;

    public static void createSupportRequest() {
        SupportRequestForm supportRequest = new SupportRequestForm();
        supportRequest.loadDefaults();
        render(supportRequest);
    }

    public static void submitSupportRequest(SupportRequestForm supportRequest) {
        supportRequest.validate();
        if (Validation.hasErrors()) {
            params.flash();
            Validation.keep();
            createSupportRequest();
        }

        TaskResourceRep task = null;
        try {
            task = submit(supportRequest);
            flash.put("info", MessagesUtils.get(SUCCESS_KEY));
        } catch (RuntimeException e) {
            flash.error(MessagesUtils.get(ERROR_KEY, e.getMessage()));
            Logger.error(e, e.getMessage());
            createSupportRequest();
        }
        Tasks.details(task.getId().toString());
    }

    protected static TaskResourceRep submit(SupportRequestForm supportRequest) {
        return SupportUtils.submitSupportRequest(supportRequest.email, supportRequest.comment, supportRequest.start,
                supportRequest.end);
    }

    public static class SupportRequestForm {

        @Required
        @Email
        public String email;

        @MaxSize(1000)
        public String comment;

        public long start;

        public long end;

        public boolean endIsCurrentTime;

        public SupportRequestForm() {
        }

        public void loadDefaults() {
            email = UserPreferencesUtils.getEmail();
            comment = MessagesUtils.get(COMMENT_TEMPLATE_KEY);
            start = System.currentTimeMillis() - DEFAULT_LOG_TIME;
            end = System.currentTimeMillis();
            endIsCurrentTime = true;
        }

        public void validate() {
            Validation.valid("supportRequest", this);
            if (start <= 0) {
                Validation.addError("supportRequest.start", "validation.required");
            }
            if (end <= 0) {
                Validation.addError("supportRequest.end", "validation.required");
            }
            if (end < start) {
                String error = "supportRequest.error.endBeforeStart";
                Validation.addError("supportRequest.start", error);
                Validation.addError("supportRequest.end", error);
            }
        }
    }

}
