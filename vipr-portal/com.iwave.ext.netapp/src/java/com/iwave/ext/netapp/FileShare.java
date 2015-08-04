/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import netapp.manage.NaAPIFailedException;
import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.netapp.model.CifsAccess;
import com.iwave.ext.netapp.model.CifsAcl;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.SecurityRuleInfo;
import com.iwave.ext.netapp.utils.ExportRule;

/**
 * @author sdorcas
 * 
 */
@SuppressWarnings({ "findbugs:WMI_WRONG_MAP_ITERATOR" })
/*
 * Code change for iterator will be made in future release
 */
public class FileShare {

    private Logger log = Logger.getLogger(getClass());

    /** maximum number of shares that will be listed */
    private static final int MAX_LIST = 1024;
    private static final String ROOT_USER = "root";
    private static final String NO_ROOT_USERS = "nobody";

    private static final int DISABLE_ROOT_ACCESS_CODE = 65535;
    private static final int DEFAULT_ANONMOUS_ROOT_ACCESS = 65534;

    private String mountPath = "";
    private NaServer server = null;

    public FileShare(NaServer server, String mountPath)
    {
        this.server = server;
        this.mountPath = mountPath;
    }

    boolean addCIFSShare(String shareName, String comment, int maxusers, String forcegroup)
    {
        NaElement elem = new NaElement("cifs-share-add");
        elem.addNewChild("path", mountPath);
        elem.addNewChild("share-name", shareName);
        elem.addNewChild("comment", comment);

        if (maxusers > 0) {
            elem.addNewChild("maxusers", String.valueOf(maxusers));
        }

        if (forcegroup != null && !forcegroup.isEmpty()) {
            elem.addNewChild("forcegroup", forcegroup);
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to create CIFS share on path: " + mountPath;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
        return true;
    }

    void deleteCIFSShare(String shareName)
    {
        NaElement elem = new NaElement("cifs-share-delete");
        elem.addNewChild("share-name", shareName);

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete CIFS share: " + shareName;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    void changeCIFSShare(String shareName, Map<String, String> attrs)
    {
        NaElement elem = new NaElement("cifs-share-change");
        elem.addNewChild("share-name", shareName);

        for (String key : attrs.keySet()) {
            elem.addNewChild(key, attrs.get(key));
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to change CIFS share: " + shareName + " attrs: " + attrs;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, String>> listCIFSInfo(String shareName)
    {
        ArrayList<Map<String, String>> shares = new ArrayList<Map<String, String>>();
        NaElement elem = new NaElement("cifs-share-list-iter-start");

        if (shareName != null && !shareName.isEmpty()) {
            elem.addNewChild("share-name", shareName);    // shareName can contain wildcards * or ?
        }

        try {
            NaElement result = server.invokeElem(elem);
            String tag = result.getChildContent("tag");

            elem = new NaElement("cifs-share-list-iter-next");
            elem.addNewChild("tag", tag);
            elem.addNewChild("maximum", MAX_LIST + "");
            List<NaElement> shareElems = server.invokeElem(elem).getChildByName("cifs-shares").getChildren();

            for (NaElement shareElem : shareElems) {
                // {description=Testing, maxusers=5, share-name=demotest, mount-point=/vol/volscott}
                Map<String, String> share = new HashMap<String, String>();
                for (NaElement info : (List<NaElement>) shareElem.getChildren()) {
                    share.put(info.getName(), info.getContent());
                }
                shares.add(share);
            }

            elem = new NaElement("cifs-share-list-iter-end");
            elem.addNewChild("tag", tag);
            server.invokeElem(elem);

            return shares;
        } catch (Exception e) {
            String msg = "Failed to list CIFS shares.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    public void setCIFSAcl(CifsAcl acl)
    {
        NaElement elem = new NaElement("cifs-share-ace-set");
        elem.addNewChild("share-name", acl.getShareName());
        elem.addNewChild("access-rights", acl.getAccess().access());

        if (acl.getUserName() != null) {
            elem.addNewChild("user-name", acl.getUserName());
        }

        if (acl.getGroupName() != null) {
            elem.addNewChild("unix-group-name", acl.getGroupName());
            elem.addNewChild("is-unixgroup", "true");
        }

        try {
            server.invokeElem(elem);
        } catch (NaAPIFailedException e) {
            String msg = "Failed to set CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppException(msg, e, e.getErrno());
        } catch (Exception e) {
            String msg = "Failed to set CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    public void deleteCIFSAcl(CifsAcl acl)
    {
        NaElement elem = new NaElement("cifs-share-ace-delete");
        elem.addNewChild("share-name", acl.getShareName());

        if (acl.getUserName() != null) {
            elem.addNewChild("user-name", acl.getUserName());
        }

        if (acl.getGroupName() != null) {
            elem.addNewChild("unix-group-name", acl.getGroupName());
            elem.addNewChild("is-unixgroup", "true");
        }

        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    public List<CifsAcl> listCIFSAcls(String shareName) {
        List<CifsAcl> acls = new ArrayList<CifsAcl>();
        NaElement elem = new NaElement("cifs-share-acl-list-iter-start");

        if (shareName != null && !shareName.isEmpty()) {
            elem.addNewChild("share-name", shareName); // shareName can contain wildcards * or ?
        }

        try {
            NaElement result = server.invokeElem(elem);
            String tag = result.getChildContent("tag");

            elem = new NaElement("cifs-share-acl-list-iter-next");
            elem.addNewChild("tag", tag);
            elem.addNewChild("maximum", MAX_LIST + "");
            List<NaElement> shareElems = server.invokeElem(elem).getChildByName("cifs-share-acls").getChildren();

            for (NaElement shareElem : shareElems) {
                String name = shareElem.getChildContent("share-name");
                shareElem = shareElem.getChildByName("user-acl-info");

                // Note: can't use shareElem.getChildByName("access-rights-info");
                // as this only gets the first element
                List<NaElement> infos = shareElem.getChildren();

                for (NaElement info : infos) {
                    CifsAcl acl = new CifsAcl();
                    acl.setShareName(name);
                    String access = info.getChildContent("access-rights");
                    try {
                        acl.setAccess(CifsAccess.valueOfAccess(access));
                    } catch (IllegalArgumentException e) {
                        log.warn("Invalid permission for a CIFS share: " + name, e);
                        log.info("Continue with next acl");
                        continue;
                    }

                    String user = info.getChildContent("user-name");
                    String group = info.getChildContent("unix-group-name");

                    /*
                     * numeric groups are returned as users like: {gid:0}
                     */
                    if (user != null && user.startsWith("{gid:")) {
                        int brace = user.indexOf('}');
                        group = user.substring(5, brace);
                        user = null;
                    }

                    acl.setUserName(user);
                    acl.setGroupName(group);
                    acls.add(acl);
                }
            }

            elem = new NaElement("cifs-share-list-iter-end");
            elem.addNewChild("tag", tag);
            server.invokeElem(elem);

            return acls;
        } catch (Exception e) {
            String msg = "Failed to list CIFS ACLs.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, String>> listNFSInfo(String pathName)
    {
        ArrayList<Map<String, String>> exports = new ArrayList<Map<String, String>>();
        NaElement elem = new NaElement("nfs-exportfs-list-rules");

        // if true, returns entries from exports file; else from memory
        elem.addNewChild("persistent", String.valueOf(true));

        if (pathName != null && !pathName.isEmpty()) {
            elem.addNewChild("pathname", pathName);
        }

        try {
            List<NaElement> ruleElems = server.invokeElem(elem).getChildByName("rules").getChildren();

            for (NaElement ruleElem : ruleElems) {
                Map<String, String> export = new HashMap<String, String>();
                for (NaElement info : (List<NaElement>) ruleElem.getChildren()) {
                    String name = info.getName();
                    String value = info.getContent();
                    List<NaElement> children = (List<NaElement>) info.getChildren();

                    if (!children.isEmpty()) {
                        StringBuilder buf = new StringBuilder();
                        for (NaElement child : children) {
                            List<NaElement> grandchildren = (List<NaElement>) child.getChildren();
                            if (!grandchildren.isEmpty()) {
                                for (NaElement grandchild : grandchildren) {
                                    if (buf.length() > 0) {
                                        buf.append(",");
                                    }
                                    buf.append(grandchild.getContent());
                                }
                            }
                        }
                        value = buf.toString();
                        if (value.equals("true")) {
                            value = "All Hosts";
                        }
                    }

                    export.put(name, value);
                }
                exports.add(export);
            }

            return exports;
        } catch (Exception e) {
            String msg = "Failed to list NFS exports.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    @SuppressWarnings("unchecked")
    List<ExportsRuleInfo> listNFSExportRules(String pathName)
    {
        List<ExportsRuleInfo> exports = Lists.newArrayList();
        NaElement elem = new NaElement("nfs-exportfs-list-rules-2");

        // if true, returns entries from exports file; else from memory
        elem.addNewChild("persistent", String.valueOf(false));
        if (StringUtils.isNotBlank(pathName)) {
            elem.addNewChild("pathname", pathName);
        }

        try {
            NaElement results = server.invokeElem(elem);

            List<NaElement> rules = results.getChildByName("rules").getChildren();
            for (NaElement rule : rules) {

                ExportsRuleInfo exportsRuleInfo = new ExportsRuleInfo();
                exportsRuleInfo.setActualPathname(rule.getChildContent("actual-pathname"));
                exportsRuleInfo.setPathname(rule.getChildContent("pathname"));

                for (NaElement securityRule : (List<NaElement>) rule.getChildByName("security-rules").getChildren()) {
                    SecurityRuleInfo securityRuleInfo = new SecurityRuleInfo();
                    securityRuleInfo.setAnon(securityRule.getChildContent("anon"));
                    // String nonsuid = securityRule.getChildContent("nonsuid"); // This is not correct.. Modified by [Gopi] as per API.
                    String nonsuid = securityRule.getChildContent("nosuid");
                    if (StringUtils.isNotBlank(nonsuid)) {
                        securityRuleInfo.setNosuid(Boolean.parseBoolean(nonsuid));
                    }

                    List<NaElement> secFlavors = (List<NaElement>) securityRule.getChildByName("sec-flavor").getChildren();
                    for (NaElement secFlavor : secFlavors) {
                        if (secFlavor != null) {
                            if (securityRuleInfo.getSecFlavor() != null) {
                                securityRuleInfo.setSecFlavor(securityRuleInfo.getSecFlavor() + ","
                                        + secFlavor
                                                .getChildContent("flavor"));
                            } else {
                                securityRuleInfo.setSecFlavor(secFlavor
                                        .getChildContent("flavor"));
                            }
                        }
                    }

                    List<ExportsHostnameInfo> readOnly = extractExportsHostnameInfos(securityRule.getChildByName("read-only"));
                    securityRuleInfo.getReadOnly().addAll(readOnly);

                    List<ExportsHostnameInfo> readWrite = extractExportsHostnameInfos(securityRule.getChildByName("read-write"));
                    securityRuleInfo.getReadWrite().addAll(readWrite);

                    List<ExportsHostnameInfo> root = extractExportsHostnameInfos(securityRule.getChildByName("root"));
                    securityRuleInfo.getRoot().addAll(root);

                    exportsRuleInfo.getSecurityRuleInfos().add(securityRuleInfo);
                }
                exports.add(exportsRuleInfo);
            }

            return exports;
        } catch (Exception e) {
            String msg = "Failed to list NFS exports.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    private List<ExportsHostnameInfo> extractExportsHostnameInfos(NaElement elem) {
        List<ExportsHostnameInfo> exportsHostnameInfos = Lists.newArrayList();
        if (elem != null && elem.getChildren() != null) {
            for (NaElement exportsHostname : (List<NaElement>) elem.getChildren()) {
                ExportsHostnameInfo exportsHostnameInfo = new ExportsHostnameInfo();
                String allHosts = exportsHostname.getChildContent("all-hosts");
                if (StringUtils.isNotBlank(allHosts)) {
                    exportsHostnameInfo.setAllHosts(Boolean.parseBoolean(allHosts));
                }
                exportsHostnameInfo.setName(exportsHostname.getChildContent("name"));
                String negate = exportsHostname.getChildContent("negate");
                if (StringUtils.isNotBlank(negate)) {
                    exportsHostnameInfo.setNegate(Boolean.parseBoolean(negate));
                }
                exportsHostnameInfos.add(exportsHostnameInfo);
            }
        }
        return exportsHostnameInfos;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, String>> listNFSExports(String pathName)
    {
        ArrayList<Map<String, String>> exports = new ArrayList<Map<String, String>>();
        NaElement elem = new NaElement("nfs-exportfs-list-rules-2");

        // if true, returns entries from exports file; else from memory
        elem.addNewChild("persistent", String.valueOf(true));

        if (pathName != null && !pathName.isEmpty()) {
            elem.addNewChild("pathname", pathName);
        }

        try {
            List<NaElement> ruleElems = server.invokeElem(elem).getChildByName("rules").getChildren();

            for (NaElement ruleElem : ruleElems) {
                Map<String, String> export = new HashMap<String, String>();
                for (NaElement info : (List<NaElement>) ruleElem.getChildren()) {
                    String name = info.getName();
                    String value = info.getContent();
                    if ("security-rules".equalsIgnoreCase(name)) {
                        // Ignore, use another method call
                    }
                    else {
                        export.put(name, value);
                    }
                }
                exports.add(export);
            }

            return exports;
        } catch (Exception e) {
            String msg = "Failed to list NFS exports.";
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }
    }

    List<String> addNFSShare(String exportPath, int anonymousUid, List<String> roHosts,
            boolean roAddAll, List<String> rwHosts, boolean rwAddAll,
            List<String> rootHosts, boolean rootAddAll, List<NFSSecurityStyle> securityStyle)
    {
        NaElement elem = new NaElement("nfs-exportfs-append-rules");
        elem.addNewChild("persistent", String.valueOf(true));
        elem.addNewChild("verbose", String.valueOf(true));

        NaElement rules = new NaElement("rules");
        NaElement exportRule = new NaElement("exports-rule-info");

        exportRule.addNewChild("pathname", exportPath);

        if (mountPath != null) {
            exportRule.addNewChild("actual-pathname", mountPath);
        }

        if (anonymousUid > -1) {
            exportRule.addNewChild("anon", String.valueOf(anonymousUid));
        }
        // Add the host list if hosts are defined or addAll is true
        if (roAddAll || (roHosts != null && !roHosts.isEmpty())) {
            NaElement readOnly = new NaElement("read-only");
            addHosts(readOnly, roHosts, roAddAll);
            exportRule.addChildElem(readOnly);
        }
        if (rwAddAll || (rwHosts != null && !rwHosts.isEmpty())) {
            NaElement readWrite = new NaElement("read-write");
            addHosts(readWrite, rwHosts, rwAddAll);
            exportRule.addChildElem(readWrite);
        }
        if (rootAddAll || (rootHosts != null && !rootHosts.isEmpty())) {
            NaElement root = new NaElement("root");
            addHosts(root, rootHosts, rootAddAll);
            exportRule.addChildElem(root);
        }
        // Add security style
        if (securityStyle != null && !securityStyle.isEmpty()) {
            NaElement secStyle = new NaElement("sec-flavor");
            addSecurityStyles(securityStyle, secStyle);
            exportRule.addChildElem(secStyle);
        }
        // Add the export rule
        rules.addChildElem(exportRule);
        elem.addChildElem(rules);

        NaElement result = null;
        try {
            result = server.invokeElem(elem).getChildByName("loaded-pathnames");
        } catch (Exception e) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        // build the result
        ArrayList<String> pathnames = new ArrayList<String>();
        for (NaElement pathInfo : (List<NaElement>) result.getChildren()) {
            pathnames.add(pathInfo.getChildContent("name"));    // API doc is wrong here
        }

        if (!pathnames.contains(exportPath)) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            msg += ". Check directory exists in volume.";
            log.error(msg);
            throw new NetAppException(msg);
        }

        return pathnames;
    }

    List<String> deleteNFSShare(boolean deleteAll)
    {
        NaElement elem = new NaElement("nfs-exportfs-delete-rules");
        elem.addNewChild("persistent", String.valueOf(true));
        elem.addNewChild("verbose", String.valueOf(true));
        // Delete all NFS Shares
        if (deleteAll) {
            elem.addNewChild("all-pathnames", String.valueOf(true));
        }
        else {
            NaElement pathnames = new NaElement("pathnames");
            NaElement pathInfo = new NaElement("pathname-info");
            // pathInfo.addNewChild("pathname", mountPath); - API doc is wrong here
            pathInfo.addNewChild("name", mountPath);
            pathnames.addChildElem(pathInfo);
            elem.addChildElem(pathnames);
        }
        NaElement result = null;
        try {
            result = server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete NFS share on path: " + mountPath;
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        // build the result
        if (result.hasChildren()) {
            ArrayList<String> pathnames = new ArrayList<String>();
            for (NaElement pathInfo : (List<NaElement>) result.getChildren()) {
                pathnames.add(pathInfo.getChildContent("pathname"));
            }
            return pathnames;
        } else {
            return Lists.newArrayList(mountPath);
        }
    }

    static private void addSecurityStyles(List<NFSSecurityStyle> securityStyle,
            NaElement secStyle)
    {
        for (NFSSecurityStyle s : securityStyle) {
            NaElement secInfo = new NaElement("sec-flavor-info");
            secInfo.addNewChild("flavor", s.name());
            secStyle.addChildElem(secInfo);
        }
    }

    static private void addHosts(NaElement elem, List<String> hosts, boolean addAll)
    {
        // To add all hosts, NetApp API requires you to create a single
        // 'exports-hostname-info' element with a child element 'all-hosts'
        // with value of true.
        if (addAll) {
            NaElement exportHost = new NaElement("exports-hostname-info");
            exportHost.addNewChild("all-hosts", String.valueOf(true));
            elem.addChildElem(exportHost);
        }
        // Otherwise add each host explicitly
        else {
            for (String name : hosts) {
                NaElement exportHost = new NaElement("exports-hostname-info");
                exportHost.addNewChild("name", name);
                elem.addChildElem(exportHost);
            }
        }
    }

    List<String> modifyNFSShare(String exportPath, List<ExportRule> exportRules)
    {

        log.info("modifyNFSShare" + exportPath);

        NaElement elem = new NaElement("nfs-exportfs-modify-rule-2");
        elem.addNewChild("persistent", String.valueOf(true));
        // elem.addNewChild("verbose", String.valueOf(true));

        NaElement rules = new NaElement("rule");
        NaElement exportRules2 = new NaElement("exports-rule-info-2");

        exportRules2.addNewChild("pathname", exportPath);

        NaElement naSecurityRules = new NaElement("security-rules");

        for (ExportRule expRule : exportRules) {

            int rootMappingUid = 0;
            String anon = expRule.getAnon();

            if (anon != null) {
                if (anon.equals(ROOT_USER)) {
                    rootMappingUid = 0;
                }
                else if (anon.equals(NO_ROOT_USERS)) {
                    rootMappingUid = DISABLE_ROOT_ACCESS_CODE;
                }
            }
            else {
                // If UID is specified other than root or nobody default it
                // to this value.
                rootMappingUid = DEFAULT_ANONMOUS_ROOT_ACCESS;
            }

            NaElement naExportRule = new NaElement("security-rule-info");
            naExportRule.addNewChild("anon", String.valueOf(rootMappingUid));

            /*
             * if (!(expRule.getAnon().equals("-1"))) {
             * naExportRule.addNewChild("anon", String.valueOf(rootMappingUid));
             * }
             */

            // TODO: Account for AddAll hosts.
            // Add read-only hosts
            if ((expRule.getReadOnlyHosts() != null) && (!expRule.getReadOnlyHosts().isEmpty())) {
                NaElement readOnly = new NaElement("read-only");
                addHosts(readOnly, expRule.getReadOnlyHosts(), false);
                naExportRule.addChildElem(readOnly);
            }

            // Add read-write hosts
            if ((expRule.getReadWriteHosts() != null) && (!expRule.getReadWriteHosts().isEmpty())) {
                NaElement readWriteHosts = new NaElement("read-write");
                addHosts(readWriteHosts, expRule.getReadWriteHosts(), false);
                naExportRule.addChildElem(readWriteHosts);
            }

            // Add root hosts
            if ((expRule.getRootHosts() != null) && (!expRule.getRootHosts().isEmpty())) {
                NaElement rootHosts = new NaElement("root");
                addHosts(rootHosts, expRule.getRootHosts(), false);
                naExportRule.addChildElem(rootHosts);
            }

            // Add security style
            if (expRule.getSecFlavor() != null) {
                NaElement secStyle = new NaElement("sec-flavor");
                List<NFSSecurityStyle> secruityStyleList = new ArrayList<NFSSecurityStyle>();
                secruityStyleList.add(NFSSecurityStyle.valueOfName(expRule.getSecFlavor()));
                addSecurityStyles(secruityStyleList, secStyle);
                naExportRule.addChildElem(secStyle);
            }
            naSecurityRules.addChildElem(naExportRule);

        }

        // Add the export rule
        exportRules2.addChildElem(naSecurityRules);
        rules.addChildElem(exportRules2);
        elem.addChildElem(rules);

        NaElement result = null;
        try {
            log.info(elem);
            result = server.invokeElem(elem).getChildByName("loaded-pathnames");
        } catch (Exception e) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        // build the result
        ArrayList<String> pathnames = new ArrayList<String>();
        // for (NaElement pathInfo : (List<NaElement>)result.getChildren()) {
        // pathnames.add(pathInfo.getChildContent("name")); // API doc is wrong here
        // }
        //
        // if (!pathnames.contains(exportPath)) {
        // String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
        // msg += ". Check directory exists in volume.";
        // log.error(msg);
        // throw new NetAppException(msg);
        // }

        return pathnames;
    }

    List<String> addNewNFSShare(String exportPath, List<ExportRule> exportRules)
    {

        log.info("addNewNFSShare" + exportPath);

        NaElement elem = new NaElement("nfs-exportfs-append-rules-2");
        elem.addNewChild("persistent", String.valueOf(true));
        elem.addNewChild("verbose", String.valueOf(true));

        NaElement rules = new NaElement("rules");
        NaElement exportRules2 = new NaElement("exports-rule-info-2");

        exportRules2.addNewChild("pathname", exportPath);

        NaElement naSecurityRules = new NaElement("security-rules");

        for (ExportRule expRule : exportRules) {

            int rootMappingUid = 0;
            String anon = expRule.getAnon();

            if (anon != null) {
                if (anon.equals(ROOT_USER)) {
                    rootMappingUid = 0;
                }
                else if (anon.equals(NO_ROOT_USERS)) {
                    rootMappingUid = DISABLE_ROOT_ACCESS_CODE;
                }
            }
            else {
                // If UID is specified other than root or nobody default it
                // to this value.
                rootMappingUid = DEFAULT_ANONMOUS_ROOT_ACCESS;
            }

            NaElement naExportRule = new NaElement("security-rule-info");
            naExportRule.addNewChild("anon", String.valueOf(rootMappingUid));

            /*
             * if (!(expRule.getAnon().equals("-1"))) {
             * naExportRule.addNewChild("anon", String.valueOf(rootMappingUid));
             * }
             */

            // TODO: Account for AddAll hosts.
            // Add read-only hosts
            if ((expRule.getReadOnlyHosts() != null) && (!expRule.getReadOnlyHosts().isEmpty())) {
                NaElement readOnly = new NaElement("read-only");
                addHosts(readOnly, expRule.getReadOnlyHosts(), false);
                naExportRule.addChildElem(readOnly);
            }

            // Add read-write hosts
            if ((expRule.getReadWriteHosts() != null) && (!expRule.getReadWriteHosts().isEmpty())) {
                NaElement readWriteHosts = new NaElement("read-write");
                addHosts(readWriteHosts, expRule.getReadWriteHosts(), false);
                naExportRule.addChildElem(readWriteHosts);
            }

            // Add root hosts
            if ((expRule.getRootHosts() != null) && (!expRule.getRootHosts().isEmpty())) {
                NaElement rootHosts = new NaElement("root");
                addHosts(rootHosts, expRule.getRootHosts(), false);
                naExportRule.addChildElem(rootHosts);
            }

            // Add security style
            if (expRule.getSecFlavor() != null) {
                NaElement secStyle = new NaElement("sec-flavor");
                List<NFSSecurityStyle> secruityStyleList = new ArrayList<NFSSecurityStyle>();
                secruityStyleList.add(NFSSecurityStyle.valueOfName(expRule.getSecFlavor()));
                addSecurityStyles(secruityStyleList, secStyle);
                naExportRule.addChildElem(secStyle);
            }
            naSecurityRules.addChildElem(naExportRule);

        }

        // Add the export rule
        exportRules2.addChildElem(naSecurityRules);
        rules.addChildElem(exportRules2);
        elem.addChildElem(rules);

        NaElement result = null;
        try {
            log.info(elem);
            result = server.invokeElem(elem).getChildByName("loaded-pathnames");
        } catch (Exception e) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            log.error(msg, e);
            throw new NetAppException(msg, e);
        }

        // build the result
        ArrayList<String> pathnames = new ArrayList<String>();
        for (NaElement pathInfo : (List<NaElement>) result.getChildren()) {
            pathnames.add(pathInfo.getChildContent("name"));    // API doc is wrong here
        }

        if (!pathnames.contains(exportPath)) {
            String msg = "Failed to create NFS share on path: " + (mountPath != null ? mountPath : exportPath);
            msg += ". Check directory exists in volume.";
            log.error(msg);
            throw new NetAppException(msg);
        }

        return pathnames;
    }

    static private void addHosts(NaElement elem, Set<String> hosts, boolean addAll)
    {
        // To add all hosts, NetApp API requires you to create a single
        // 'exports-hostname-info' element with a child element 'all-hosts'
        // with value of true.
        if (addAll) {
            NaElement exportHost = new NaElement("exports-hostname-info");
            exportHost.addNewChild("all-hosts", String.valueOf(true));
            elem.addChildElem(exportHost);
        }
        // Otherwise add each host explicitly
        else {
            for (String name : hosts) {
                NaElement exportHost = new NaElement("exports-hostname-info");
                exportHost.addNewChild("name", name);
                elem.addChildElem(exportHost);
            }
        }
    }

}
