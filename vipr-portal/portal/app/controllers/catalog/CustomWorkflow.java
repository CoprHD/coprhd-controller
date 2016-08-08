package controllers.catalog;

import play.mvc.Controller;
import play.mvc.With;
import controllers.Common;
import controllers.deadbolt.Restrict;
import controllers.deadbolt.Restrictions;
import controllers.tenant.TenantSelector;

@With(Common.class)
@Restrictions({ @Restrict("TENANT_ADMIN") })
public class CustomWorkflow extends Controller {

	public static void canvas() {
		TenantSelector.addRenderArgs();
		render();
	}

}
