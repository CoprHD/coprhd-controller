/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013 EMC Corporation 
 * All Rights Reserved 
 *
 * This software contains the intellectual property of EMC Corporation 
 * or is licensed to EMC Corporation from third parties.  Use of this 
 * software and the intellectual property contained therein is expressly 
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.apidiff.serializer;

import com.emc.storageos.apidiff.util.Pair;

/**
 * Associated class to construct HTML format contents. Puts all HTML related tags here, including HEAD,
 * BODY, TABLE, DIV, UL and so on. Therefore, we can avoid to write any HTML format code in outside. This
 * will help us reduce complexity of presentation and clear code structure.
 */
class HtmlSerializerHelper {

    private HtmlSerializerHelper() {}

    static String buildHeader(final String title) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!DOCTYPE HTML>\n");
        builder.append("<HTML>\n");
        builder.append("  <HEAD>\n");
        builder.append("    <meta charset=\"utf-8\">\n");
        builder.append("    <meta content=\"text/html;charset=UTF-8\" http-equiv=\"content-type\">\n");
        builder.append("    <meta content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0\" name=\"viewport\">\n");
        builder.append("    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=EDGE; IE=9; IE=8\" />\n");
        builder.append("    <TITLE>").append(title).append("</TITLE>\n");
        builder.append("    <link href=\"static/bootstrap.min.css\" type=\"text/css\" rel=\"stylesheet\"/>\n");
        builder.append("    <link href=\"static/custom.css\" type=\"text/css\" rel=\"stylesheet\"/>\n");
        builder.append("  </HEAD>\n");
        builder.append("  <BODY style=\"font-family:arial;\">\n");
        return builder.toString();
    }

    static String buildTailer() {
        return "  </BODY>\n</HTML>\n";
    }

    static String buildBodyTitle(final String title, final String subTitle) {
        StringBuilder builder = new StringBuilder();
        builder.append("    <CENTER>\n");
        builder.append("      <H1>").append(title).append("</H1>\n");
        if (subTitle != null && subTitle.length() > 0)
            builder.append("      <H2>").append(subTitle).append("</H2>\n");
        builder.append("    </CENTER>\n");
        return builder.toString();
    }

    static String buildTableHeader() {
        return "    <TABLE class=\"payload table table-striped\" BORDER=\"1\" CELLPADDING=\"3\" CELLSPACING=\"0\" WIDTH=\"100%\">\n";
    }

    static String buildTableTailer() {
        return "    </TABLE>";
    }

    static String buildTableHeaderRow(int colSpan, Pair<?, ?>... columns) {
        StringBuilder builder = new StringBuilder();
        builder.append("      <THREAD>\n");
        builder.append("      <TR BGCOLOR=\"#CCCCFF\">\n");
        int i;
        for (i = 0; i < columns.length; i++) {
            builder.append("        <TD ALIGN=\"LEFT\" VALIGN=\"TOP\" WIDTH=\"");
            builder.append(columns[i].getRight()).append("%\"");
            if (i == columns.length - 1)
                builder.append(" COLSPAN=\"").append(colSpan).append("\"");
            builder.append(">");
            builder.append("<FONT SIZE=\"+1\"><B>").append(columns[i].getLeft()).append("</B></FONT></TD>\n");
        }
        builder.append("      </TR>\n");
        builder.append("      </THREAD>\n");
        builder.append("      <tbody>");
        return builder.toString();
    }

    static String buildTableRow(int colSpan, Pair<?, ?>... columns) {
        StringBuilder builder = new StringBuilder();
        builder.append("        <TR BGCOLOR=\"#FFFFFF\">\n");
        if (columns == null) {
            builder.append("        </TR>\n");
            return builder.toString();
        }

        builder.append("        <TH ALIGN=\"LEFT\" VALIGN=\"TOP\" WIDTH=\"");
        builder.append(columns[0].getRight()).append("%\">");
        builder.append("<B>").append(columns[0].getLeft()).append("</B></TH>\n");

        int i;
        for (i = 1; i < columns.length; i++) {
            builder.append("          <TD ALIGN=\"LEFT\" VALIGN=\"TOP\" WIDTH=\"");
            builder.append(columns[i].getRight());
            builder.append("%\"");
            if (i == columns.length - 1)
                builder.append(" COLSPAN=\"").append(colSpan).append("\"");
            builder.append(">");
            builder.append(columns[i].getLeft()).append("</TD>\n");
        }
        builder.append("        </TR>\n");

        return builder.toString();
    }

    static String buildDivHeader(final String id) {
        return "    <P>\n" + "    <DIV id=\"" + id + "\">\n";
    }

    static String buildDivTailer() {
        return "    </DIV>";
    }

    static String buildContent(final String content, final String ttContent, final int level) {
        return String.format("<H%d> %s  <tt>%s</tt> </H%d>\n", level, content, ttContent, level);
    }

    static String buildInPageLink(final String link) {
        return String.format("<A HREF=\"#%s\"> %s </A>", link, link);
    }

    static String buildLink(final String linkPage, final String content) {
        return String.format("<A HREF=\"%s\"> %s </A>", linkPage, content);
    }


    static String buildListHeader() {
        return "    <UL>\n";
    }

    static String buildListTailer() {
        return "    </UL>\n";
    }

    static String buildListItem(final String item) {
        return String.format("      <LI> %s </LI>\n", item);
    }

    static String buildBodyHeader() {
        StringBuilder builder = new StringBuilder();
        builder.append("<div id=\"mainbar\">\n");
        builder.append("<img src=\"static/EMCLogo-Small.png\" class=\"logo\"width=\"100px\"/>\n");
        builder.append("<span class=\"mainTitle\">: ViPR REST API</span>\n");
        builder.append("<img src=\"static/emc_vipr-small.jpg\" class=\"vipr_logo\" width=\"92\"/>\n");
        builder.append("</div>\n");
        return builder.toString();
    }

    static String buildBlankLine() {
        return "<br>\n";
    }

    static String buildSideLine() {
        return "<hr/>\n";
    }

}
