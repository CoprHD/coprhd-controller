/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
/**
 * 
 */
package com.iwave.ext.netappc;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;

import netapp.manage.NaAPIFailedException;
import netapp.manage.NaElement;
import netapp.manage.NaServer;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.collect.Lists;
import com.iwave.ext.netappc.NFSSecurityStyle;
import com.iwave.ext.netappc.model.CifsAcl;
import com.iwave.ext.netappc.model.CifsAccess;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.Qtree;
import com.iwave.ext.netapp.model.SecurityRuleInfo;
import com.iwave.ext.netapp.utils.ExportRule;

public class FlexFileShare {

private Logger log = Logger.getLogger(getClass());
	
    private static final String ROOT_USER = "root";
    private static final String NO_ROOT_USERS = "nobody";
    private static final String SEC_FLAVOR_NEVER = "never";
    private static final String SEC_FLAVOR_NONE = "none";
    private static final String RO_PERMISSION = "ro";
    private static final String RW_PERMISSION = "rw";
    private static final String ROOT_PERMISSION = "root";
    private static final String DEFAULT_EXPORT_POLICY = "default";
    private static final String UNEXPORTED_EXPORT_POLICY = "unexport_policy";
    
    private static final int DISABLE_ROOT_ACCESS_CODE = 65535;
    private static final int DEFAULT_ANONMOUS_ROOT_ACCESS = 65534;

	private String mountPath = "";
	private NaServer server = null;
	
	public FlexFileShare(NaServer server, String mountPath)
	{
		this.server = server;
		this.mountPath = mountPath;
	}
	
	boolean addCIFSShare(String shareName, String comment, int maxusers, String forcegroup)
	{
		NaElement elem = new NaElement("cifs-share-create");
		elem.addNewChild("path", mountPath);
		elem.addNewChild("share-name", shareName);
		if(comment != null && !comment.isEmpty()) {
			elem.addNewChild("comment", comment);
		}

		if (maxusers > 0)  {
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
			throw new NetAppCException(msg, e);
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
            throw new NetAppCException(msg, e);
		}
	}

	void changeCIFSShare(String shareName, Map<String, String> attrs)
	{
		NaElement elem = new NaElement("cifs-share-change");
		elem.addNewChild("share-name", shareName);

		for (String key : attrs.keySet()) {
			elem.addNewChild(key, attrs.get(key)); //NOSONAR("Fix this in future release to avoid regression")
		}

		try {
			server.invokeElem(elem);
		}
		catch (Exception e) {
			String msg = "Failed to change CIFS share: " + shareName + " attrs: " + attrs;
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}
	}	
	
	public void setCIFSAcl(CifsAcl acl)
	{
		NaElement elem = new NaElement("cifs-share-access-control-modify");
		elem.addNewChild("share", acl.getShareName());
		elem.addNewChild("permission", acl.getAccess().access());

		if (acl.getUserName() != null) {
			elem.addNewChild("user-or-group", acl.getUserName());
		}

		if (acl.getGroupName() != null) {
			elem.addNewChild("user-or-group", acl.getGroupName());
		}

		try {
			server.invokeElem(elem);
		} catch (NaAPIFailedException e) {
			String msg = "Failed to set CIFS Acl: " + acl;
			log.error(msg, e);
			throw new NetAppCException(msg, e, e.getErrno());
		} catch (Exception e) {
			String msg = "Failed to set CIFS Acl: " + acl;
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}
	}
	
	/**
     * this function add new acl
     * @param acl
     */
    public void addCIFSAcl(CifsAcl acl)
    {
        NaElement elem = new NaElement("cifs-share-access-control-create");
        elem.addNewChild("share", acl.getShareName());
        elem.addNewChild("permission", acl.getAccess().access());
       
        if (acl.getUserName() != null) {
            elem.addNewChild("user-or-group", acl.getUserName());
        }
        
        if (acl.getGroupName() != null) {
            elem.addNewChild("user-or-group", acl.getGroupName());
        }
        
        try {
            server.invokeElem(elem);
        } catch (NaAPIFailedException e) {
            String msg = "Failed to create CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppCException(msg, e, e.getErrno());
        } catch (Exception e) {
            String msg = "Failed to create CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }

    @SuppressWarnings("unchecked")
    List<Map<String, String>> listCIFSInfo(String shareName)
    {
        ArrayList<Map<String,String>> shares = new ArrayList<Map<String, String>>();
        NaElement elem = new NaElement("cifs-share-get-iter");
        if (shareName != null && !shareName.isEmpty()) {
            NaElement accessAttr = new NaElement("cifs-share");
            accessAttr.addNewChild("share-name", shareName);//shareName can contain wildcards * or ?
            NaElement query = new NaElement("query");
            query.addChildElem(accessAttr);
            elem.addChildElem(query);
        }

        String tag = null;
        try {
            do {
                NaElement result = server.invokeElem(elem);
                tag = result.getChildContent("next-tag");                
                NaElement naElement = result.getChildByName("attributes-list");
                if(naElement != null){
                    List<NaElement> shareElems = naElement.getChildren();
                    if(shareElems != null && !shareElems.isEmpty()){
                        for (NaElement shareElem : shareElems) {
                            // {description=Testing, share-name=demotest, mount-point=/vol/volscott}
                            Map<String, String> share = new HashMap<String, String>();
                            for (NaElement info : (List<NaElement>) shareElem.getChildren()) {
                                share.put(info.getName(), info.getContent());
                            }
                            shares.add(share);
                        }
                    }
                }
                if(tag != null && !tag.isEmpty()) {
                	elem = new NaElement("cifs-share-get-iter");
                	elem.addNewChild("tag", tag);
                }
            }
            while(tag != null && !tag.isEmpty());
            return shares;
        }catch (Exception e) {
            String msg = "Failed to list CIFS shares.";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }
	
	public void deleteCIFSAcl(CifsAcl acl)
    {
        NaElement elem = new NaElement("cifs-share-access-control-delete");
        elem.addNewChild("share", acl.getShareName());
        
        if (acl.getUserName() != null) {
            elem.addNewChild("user-or-group", acl.getUserName());
        }
        
        if (acl.getGroupName() != null) {
            elem.addNewChild("user-or-group", acl.getGroupName());
        }
        
        try {
            server.invokeElem(elem);
        } catch (Exception e) {
            String msg = "Failed to delete CIFS Acl: " + acl;
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }
    
	public List<CifsAcl> listCIFSAcls(String shareName) {
		List<CifsAcl> acls = new ArrayList<CifsAcl>();
		String tagNext =null;
		NaElement elem = new NaElement("cifs-share-access-control-get-iter");
		if (shareName != null && !shareName.isEmpty()) {
			NaElement accessAttr = new NaElement("cifs-share-access-control");
			accessAttr.addNewChild("share", shareName);
			NaElement query = new NaElement("query");
			query.addChildElem(accessAttr);
			elem.addChildElem(query);
		}
		try {

			do {
				NaElement result = server.invokeElem(elem);
				tagNext = result.getChildContent("next-tag");
				NaElement cifsAcl = result.getChildByName("attributes-list");  
				if (cifsAcl !=null ){
					List <NaElement> cifacls = cifsAcl.getChildren();
					for (NaElement shareElem : cifacls) {
						NaElement  permission = shareElem.getChildByName("permission");
						String name = shareElem.getChildContent("share");

						CifsAcl acl = new CifsAcl();
						try {
							acl.setAccess(CifsAccess.valueOfAccess(permission.getContent()));
						} catch (IllegalArgumentException e) {
	                    	log.warn("Invalid permission for a CIFS share: " + name, e);
	                    	log.info("Continue with next acl");
	                    	continue;
	                    }
						acl.setShareName(name);
						String userorgroup = shareElem.getChildContent("user-or-group");
						acl.setUserName(userorgroup);
						acls.add(acl);
					}
				} else {
					return acls;
				}
				if(tagNext != null && !tagNext.isEmpty()) {
					elem = new NaElement("cifs-share-access-control-get-iter");
					if (shareName != null && !shareName.isEmpty()) {
						NaElement accessAttr = new NaElement("cifs-share-access-control");
						accessAttr.addNewChild("share", shareName);
						NaElement query = new NaElement("query");
						query.addChildElem(accessAttr);
						elem.addChildElem(query);
					}
					elem.addNewChild("tag-next", tagNext);
				}
			}while( tagNext != null && !tagNext.isEmpty() );


			return acls;
		}
		catch (Exception e) {
			String msg = "Failed to list CIFS ACLs.";
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}

}

	
    @SuppressWarnings("unchecked")
    List<ExportsRuleInfo> listNFSExportRules(String pathName)
    {
        List<ExportsRuleInfo> exports = Lists.newArrayList();
        NaElement elem = new NaElement("nfs-exportfs-list-rules-2");

        //if true, returns entries from exports file; else from memory. For Cluster mode, it is always true.
        elem.addNewChild("persistent", String.valueOf(true));
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
                	//String nonsuid = securityRule.getChildContent("nonsuid"); // This is not correct.. Modified by [Gopi] as per API.
                	String nonsuid = securityRule.getChildContent("nosuid");
                	if (StringUtils.isNotBlank(nonsuid)) {
                		securityRuleInfo.setNosuid(Boolean.parseBoolean(nonsuid));
                	}

                	List<NaElement>  secFlavors = (List<NaElement>) securityRule.getChildByName("sec-flavor").getChildren();
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
        }
        catch (Exception e) {
            String msg = "Failed to list NFS exports.";
            log.error(msg, e);
            throw new NetAppCException(msg, e);
        }
    }
    
    public String getExportPolicyOfVolume(String exportPath, String volume) {
    	NaElement elem = new NaElement("volume-get-iter");
    	String policyName = null;

    	if (volume != null && !volume.isEmpty()) {
    		NaElement volumeIdAttrs = new NaElement("volume-id-attributes");
    		volumeIdAttrs.addNewChild("name", volume);
    		NaElement volumeAttrs = new NaElement("volume-attributes");
    		volumeAttrs.addChildElem(volumeIdAttrs);
    		NaElement query = new NaElement("query");
    		query.addChildElem(volumeAttrs);
    		elem.addChildElem(query);
    	}

    	NaElement resultElem = null;

    	try {
    		NaElement result = server.invokeElem(elem);
    		resultElem = result.getChildByName("attributes-list");

    		if(resultElem != null) {
    			for (NaElement volInfo : (List<NaElement>) resultElem.getChildren()) {
    				NaElement volExportAttrs = volInfo.getChildByName("volume-export-attributes");
    				if(volExportAttrs != null) {
    					policyName = volExportAttrs.getChildContent("policy");
    				}
    			}
    		}
    	} catch (Exception e) {
    		String msg = "Failed to get export policy of Volume " + volume;
    		log.error(msg, e);
    		throw new NetAppCException   (msg, e);
    	}

    	return policyName;
    }
    
    public String getExportPolicyOfQtree(String exportPath, String volume, String qtree) {
        NaElement elem = new NaElement("qtree-list-iter");
        String policyName = null;
        if ((volume != null && !volume.isEmpty()) && (qtree != null && !qtree.isEmpty())) {
        	NaElement qtreeAttrs = new NaElement("qtree-info");
        	qtreeAttrs.addNewChild("volume", volume);
        	qtreeAttrs.addNewChild("qtree", qtree);
        	NaElement query = new NaElement("query");
        	query.addChildElem(qtreeAttrs);
            elem.addChildElem(query);
        }
           
        NaElement resultElem = null;
        String tag = null;
        List<Qtree> qtrees = Lists.newArrayList();
        try {
        	do {
        		NaElement results = server.invokeElem(elem);
        		tag = results.getChildContent("next-tag");
        		resultElem = results.getChildByName("attributes-list");
        		if(resultElem != null) {
        			// Get the number of records returned by API.
        			for (NaElement qtreeElem : (List<NaElement>) resultElem.getChildren()) {
        				if(qtreeElem != null){
        					policyName = qtreeElem.getChildContent("export-policy");
        				}
        			}
        		}
        		if(tag != null && !tag.isEmpty()) {
        			elem = new NaElement("qtree-list-iter");
        			elem.addNewChild("tag", tag);
        		}
        	} while(tag != null && !tag.isEmpty());
        }catch (IllegalArgumentException e) {
    		String msg = "Failed to get export policy on path: " + (mountPath != null ? mountPath : exportPath);
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to get export policy on path: " + (mountPath != null ? mountPath : exportPath);
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}
        return policyName;
    }
    
    public void addNFSShare(String fsName, String qtreeName, String exportPath, 
    		int anonymousUid, List<String> roHosts, List<String> rwHosts, 
    		List<String> rootHosts, List<NFSSecurityStyle> securityStyle) {
    	String policyName = "";
    	String qtreePolicyName = "";
    	boolean isExported = true;
    	String volumePolicyName = getExportPolicyOfVolume(exportPath, fsName);
    	policyName = volumePolicyName;
    	if(qtreeName == null || qtreeName.isEmpty()) {
    		if(policyName.equalsIgnoreCase(DEFAULT_EXPORT_POLICY) || policyName.equalsIgnoreCase(UNEXPORTED_EXPORT_POLICY)) {
    			isExported = false;
    		}
    	} else {
    		qtreePolicyName = fsName + "_" + qtreeName;
    		policyName = getExportPolicyOfQtree(exportPath, fsName, qtreeName);
    		if(policyName.equalsIgnoreCase(volumePolicyName) || policyName.equalsIgnoreCase(DEFAULT_EXPORT_POLICY) || 
    				policyName.equalsIgnoreCase(UNEXPORTED_EXPORT_POLICY)) {
    			isExported = false;
    		}
    	}
    	if(!isExported) {
    		createExportPolicy(qtreeName, fsName, exportPath);
    		try {
    			NaElement result = null;
    			Set<String> hosts = new HashSet<String>();
    			hosts.addAll(roHosts);
    			hosts.addAll(rwHosts);
    			hosts.addAll(roHosts);
    			for(String host : hosts) {
    				NaElement ruleElem = new NaElement("export-rule-create");
    				if(qtreeName != null && !qtreeName.isEmpty())
    					ruleElem.addNewChild("policy-name", qtreePolicyName);
    				else
    					ruleElem.addNewChild("policy-name", fsName);
    				ruleElem.addNewChild("client-match", host);
    				if (anonymousUid > -1) {
    					ruleElem.addNewChild("anonymous-user-id", String.valueOf(anonymousUid));
    				}
    				setSecurityStyle(host, securityStyle, roHosts, rwHosts, rootHosts, ruleElem);
    				result = server.invokeElem(ruleElem);
    			}
    		} catch (IllegalArgumentException e) {
    			//Rollback - Delete export policy if creation of export rules fail
    			deleteExportPolicy(policyName, fsName);
    			String msg = "Failed to create export rule on path: " + (mountPath != null ? mountPath : exportPath);
    			log.error(msg, e);
    			throw new NetAppCException(msg, e);
    		} catch(Exception e) {
    			//Rollback - Delete export policy if creation of export rules fail
    			deleteExportPolicy(policyName, fsName);
    			String msg = "Failed to create export rule on path: " + (mountPath != null ? mountPath : exportPath);
    			log.error(msg, e);
    			throw new NetAppCException(msg, e);
    		}

    		if(qtreeName != null && !qtreeName.isEmpty()) {
    			assignExportPolicyToQtree(qtreeName, fsName, exportPath);
    		} else {
    			assignExportPolicyToVolume(fsName, policyName, exportPath);
    		}
    	} else {
			String msg = "Export already exists. Failed to export path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg);
			throw new NetAppCException(msg);
    	}
    }    

    public void changeNFSShare(String fsName, String qtreeName, ExportRule oldRule, 
    		ExportRule newRule, String exportPath) {
    	String policyName = "";
    	if(qtreeName == null || qtreeName.isEmpty()) {
    		policyName = getExportPolicyOfVolume(exportPath, fsName);
    	} else {
    		policyName = getExportPolicyOfQtree(exportPath, fsName, qtreeName);
    	}
    	NaElement result = null;
    	try {
    		List<String> rootHosts = new ArrayList<String>();
    		List<String> rwHosts = new ArrayList<String>();
    		List<String> roHosts = new ArrayList<String>();
    		int rootMappingUid = getAnonId(newRule.getAnon());

    		List<NFSSecurityStyle> securityStyleList = new ArrayList<NFSSecurityStyle>();
    		securityStyleList.add(NFSSecurityStyle.valueOfName(newRule.getSecFlavor()));

    		//Collects all the hosts from export rule after modification
    		Set<String> hosts = new HashSet<String>();
    		if(newRule.getReadOnlyHosts() != null) {
    			hosts.addAll(newRule.getReadOnlyHosts());
    			roHosts.addAll(newRule.getReadOnlyHosts());
    		}
    		if(newRule.getReadWriteHosts() != null) {
    			hosts.addAll(newRule.getReadWriteHosts());
    			rwHosts.addAll(newRule.getReadWriteHosts());
    		}
    		if(newRule.getRootHosts() != null) {
    			hosts.addAll(newRule.getRootHosts());
    			rootHosts.addAll(newRule.getRootHosts());
    		}

    		if(oldRule != null) {
    			//Collects all the hosts from export rule before modification
    			Set<String> oldHosts = new HashSet<String>();
    			if(oldRule.getReadOnlyHosts() != null) {
    				oldHosts.addAll(oldRule.getReadOnlyHosts());
    			}
    			if(oldRule.getReadWriteHosts() != null) {
    				oldHosts.addAll(oldRule.getReadWriteHosts());
    			}
    			if(oldRule.getRootHosts() != null) {
    				oldHosts.addAll(oldRule.getRootHosts());
    			}

    			//Handles removing endpoint from export rule during modify.    		
    			Set<String> hostsToRemove = com.google.common.collect.Sets.difference(oldHosts, hosts);
    			for(String host : hostsToRemove) {
    				List<String> permission = new ArrayList<String>();
    				if(oldRule.getReadOnlyHosts() != null && oldRule.getReadOnlyHosts().contains(host))
    					permission.add(RO_PERMISSION);
    				if(oldRule.getReadWriteHosts() != null && oldRule.getReadWriteHosts().contains(host))
    					permission.add(RW_PERMISSION);
    				if(oldRule.getRootHosts() != null && oldRule.getRootHosts().contains(host))
    					permission.add(ROOT_PERMISSION);
    				String ruleIndex = getMatchingRule(host, policyName, permission, oldRule.getSecFlavor(), exportPath);
    				NaElement ruleElem = null;
    				if(!ruleIndex.isEmpty()) {
        				deleteExportRule(ruleIndex, policyName, fsName);
    				}   
    			}
    		}

    		for(String host : hosts) {
    			String ruleIndex = "";
    			if(oldRule != null) {
    				List<String> permission = new ArrayList<String>();
    				if(oldRule.getReadOnlyHosts() != null && oldRule.getReadOnlyHosts().contains(host))
    					permission.add(RO_PERMISSION);
    				if(oldRule.getReadWriteHosts() != null && oldRule.getReadWriteHosts().contains(host))
    					permission.add(RW_PERMISSION);
    				if(oldRule.getRootHosts() != null && oldRule.getRootHosts().contains(host))
    					permission.add(ROOT_PERMISSION);
    				ruleIndex = getMatchingRule(host, policyName, permission, oldRule.getSecFlavor(), exportPath);
    			} else {
    				ruleIndex = getMatchingRule(host, policyName, null, null, exportPath);
    			}
    			NaElement ruleElem = null;
    			if(!ruleIndex.isEmpty()) {
    				ruleElem = new NaElement("export-rule-modify");
    				ruleElem.addNewChild("rule-index", ruleIndex);
    			} else {
    				ruleElem = new NaElement("export-rule-create");
    			}
    			ruleElem.addNewChild("anonymous-user-id", String.valueOf(rootMappingUid));
    			ruleElem.addNewChild("policy-name", policyName);
    			ruleElem.addNewChild("client-match", host); 

    			setSecurityStyle(host, securityStyleList, roHosts, rwHosts, rootHosts, ruleElem);
    			result = server.invokeElem(ruleElem);
    		}
    	} catch (IllegalArgumentException e) {
    		String msg = "Failed to create export rule for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to create export rule for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} 
    }
    
    public void deleteNFSShare(String fsName, String qtreeName, ExportRule oldRule, 
    		String exportPath) {
    	String policyName = "";
    	if(qtreeName == null || qtreeName.isEmpty()) {
    		policyName = getExportPolicyOfVolume(exportPath, fsName);
    	} else {
    		policyName = getExportPolicyOfQtree(exportPath, fsName, qtreeName);
    	}
    	NaElement result = null;
    	try {
    		List<String> rootHosts = new ArrayList<String>();
    		List<String> rwHosts = new ArrayList<String>();
    		List<String> roHosts = new ArrayList<String>();

    		List<NFSSecurityStyle> secruityStyleList = new ArrayList<NFSSecurityStyle>();
    		secruityStyleList.add(NFSSecurityStyle.valueOfName(oldRule.getSecFlavor()));

			//Collects all the hosts from export rule before modification
    		Set<String> hosts = new HashSet<String>();
    		if(oldRule.getReadOnlyHosts() != null) {
    			hosts.addAll(oldRule.getReadOnlyHosts());
    			roHosts.addAll(oldRule.getReadOnlyHosts());
    		}
    		if(oldRule.getReadWriteHosts() != null) {
    			hosts.addAll(oldRule.getReadWriteHosts());
    			rwHosts.addAll(oldRule.getReadWriteHosts());
    		}
    		if(oldRule.getRootHosts() != null) {
    			hosts.addAll(oldRule.getRootHosts());
    			rootHosts.addAll(oldRule.getRootHosts());
    		}
    		for(String host : hosts) {
    			List<String> permission = new ArrayList<String>();
    			if(oldRule.getReadOnlyHosts() != null && oldRule.getReadOnlyHosts().contains(host))
    				permission.add(RO_PERMISSION);
    			if(oldRule.getReadWriteHosts() != null && oldRule.getReadWriteHosts().contains(host))
    				permission.add(RW_PERMISSION);
    			if(oldRule.getRootHosts() != null && oldRule.getRootHosts().contains(host))
    				permission.add(ROOT_PERMISSION);
    			String ruleIndex = getMatchingRule(host, policyName, permission, oldRule.getSecFlavor(), exportPath);
    			NaElement ruleElem = null;
    			if(!ruleIndex.isEmpty()) {
    				deleteExportRule(ruleIndex, policyName, fsName);
    			}    			    			    		
    		}
    	} catch (IllegalArgumentException e) {
    		String msg = "Failed to create export rule for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to create export rule for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}
    }    
        
    public int getAnonId(String anon) {
		int rootMappingUid = 0;
		if (anon !=null){
			if (anon.equals(ROOT_USER)) {
				rootMappingUid = 0;
			}
			else if(anon.equals(NO_ROOT_USERS)) {
				rootMappingUid = DISABLE_ROOT_ACCESS_CODE;
			}
		} else {
			// If UID is specified other than root or nobody default it
			// to this value.
			rootMappingUid = DEFAULT_ANONMOUS_ROOT_ACCESS;
		}
		return rootMappingUid;
    }
    
    /**
     * Gets the index of export rule to be modified by matching the properties 
     * of export rule before modification
     */
    public String getMatchingRule(String host, String fsName, 
    		List<String> permission, String secFlavor, String exportPath) {
    	String ruleIndex = "";
    	try {
    		NaElement expRuleElem = new NaElement("export-rule-get-iter");        			
    		NaElement expRuleInfo = new NaElement("export-rule-info");
    		NaElement result = null;
    		expRuleInfo.addNewChild("policy-name", fsName);
    		expRuleInfo.addNewChild("client-match", host);
    		expRuleInfo.addNewChild("vserver-name", server.getVserver()); 

    		if(permission != null && !permission.isEmpty() && secFlavor != null) {
    			NaElement roRule = new NaElement("ro-rule");
    			NaElement rwRule = new NaElement("rw-rule");
    			NaElement rootRule = new NaElement("super-user-security");
    			if(permission.contains(ROOT_PERMISSION)) {
    				rootRule.addNewChild("security-flavor", secFlavor);
    				rwRule.addNewChild("security-flavor", secFlavor);
    				roRule.addNewChild("security-flavor", secFlavor);
    			} else if(permission.contains(RW_PERMISSION)) {
    				rootRule.addNewChild("security-flavor", SEC_FLAVOR_NONE);
    				rwRule.addNewChild("security-flavor", secFlavor);
    				roRule.addNewChild("security-flavor", secFlavor);
    			} else if(permission.contains(RO_PERMISSION)) {
    				rootRule.addNewChild("security-flavor", SEC_FLAVOR_NONE);
    				rwRule.addNewChild("security-flavor", SEC_FLAVOR_NEVER);
    				roRule.addNewChild("security-flavor", secFlavor);
    			}
    			expRuleInfo.addChildElem(rwRule);
    			expRuleInfo.addChildElem(roRule);
    			expRuleInfo.addChildElem(rootRule);
    		}
    		if((permission == null) || (permission != null && permission.isEmpty())) { //NOSONAR("Null check required")
    			NaElement roRule = new NaElement("ro-rule");
    			NaElement rwRule = new NaElement("rw-rule");
    			NaElement rootRule = new NaElement("super-user-security");
    			rootRule.addNewChild("security-flavor", SEC_FLAVOR_NONE);
				rootRule.addNewChild("security-flavor", SEC_FLAVOR_NEVER);
				rwRule.addNewChild("security-flavor", SEC_FLAVOR_NEVER);
    			expRuleInfo.addChildElem(rwRule);
    			expRuleInfo.addChildElem(roRule);
    			expRuleInfo.addChildElem(rootRule);
			}

    		NaElement query = new NaElement("query");
    		query.addChildElem(expRuleInfo);
    		expRuleElem.addChildElem(query);
    		NaElement resultElem = null;
    		result = server.invokeElem(expRuleElem);
    		resultElem = result.getChildByName("attributes-list");
    		if(resultElem != null) {
    			for (NaElement exportRule : (List<NaElement>) resultElem.getChildren()) {
    				ruleIndex = exportRule.getChildContent("rule-index");
    				break;
    			}
    		}
    	} catch (IllegalArgumentException e) {
    		String msg = "Failed to match export rule on path: " + (mountPath != null ? mountPath : exportPath);
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to match export rule on path: " + (mountPath != null ? mountPath : exportPath);
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}
    	return ruleIndex;
    }
    
    /**
     * Deletes export rule based on rule index
     */
    public void deleteExportRule(String ruleIndex, String policyName, String fsName) {
    	NaElement ruleElem = null;
    	NaElement result = null;
    	try {
		ruleElem = new NaElement("export-rule-destroy");
		ruleElem.addNewChild("rule-index", ruleIndex);
		ruleElem.addNewChild("policy-name", policyName);
		result = server.invokeElem(ruleElem);
    	} catch (IllegalArgumentException e) {
    		String msg = "Failed to delete export rule index " + ruleIndex + " for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to delete export rule index " + ruleIndex + " for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}
    }
    
    /**
     * Deletes export policy based on policy name
     */
    public void deleteExportPolicy(String policyName, String fsName) {
    	NaElement ruleElem = null;
    	NaElement result = null;
    	try {
		ruleElem = new NaElement("export-policy-destroy");
		ruleElem.addNewChild("policy-name", policyName);
		result = server.invokeElem(ruleElem);
    	} catch (IllegalArgumentException e) {
    		String msg = "Failed to delete export policy for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	} catch(Exception e) {
    		String msg = "Failed to create export policy for file system: " + fsName;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}
    }
    
    /**
     * Creates export policy
     */
    public void createExportPolicy(String qtreeName, String fsName, String exportPath) {
		NaElement result = null;
		try {
			NaElement policyElem = new NaElement("export-policy-create");
			if(qtreeName != null && !qtreeName.isEmpty()) {
				String qtreePolicyName = fsName + "_" + qtreeName;
				policyElem.addNewChild("policy-name", qtreePolicyName);
			} else {
				policyElem.addNewChild("policy-name", fsName);
			}
			result = server.invokeElem(policyElem);
		} catch (IllegalArgumentException e) {
			String msg = "Failed to create export policy on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		} catch(Exception e) {
			String msg = "Failed to create export policy on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}	
    }
    
    /**
     * Assigns export policy name to qtree after export policy and rules are created 
     */
    public void assignExportPolicyToQtree(String qtreeName, String fsName, String exportPath) {
		try {
			NaElement result = null;
			String qtreePolicyName = fsName + "_" + qtreeName;
			NaElement qtreeElem = new NaElement("qtree-modify");    			
			qtreeElem.addNewChild("export-policy", qtreePolicyName);
			qtreeElem.addNewChild("volume", fsName);
			qtreeElem.addNewChild("qtree", qtreeName);
			result = server.invokeElem(qtreeElem);
		} catch (IllegalArgumentException e) {
			String msg = "Failed to modify volume on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		} catch(Exception e) {
			String msg = "Failed to modify volume on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}
    }
    
    /**
     * Assigns export policy name to volume after export policy and rules are created 
     */
    public void assignExportPolicyToVolume(String fsName, String policyName, String exportPath) {
		try {
			NaElement result = null;
			NaElement volumeElem = new NaElement("volume-modify-iter");
			NaElement volumeIdAttrs = new NaElement("volume-id-attributes");
			volumeIdAttrs.addNewChild("name",fsName);
			NaElement query = new NaElement("query");	
			NaElement volumeAttr = new NaElement("volume-attributes");
			volumeAttr.addChildElem(volumeIdAttrs);
			query.addChildElem(volumeAttr);
			volumeElem.addChildElem(query);

			NaElement attrs = new NaElement("attributes");
			NaElement volumeAttrs = new NaElement("volume-attributes");
			NaElement volumeExportAttrs = new NaElement("volume-export-attributes");			
			volumeExportAttrs.addNewChild("policy",fsName);
			volumeAttrs.addChildElem(volumeExportAttrs);
			attrs.addChildElem(volumeAttrs);
			volumeElem.addChildElem(attrs);
			volumeElem.addNewChild("return-failure-list", String.valueOf(true));

			result = server.invokeElem(volumeElem);
			log.info("Failure list size " + result.getChildByName("failure-list").getChildren().size());
			if(!result.getChildByName("failure-list").getChildren().isEmpty()) {
				log.error("Failed to modify Export Policy of File System : " + fsName);
			}
		} catch (IllegalArgumentException e) {
			//Rollback - Delete export policy if assigning export policy to volume fails
			deleteExportPolicy(policyName, fsName);
			String msg = "Failed to modify volume on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		} catch(Exception e) {
			//Rollback - Delete export policy if assigning export policy to volume fails
			deleteExportPolicy(policyName, fsName);
			String msg = "Failed to modify volume on path: " + (mountPath != null ? mountPath : exportPath);
			log.error(msg, e);
			throw new NetAppCException(msg, e);
		}
    }
    
    private void setSecurityStyle(String host, List<NFSSecurityStyle> securityStyleList, 
    		List<String> roHosts, List<String> rwHosts, List<String> rootHosts, NaElement ruleElem) {
		NaElement roRule = new NaElement("ro-rule");
		NaElement rwRule = new NaElement("rw-rule");
		NaElement rootRule = new NaElement("super-user-security");
		boolean hasRootPermission = false;
		boolean hasRWPermission = false;
		boolean hasROPermission = false;

		if(roHosts.contains(host)) {
			hasROPermission = true;
		}
		if(rwHosts.contains(host)) {
			hasRWPermission = true;
			hasROPermission = true; 
		}
		if(rootHosts.contains(host)) {
			hasRootPermission = true;
			hasRWPermission = true;
			hasROPermission = true;  	
		}

		if(hasRootPermission) {
			// Add security style
			for(NFSSecurityStyle s : securityStyleList) {
				rootRule.addNewChild("security-flavor", s.name());
				rwRule.addNewChild("security-flavor", s.name());
				roRule.addNewChild("security-flavor", s.name());
			}
		} else { 
			if(hasRWPermission) {
				rootRule.addNewChild("security-flavor", SEC_FLAVOR_NONE);
				// Add security style
				for(NFSSecurityStyle s : securityStyleList) {
					rwRule.addNewChild("security-flavor", s.name());
					roRule.addNewChild("security-flavor", s.name());
				}
			} else {
				rootRule.addNewChild("security-flavor", SEC_FLAVOR_NONE);
				rwRule.addNewChild("security-flavor", SEC_FLAVOR_NEVER);
				if(hasROPermission) {
					// Add security style
					for(NFSSecurityStyle s : securityStyleList) {
						roRule.addNewChild("security-flavor", s.name());
					}
				} else {
					roRule.addNewChild("security-flavor", SEC_FLAVOR_NEVER);
				}
			}
		}
		ruleElem.addChildElem(rootRule);
		ruleElem.addChildElem(rwRule);
		ruleElem.addChildElem(roRule);
    }
    
    public List<String> deleteNFSShare(boolean deleteAll)
    {
    	NaElement elem = new NaElement("nfs-exportfs-delete-rules");
    	elem.addNewChild("persistent", String.valueOf(true));
    	elem.addNewChild("verbose", String.valueOf(true));
    	// Delete all NFS Shares
    	if(deleteAll) {
    		elem.addNewChild("all-pathnames", String.valueOf(true));
    	}
    	else {
    		NaElement pathnames = new NaElement("pathnames");
    		NaElement pathInfo = new NaElement("pathname-info");
    		pathInfo.addNewChild("name", mountPath);
    		pathnames.addChildElem(pathInfo);
    		elem.addChildElem(pathnames);
    	}
    	NaElement result = null;
    	try {
    		result = server.invokeElem(elem);
    	} catch(Exception e) {
    		String msg = "Failed to delete NFS share on path: " + mountPath;
    		log.error(msg, e);
    		throw new NetAppCException(msg, e);
    	}

    	// build the result
    	if (result.hasChildren()) {
    		ArrayList<String> pathnames = new ArrayList<String>();
    		for(NaElement pathInfo : (List<NaElement>)result.getChildren()) {
    			pathnames.add(pathInfo.getChildContent("pathname"));
    		}
    		return pathnames;
    	} else {
    		return Lists.newArrayList(mountPath);
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

}

