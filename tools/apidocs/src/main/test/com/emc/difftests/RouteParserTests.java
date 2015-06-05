package com.emc.difftests;

import com.emc.apidocs.model.ApiMethod;
import com.emc.apidocs.model.ApiService;
import com.emc.apidocs.processing.PlayRoutesParser;

import junit.framework.TestCase;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Collection;

public class RouteParserTests  {

    @Test
    @Ignore
    public void testPlayParser() {
        Collection<ApiService> services = PlayRoutesParser.getPortalServices("/Users/maddid/SourceCode/bourne/isa/portal");

        boolean executionWindowsFound = false;
        boolean approvalsFound = false;
        for (ApiService service : services) {
//            System.out.println(service.path+"  "+service.getTitle());
            for (ApiMethod method : service.methods) {
//                System.out.println(method.httpMethod+"  "+method.path+"  "+method.getTitle());
                if (method.path.equals("/admin/api/executionwindows")) {
                    executionWindowsFound = true;  // An admin service found
                }

                if (method.path.equals("/api/approvals")) {
                    approvalsFound = true; // a Non Admin service Found
                }
            }
        }

        TestCase.assertTrue(executionWindowsFound);
        TestCase.assertTrue(approvalsFound);
    }
}
