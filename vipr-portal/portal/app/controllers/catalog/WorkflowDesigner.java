/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package controllers.catalog;

import controllers.Common;
import play.mvc.Controller;
import play.mvc.With;

/**
 * @author Chris Dail
 */
@With(Common.class)
public class WorkflowDesigner extends Controller {

    public static void view() {
        render();
    }


}
