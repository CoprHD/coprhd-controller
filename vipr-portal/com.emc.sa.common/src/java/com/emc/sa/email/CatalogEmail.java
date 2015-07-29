/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.email;

public abstract class CatalogEmail extends TemplateBase {

    private static final String EMAIL_LAYOUT_TEMPLATE = readEmailLayoutTemplate();

    private static final String TITLE = "title";
    private static final String BODY = "body";

    public CatalogEmail() {
    }

    public void setTitle(String title) {
        setParameter(TITLE, title);
    }

    public String getTitle() {
        return getParameter(TITLE);
    }

    public String getEmailContent() {
        setParameter(BODY, getBodyContent());
        return evaluate(EMAIL_LAYOUT_TEMPLATE);
    }

    private static String readEmailLayoutTemplate() {
        return readTemplate("EmailLayout.html");
    }

    public abstract String getBodyContent();

}
