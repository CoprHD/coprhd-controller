
package controllers.catalog;

import controllers.Common;
import play.mvc.Controller;
import play.mvc.With;

/**
 * @author Nick Aquino
 */
@With(Common.class)
public class WorkflowBuilder extends Controller {

    public static void view() {
        render();
    }


}
