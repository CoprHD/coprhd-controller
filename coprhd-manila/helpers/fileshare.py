#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

from manila.share.drivers.coprhd.helpers import commoncoprhdapi
import tag
import json
import socket
import commands
from commoncoprhdapi import SOSError
from threading import Timer
import schedulepolicy
import virtualpool


class Fileshare(object):

    '''
    The class definition for operations on 'Fileshare'.
    '''
    # Commonly used URIs for the 'Fileshare' module
    URI_SEARCH_FILESHARES = '/file/filesystems/search?project={0}'
    URI_SEARCH_FILESHARES_BY_PROJECT_AND_NAME = \
        '/file/filesystems/search?project={0}&name={1}'
    URI_FILESHARES = '/file/filesystems'
    URI_FILESHARE = URI_FILESHARES + '/{0}'
    URI_FILESHARE_CREATE = URI_FILESHARES + '?project={0}'
    URI_FILESHARE_SNAPSHOTS = URI_FILESHARE + '/snapshots'
    URI_FILESHARE_RESTORE = URI_FILESHARE + '/restore'
    URI_FILESHARE_EXPORTS = URI_FILESHARE + '/exports'
    URI_FILESHARE_SHOW_EXPORTS = URI_FILESHARE + '/export'
    URI_FILESHARE_SMB_EXPORTS = URI_FILESHARE + '/shares'
    URI_FILESHARE_UNEXPORTS = URI_FILESHARE + '/export'
    URI_FILESHARE_SMB_UNEXPORTS = URI_FILESHARE_SMB_EXPORTS + '/{1}'
    URI_FILESHARE_CONSISTENCYGROUP = URI_FILESHARE + '/consistency-group'
    URI_PROJECT_RESOURCES = '/projects/{0}/resources'
    URI_EXPAND = URI_FILESHARE + '/expand'
    URI_DEACTIVATE = URI_FILESHARE + '/deactivate'
    URI_TAG_FILESHARE = URI_FILESHARE + '/tags'

    URI_UNMANAGED_FILESYSTEM_INGEST = '/vdc/unmanaged/filesystems/ingest'
    URI_UNMANAGED_FILESYSTEM_SHOW = '/vdc/unmanaged/filesystems/{0}'
    URI_CIFS_ACL = URI_FILESHARE + '/shares/{1}/acl'

    URI_TASK_LIST = URI_FILESHARE + '/tasks'
    URI_TASK = URI_TASK_LIST + '/{1}'
    URI_NFS_ACL = '/file/filesystems/{0}/acl'
    
    URI_POLICY_ASSIGN = '/file/filesystems/{0}/assign-file-policy/{1}'
    URI_POLICY_UNASSIGN = '/file/filesystems/{0}/unassign-file-policy/{1}'
    URI_POLICY_LIST = '/file/filesystems/{0}/file-policies'

    URI_CONTINUOS_COPIES_START = '/file/filesystems/{0}/protection/continuous-copies/start'
    URI_CONTINUOS_COPIES_PAUSE = '/file/filesystems/{0}/protection/continuous-copies/pause'
    URI_CONTINUOS_COPIES_RESUME = '/file/filesystems/{0}/protection/continuous-copies/resume'
    URI_CONTINUOS_COPIES_STOP = '/file/filesystems/{0}/protection/continuous-copies/stop'
    URI_CONTINUOS_COPIES_FAILOVER = '/file/filesystems/{0}/protection/continuous-copies/failover'
    URI_CONTINUOS_COPIES_FAILBACK = '/file/filesystems/{0}/protection/continuous-copies/failback'
    URI_CONTINUOS_COPIES_CREATE = '/file/filesystems/{0}/protection/continuous-copies/create'
    URI_CONTINUOS_COPIES_DEACTIVATE = '/file/filesystems/{0}/protection/continuous-copies/deactivate'
    URI_CONTINUOS_COPIES_REFRESH = '/file/filesystems/{0}/protection/continuous-copies/refresh'
    URI_VPOOL_CHANGE = '/file/filesystems/{0}/vpool-change'
    
    URI_SCHEDULE_SNAPSHOTS_LIST = '/file/filesystems/{0}/file-policies/{1}/snapshots'

    isTimeout = False
    timeout = 300

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    # Lists fileshares in a project
    def list_fileshares(self, project):
        '''
        Makes REST API call to list fileshares under a project
        Parameters:
            project: name of project
        Returns:
            List of fileshares uuids in JSON response payload
        '''

        from project import Project

        proj = Project(self.__ipAddr, self.__port)
        project_uri = proj.project_query(project)

        fileshare_uris = self.search_fileshares(project_uri)
        fileshares = []
        for uri in fileshare_uris:
            fileshare = self.show_by_uri(uri)
            if(fileshare):
                fileshares.append(fileshare)
        return fileshares

    '''
    Given the project name and volume name, the search will be performed to
    find  if the fileshare with the given name exists or not.
    If found, the uri of the fileshare will be returned
    '''

    def search_by_project_and_name(self, projectName, fileshareName):

        return (
            commoncoprhdapi.search_by_project_and_name(
                projectName, fileshareName,
                Fileshare.URI_SEARCH_FILESHARES_BY_PROJECT_AND_NAME,
                self.__ipAddr, self.__port)
        )

    def search_fileshares(self, project_uri, fileshare=None):

        uri = Fileshare.URI_SEARCH_FILESHARES.format(project_uri)
        if (fileshare is not None):
            uri = Fileshare.URI_SEARCH_FILESHARES_BY_PROJECT_AND_NAME.format(
                project_uri, fileshare)
        
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET", uri, None)
        o = commoncoprhdapi.json_decode(s)
        if not o:
            return []

        fileshare_uris = []
        resources = commoncoprhdapi.get_node_value(o, "resource")
        for resource in resources:
            fileshare_uris.append(resource["id"])
        return fileshare_uris

    # Get the list of fileshares given a project uri
    def list_by_uri(self, project_uri):
        '''
        Makes REST API call and retrieves fileshares based on project UUID
        Parameters:
            project_uri: UUID of project
        Returns:
            List of fileshare UUIDs in JSON response payload
        '''

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Fileshare.URI_PROJECT_RESOURCES.format(project_uri), None)
        o = commoncoprhdapi.json_decode(s)
        if not o:
            return []

        fileshare_uris = []
        resources = commoncoprhdapi.get_node_value(o, "project_resource")
        for resource in resources:
            if(resource["resource_type"] == "fileshare"):
                fileshare_uris.append(resource["id"])
        return fileshare_uris

    # Shows fileshare information given its name
    def show(self, name, show_inactive=False, xml=False):
        '''
        Retrieves fileshare details based on fileshare name
        Parameters:
            name: name of the fileshare. If the fileshare is under a project,
            then full XPath needs to be specified.
            Example: If FS1 is a fileshare under project PROJ1, then the name
            of fileshare is PROJ1/FS1
        Returns:
            Fileshare details in JSON response payload
        '''

        from project import Project

        if (commoncoprhdapi.is_uri(name)):
            return name

        (pname, label) = commoncoprhdapi.get_parent_child_from_xpath(name)
        if(not pname):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Filesystem " + name + ": not found")

        proj = Project(self.__ipAddr, self.__port)
        puri = proj.project_query(pname)

        puri = puri.strip()
        uris = self.search_fileshares(puri, label)
        
        if (len(uris) > 0):
            fileshare = self.show_by_uri(uris[0], show_inactive)
            if(not xml):
                return fileshare
            else:
                return self.show_by_uri(fileshare['id'], show_inactive, xml)
            
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Filesystem " + label + ": not found")

    # Shows fileshare information given its uri
    def show_by_uri(self, uri, show_inactive=False, xml=False):
        '''
        Makes REST API call and retrieves fileshare details based on UUID
        Parameters:
            uri: UUID of fileshare
        Returns:
            Fileshare details in JSON response payload
        '''
        if(xml):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "GET", Fileshare.URI_FILESHARE.format(uri),
                None, None, xml)
            return s

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET", Fileshare.URI_FILESHARE.format(uri), None)
        if(not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(show_inactive):
            return o
        if('inactive' in o):
            if(o['inactive']):
                return None
        return o

    def unmanaged_filesystem_ingest(self, tenant, project,
                                    varray, vpool, filesystems):
        '''
        This function is to ingest given unmanaged filesystems
        into ViPR.
        '''
        from project import Project
        proj_obj = Project(self.__ipAddr, self.__port)
        project_uri = proj_obj.project_query(tenant + "/" + project)

        from virtualpool import VirtualPool
        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "file")

        from virtualarray import VirtualArray
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)

        request = {
            'vpool': vpool_uri,
            'varray': varray_uri,
            'project': project_uri,
            'unmanaged_filesystem_list': filesystems
        }

        body = json.dumps(request)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_UNMANAGED_FILESYSTEM_INGEST,
            body)
        o = commoncoprhdapi.json_decode(s)
        return o

    def unmanaged_filesystem_show(self, filesystem):
        '''
        This function is to show the details of unmanaged filesystem
        from  ViPR.
        '''
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_UNMANAGED_FILESYSTEM_SHOW.format(filesystem),
            None)
        o = commoncoprhdapi.json_decode(s)
        return o

    # Creates a fileshare given label, project, vpool, size and id for vnx file
    def create(self, project, label, size, varray, vpool, id, protocol, sync, advlim, softlim, grace,synctimeout):
        '''
        Makes REST API call to create fileshare under a project
        Parameters:
            project: name of the project under which the fileshare will
                     be created
            label: name of fileshare
            size: size of fileshare
            varray: name of varray
            vpool: name of vpool
            id : id of fileshare applicatble for VNX File
            protocol: NFS, NFSv4, CIFS
        Returns:
            Created task details in JSON response payload
        '''

        from virtualpool import VirtualPool
        from project import Project
        from virtualarray import VirtualArray

        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "file")

        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)

        parms = {
            'name': label,
            'size': size,
            'varray': varray_uri,
            'vpool': vpool_uri,
            'fs_id' : id,
            'soft_limit' : softlim,
            'soft_grace' : grace,
            'notification_limit' : advlim
        }

        if(protocol):
            parms["protocols"] = protocol

        body = json.dumps(parms)

        proj = Project(self.__ipAddr, self.__port)
        project_uri = proj.project_query(project)

        try:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Fileshare.URI_FILESHARE_CREATE.format(project_uri),
                body)
            o = commoncoprhdapi.json_decode(s)
            if(sync):
                #fileshare = self.show(name, True)
                return self.check_for_sync(o, sync,synctimeout)
            
            else:
                return o
        except SOSError as e:
            errorMessage = str(e).replace(vpool_uri, vpool)
            errorMessage = errorMessage.replace(varray_uri, varray)
            commoncoprhdapi.format_err_msg_and_raise("create", "filesystem",
                                            errorMessage, e.err_code)

    # Tag the fileshare information
    def tag(self, name, add, remove):
        '''
        Makes REST API call to update a fileshare information
        Parameters:
            name: name of the fileshare to be updated
            add: tags to be added
            remove: tags to be removed
        Returns
            Created task details in JSON response payload
        '''
        fileshare_uri = self.fileshare_query(name)

        return (
            tag.tag_resource(self.__ipAddr, self.__port,
                             Fileshare.URI_TAG_FILESHARE,
                             fileshare_uri, add, remove)
        )

    #Update a fileshare information
    def update(self, name, advlim, softlim, grace):
        '''
        Makes REST API call to update a fileshare information
        Parameters:
            name: name of the fileshare to be updated
        Returns
            Created task details in JSON response payload
        '''
        
        fileshare_uri = self.fileshare_query(name)

        parms = dict()

        if advlim is not None :
            parms['notification_limit'] = advlim
        if softlim is not None :
            parms['soft_limit'] = softlim
        if grace is not None :
            parms['soft_grace'] = grace
            
        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Fileshare.URI_FILESHARE.format(fileshare_uri), body)
        o = commoncoprhdapi.json_decode(s)
        return o

    # Exports a fileshare to a host given a fileshare name and the host name
    def export(
            self, name, security_type, permission, root_user,
            endpoints, protocol, share_name, share_description,
            permission_type, sub_dir, sync,synctimeout):
        '''
        Makes REST API call to export fileshare to a host
        Parameters:
            name: name of fileshare
            type: type of security
            permission: Permissions
            root_user: root user
            endpoints: host names, IP addresses, or netgroups
            protocol:  NFS, NFSv4, CIFS
            share_name: Name of the SMB share
            share_description: Description of SMB share
        Returns:
            Created Operation ID details in JSON response payload
        '''
        fileshare_uri = name
        try:
            fileshare_uri = self.fileshare_query(name)
            if(protocol == 'CIFS'):
                request = {
                    'name': share_name,
                    'description': share_description
                }
                if(sub_dir):
                    request["subDirectory"] = sub_dir
                

                if(permission_type):
                    request["permission_type"] = permission_type
                if(permission and permission in ["read", "change", "full"]):
                    request["permission"] = permission

                body = json.dumps(request)

                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    Fileshare.URI_FILESHARE_SMB_EXPORTS.format(fileshare_uri),
                    body)

            else:
                request = {
                    'type': security_type,
                    'permissions': permission,
                    'root_user': root_user,
                    'endpoints': endpoints,
                    'protocol': protocol,
                }
                if(sub_dir):
                    request["sub_directory"] = sub_dir
                

                body = json.dumps(request)
                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    Fileshare.URI_FILESHARE_EXPORTS.format(fileshare_uri),
                    body)
            if(not s):
                return None
            o = commoncoprhdapi.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync,synctimeout)
            else:
                return o
        except SOSError as e:
            errorMessage = str(e)
            if(commoncoprhdapi.is_uri(fileshare_uri)):
                errorMessage = str(e).replace(fileshare_uri, name)
            commoncoprhdapi.format_err_msg_and_raise("export", "filesystem",
                                            errorMessage, e.err_code)

    # Unexports a fileshare from a host given a fileshare name, type of
    # security and permission
    def unexport(self, name, protocol, share_name, sub_dir, all_dir, sync,synctimeout):
        '''
        Makes REST API call to unexport fileshare from a host
        Parameters:
            name: name of fileshare
            security_type: type of security
            permission: Permissions
            root_user: root_user mapping
            protocol: NFS, NFSv4, CIFS
        Returns:
            Created Operation ID details in JSON response payload
        '''

        fileshare_uri = self.fileshare_query(name)
        if(protocol == 'CIFS'):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "DELETE",
                Fileshare.URI_FILESHARE_SMB_UNEXPORTS.format(fileshare_uri,
                                                             share_name),
                None)
        else:
            request_uri = Fileshare.URI_FILESHARE_UNEXPORTS.format(fileshare_uri)
            if(sub_dir):
                request_uri = request_uri + "?subDir=" + sub_dir
            elif(all_dir):
                request_uri = request_uri + "?allDirs=true"
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "DELETE",
                request_uri, None)
        if(not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync,synctimeout)
        else:
            return o


    def export_rule(self, name, operation, securityflavor, user=None, roothosts=None, readonlyhosts=None, readwritehosts=None, subDir=None):
        
        fileshare_uri = self.fileshare_query(name)
        exportRulesparam = dict()
        exportRulesparam['secFlavor'] = securityflavor
        if(roothosts):
            exportRulesparam['rootHosts'] = roothosts
        if(readonlyhosts):
            exportRulesparam['readOnlyHosts'] = readonlyhosts
        if(readwritehosts):
            exportRulesparam['readWriteHosts'] = readwritehosts
        if(user):
            exportRulesparam['anon'] = user

        exportRulerequest = {'exportRules':[exportRulesparam]}

        if("add"== operation):
            request = {'add': exportRulerequest}
        elif("delete" == operation):
            request = {'delete' : exportRulerequest}
        else:
            request = {'modify' : exportRulerequest}
	
        body = json.dumps(request)
        params = ''
        if(subDir):
            params += '&' if ('?' in params) else '?'
            params += "subDir=" + subDir

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT", Fileshare.URI_FILESHARE_SHOW_EXPORTS.format(fileshare_uri) + params, body)
        o = commoncoprhdapi.json_decode(s)
        return o



    # Deletes a fileshare given a fileshare name
    def delete(self, name, forceDelete=False, delete_type='FULL', sync=False,synctimeout=0):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
        '''
        fileshare_uri = self.fileshare_query(name)
        return self.delete_by_uri(fileshare_uri, forceDelete, delete_type, sync,synctimeout)
    
        # Deletes a fileshare given a fileshare name
    def delete_acl(self, name, sharename):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
        
        '''
        
        fileshare_uri = self.fileshare_query(name)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Fileshare.URI_CIFS_ACL.format(fileshare_uri, sharename),
            None)
        return 
    
    def list_acl(self, name, sharename):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
        '''
        fileshare_uri = self.fileshare_query(name)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Fileshare.URI_CIFS_ACL.format(fileshare_uri, sharename),
            None)
        o = commoncoprhdapi.json_decode(s)
        return o
    
    #Delete and List NFS ACL Routines
    
    def nfs_acl_delete(self, name, subdir):
        '''
        Delete filesystem acls or filesystem subdir
        Parameters:
            name: name of fileshare
            subdir:name of subdirectory
        
        '''
        uri_nfs_qp = Fileshare.URI_NFS_ACL
        if(subdir is not None):
            uri_nfs_qp = Fileshare.URI_NFS_ACL + '?' + "subDir=" + subdir
        
        fileshare_uri = self.fileshare_query(name)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            uri_nfs_qp.format(fileshare_uri),
            None)
        return 
    
    def nfs_acl_list(self, name, alldir, subdir):
        '''
        List the acl's of a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
            alldir : list the acls for all the directories
            subdir : list the acl's of a particular subdirectory
            
        '''
        uri_nfs_qp = Fileshare.URI_NFS_ACL
        if(alldir == True):
            uri_nfs_qp = Fileshare.URI_NFS_ACL + '?' + "allDirs=true"
        if(subdir is not None):
            uri_nfs_qp = Fileshare.URI_NFS_ACL + '?' + "subDir=" + subdir
        fileshare_uri = self.fileshare_query(name)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port, "GET",
            uri_nfs_qp.format(fileshare_uri),
            None)
        o = commoncoprhdapi.json_decode(s)
        return o
    
    # update cifs acl for given share    
    def cifs_acl(self, tenant, project, fsname, sharename, operation, user=None, permission=None, domain=None, group=None):
        path = tenant + "/" + project + "/"
        fs_name = path + fsname

        
        fs_uri = self.fileshare_query(fs_name)        
        #share_uri = self.fileshare_query(sh_name)
        
        cifs_acl_param = dict()
        cifs_acl_param['share_name'] = sharename
        if(permission):
            cifs_acl_param['permission'] = permission
        if(user):
            cifs_acl_param['user'] = user
        if(domain):
            cifs_acl_param['domain'] = domain
        if(group):
            cifs_acl_param['group'] = group

        acl_cifs_request = {'acl':[cifs_acl_param]}

        if("add"== operation):
            request = {'add': acl_cifs_request}
        elif("delete" == operation):
            request = {'delete' : acl_cifs_request}
        else:
            request = {'modify' : acl_cifs_request}
    
        body = json.dumps(request)
        
        (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, 
                    "PUT", 
                    Fileshare.URI_CIFS_ACL.format(fs_uri, sharename) , body)
        o = commoncoprhdapi.json_decode(s)
        return o
    
    
    #Main routine for NFSv4 ACL
    def nfs_acl(self, tenant, project, fsname, subdir, permissiontype, type, operation, user=None, permission=None, domain=None, group=None):
        path = tenant + "/" + project + "/"
        fs_name = path + fsname
        
        
        fs_uri = self.fileshare_query(fs_name)        
        
        
        nfs_acl_param = dict()
        if(permissiontype):
            nfs_acl_param['permission_type'] = permissiontype
        if(permission):
            nfs_acl_param['permissions'] = permission
        if(user):
            nfs_acl_param['user'] = user
        if(domain):
            nfs_acl_param['domain'] = domain
        if(type):
            nfs_acl_param['type'] = type
            
        request = dict()
        
        if("add" == operation):
            request = {'add': [nfs_acl_param] }
        if("delete" == operation):
            request = {'delete' : [nfs_acl_param]}
        if("update" == operation):
            request = {'modify' : [nfs_acl_param]}
        if(subdir):
            request['subDir']= subdir 
        body = json.dumps(request)
        
        (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port, 
                    "PUT", 
                    Fileshare.URI_NFS_ACL.format(fs_uri) , body)
        o = commoncoprhdapi.json_decode(s)
        return o



    # Deletes a fileshare given a fileshare uri
    def delete_by_uri(self, uri, forceDelete=False, delete_type='FULL', sync=False,synctimeout=0):
        '''
        Deletes a fileshare based on fileshare uri
        Parameters:
            uri: uri of fileshare
        '''
        request = {"forceDelete": forceDelete,"delete_type": delete_type}
        body = json.dumps(request)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_DEACTIVATE.format(uri),
            body)
        if(not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync,synctimeout)
        return o

    def get_exports_by_uri(self, uri, subDir=None, allDir=None):
        '''
        Get a fileshare export based on fileshare uri
        Parameters:
            uri: uri of fileshare
        '''
        params = ''
        if (subDir):
            params += '&' if ('?' in params) else '?'
            params += "subDir=" + subDir
        elif (allDir):
            params += '&' if ('?' in params) else '?'
            params += "allDir=" + "true"
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_FILESHARE_SHOW_EXPORTS.format(uri) + params,
            None)
        if(not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(not o):
            return None
        return o

    def get_exports(self, name, subDir, allDir):
        '''
        Get a fileshare export based on fileshare name
        Parameters:
            name: name of fileshare
        '''
        fileshare_uri = self.fileshare_query(name)
        return self.get_exports_by_uri(fileshare_uri, subDir, allDir)

    def get_shares_by_uri(self, uri):
        '''
        Get a fileshare export based on fileshare uri
        Parameters:
            uri: uri of fileshare
        '''
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_FILESHARE_SMB_EXPORTS.format(uri),
            None)
        if (not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(not o):
            return None
        return o

    def get_shares(self, name):
        '''
        Get a fileshare shares based on fileshare name
        Parameters:
            name: name of fileshare
        '''
        fileshare_uri = self.fileshare_query(name)
        return self.get_shares_by_uri(fileshare_uri)

    # Queries a fileshare given its name
    def fileshare_query(self, name):
        '''
        Makes REST API call to query the fileshare by name
        Parameters:
            name: name of fileshare
        Returns:
            Fileshare details in JSON response payload
        '''

        from project import Project
        if (commoncoprhdapi.is_uri(name)):
            return name
        (pname, label) = commoncoprhdapi.get_parent_child_from_xpath(name)
        if(not pname):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Project name  not specified")

        proj = Project(self.__ipAddr, self.__port)
        puri = proj.project_query(pname)
        puri = puri.strip()
        uris = self.search_fileshares(puri)
        for uri in uris:
            fileshare = self.show_by_uri(uri)
            if (fileshare and fileshare['name'] == label):
                return fileshare['id']
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Filesystem " + label + ": not found")

    # Mounts the fileshare to the mount_dir
    def mount(self, name, mount_dir):
        '''
        First we need to export the fileshare to the current machine
        Then we need to find the mount path
        then we need to mount the fileshare to the specified directory
        '''

        #share = self.show(name)
        subdir = None
        alldir = None
        fsExportInfo = self.get_exports(name, subdir, alldir)
        if(fsExportInfo and "filesystem_export" in fsExportInfo and
           len(fsExportInfo["filesystem_export"]) > 0):
            fsExport = fsExportInfo["filesystem_export"][0]

            mount_point = fsExport["mount_point"]
            mount_cmd = 'mount ' + mount_point + " " + mount_dir

            (o, h) = commands.getstatusoutput(mount_cmd)
            if(o == 0):
                return (
                    "Filesystem: " +
                    name + " mounted to " +
                    mount_dir + " successfully")
            raise SOSError(SOSError.CMD_LINE_ERR,
                           "Unable to mount " + name +
                           " to " + mount_dir + "\nRoot cause: " + h)
        else:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "error: Filesystem: " + name +
                           " is not exported. Export it first.")

    # Timeout handler for synchronous operations
    def timeout_handler(self):
        self.isTimeout = True

    # Blocks the opertaion until the task is complete/error out/timeout
    def check_for_sync(self, result, sync, synctimeout=0):
        if(sync):
            if 'resource' in result :
                if(len(result["resource"]) > 0):
                    resource = result["resource"]
                    return (
                        commoncoprhdapi.block_until_complete("fileshare", resource["id"],
                                                    result["id"], self.__ipAddr,
                                                    self.__port,synctimeout)
                    )
                else:
                    raise SOSError(
                        SOSError.SOS_FAILURE_ERR,
                        "error: task list is empty, no task response found")
        else:
            return result
        
    
    # Blocks the replication operation until the task is complete/error out/timeout
    def check_for_sync_replication(self, result, sync, synctimeout=0):
        if(sync):
            if 'task' in result :
                task = result['task']
                task_element = task[0]
                if(len(task_element['resource']) > 0):
                    resource = task_element['resource']
                    return (
                        commoncoprhdapi.block_until_complete("fileshare", resource["id"],
                                                    task_element['id'], self.__ipAddr,
                                                    self.__port,synctimeout)
                    )
                else:
                    raise SOSError(
                        SOSError.SOS_FAILURE_ERR,
                        "error: task list is empty, no task response found")
        else:
            return result


    def list_tasks(self, project_name, fileshare_name=None, task_id=None):
        return (
            commoncoprhdapi.list_tasks(self.__ipAddr, self.__port, "fileshare",
                              project_name, fileshare_name, task_id)
        )
    
    def expand(self, name, new_size, sync=False,synctimeout=0):

        fileshare_detail = self.show(name)
        current_size = float(fileshare_detail["capacity_gb"])

        if(new_size <= current_size):
            raise SOSError(
                SOSError.VALUE_ERR,
                "error: Incorrect value of new size: " + str(new_size) +
                " bytes\nNew size must be greater than current size: " +
                str(current_size) + " bytes")

        body = json.dumps({
            "new_size": new_size
        })

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_EXPAND.format(fileshare_detail["id"]),
            body)
        if(not s):
            return None
        o = commoncoprhdapi.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync,synctimeout)
        return o
    
    def assign_policy(self, filesharename, policyname, tenantname, policyid):
        fsname = self.show(filesharename)
        fsid = fsname['id']

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            Fileshare.URI_POLICY_ASSIGN.format(fsid, policyid),
            None)
        
        return
    
    def unassign_policy(self, filesharename, policyname, tenantname, policyid):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            Fileshare.URI_POLICY_UNASSIGN.format(fsid, policyid),
            None)
        
        return
    
    def policy_list(self, filesharename):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_POLICY_LIST.format(fsid),
            None)
        
        res = commoncoprhdapi.json_decode(s)
        return res['file_policy']

    def continous_copies_start(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_START.format(fsid),
            body)
        o = commoncoprhdapi.json_decode(s)
        
        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_pause(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_PAUSE.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_resume(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_RESUME.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_stop(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_STOP.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_failover(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_FAILOVER.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_failback(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}
        
        body = None

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_FAILBACK.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_create(self, filesharename, sync, targetname=None, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        parms = {
                     'copy_name' : targetname,
                     'type' : "REMOTE_MIRROR"}

        body = json.dumps(parms)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_CREATE.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_deactivate(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        parms = {
                     'delete_type' : "FULL"}

        body = json.dumps(parms)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_DEACTIVATE.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def continous_copies_refresh(self, filesharename, sync, synctimeout=0):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        copy_dict = {
                     'type' : "REMOTE_MIRROR"}
        copy_list = []
        copy_list.append(copy_dict)
        parms = {
                 'file_copy' : copy_list}

        body = json.dumps(parms)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_CONTINUOS_COPIES_REFRESH.format(fsid),
            body)
        
        o = commoncoprhdapi.json_decode(s)

        if(sync):
            return self.check_for_sync_replication(o, sync, synctimeout)
        else:
            return
    
    def change_vpool(self, filesharename, vpoolid):
        fsname = self.show(filesharename)
        fsid = fsname['id']
         
        parms = {
                 'vpool' : vpoolid}
        

        body = json.dumps(parms)
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            Fileshare.URI_VPOOL_CHANGE.format(fsid),
            body)

        return
    
    def schedule_snapshots_list(self, filesharename, policyname, tenantname, policyid):
        fsname = self.show(filesharename)
        fsid = fsname['id']
        
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_SCHEDULE_SNAPSHOTS_LIST.format(fsid, policyid),
            None)
        
        o = commoncoprhdapi.json_decode(s)
        return o

# Fileshare Create routines



def fileshare_create(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    size = commoncoprhdapi.to_bytes(args.size)
    if not size:
        raise SOSError(SOSError.CMD_LINE_ERR,
                       'error: Invalid input for -size')
    if(not args.tenant):
        args.tenant = ""
    try:
        obj = Fileshare(args.ip, args.port)
        res = obj.create(args.tenant + "/" + args.project,
                         args.name,
                         size,
                         args.varray,
                         args.vpool,
                         args.id,
                         None,
                         args.sync,
                         args.advlim,
                         args.softlim,
                         args.grace,args.synctimeout)
#        if(args.sync == False):
#            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Create failed: " + e.err_text)
        else:
            raise e

# fileshare Update routines


'''def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Filesystem Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a filesystem')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of filesystem',
                                metavar='<filesystemname>',
                                dest='name',
                                required=True)
    update_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-label', '-l',
                                help='New label of filesystem',
                                metavar='<label>',
                                dest='label',
                                required=True)
    mandatory_args.add_argument('-vpool', '-vp',
                                help='Name of New vpool',
                                metavar='<vpoolname>',
                                dest='vpool',
                                required=True)

    update_parser.set_defaults(func=fileshare_update)


def fileshare_update(args):
    if(not args.tenant):
        args.tenant = ""

    try:
        obj = Fileshare(args.ip, args.port)
        res = obj.update(args.tenant + "/" + args.project + "/" + args.name,
                         args.label,
                         args.vpool)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Update failed: " + e.err_text)
        else:
            raise e'''
       
       


def fileshare_update(args):
    if(not args.tenant):
        args.tenant = ""

    try:
        obj = Fileshare(args.ip, args.port)
        res = obj.update(args.tenant + "/" + args.project + "/" + args.name,
                         args.advlim,
                         args.softlim,
                         args.grace)
    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Update failed: " + e.err_text)
        else:
            raise e       



# Fileshare Delete routines



def fileshare_delete(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        obj.delete(
            args.tenant + "/" + args.project + "/" + args.name,
            args.forceDelete, args.delete_type, args.sync,args.synctimeout)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("delete", "filesystem",
                                        e.err_text, e.err_code)
    # cifs acl update parse
def fileshare_acl(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.user and not args.permission):
            raise SOSError(SOSError.CMD_LINE_ERR, "Anonymous user should be provided to add/update/delete acl rule")
        if(args.user and args.group):
            raise SOSError(SOSError.CMD_LINE_ERR, "User and Group cannot be specified together")	
        
        
        res = obj.cifs_acl(args.tenant, args.project, 
                           args.name, 
                           args.share, 
                           args.operation, 
                           args.user, 
                           args.permission,
                           args.domain,
                           args.group)


    except SOSError as e:
                
        commoncoprhdapi.format_err_msg_and_raise("share-acl", "filesystem",
                                        e.err_text, e.err_code)



#NFSv4 ACL parser



def nfs_acl(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        
        res = obj.nfs_acl(args.tenant, args.project, 
                           args.name, 
                           args.subdir,
                           args.permissiontype, 
                           args.type,
                           args.operation, 
                           args.user, 
                           args.permissions,
                           args.domain)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("nfs-acl", "filesystem",
                                        e.err_text, e.err_code)

# Fileshare Delete routines

def acl_delete_parser(subcommand_parsers, common_parser):
    acl_delete_parser = subcommand_parsers.add_parser(
        'delete-acl',
        description='ViPR ACL Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a ACL of share')
    mandatory_args = acl_delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    acl_delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-share', '-sh',
                               help='Name of SMB share',
                               dest='share',
                               required=True)
    
    acl_delete_parser.set_defaults(func=fileshare_acl_delete)


def fileshare_acl_delete(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        obj.delete_acl(
            args.tenant + "/" + args.project + "/" + args.name,
            args.share)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("delete-acl", "filesystem",
                                        e.err_text, e.err_code)
 


def nfs_acl_delete(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        res = obj.nfs_acl_delete(
            args.tenant + "/" + args.project + "/" + args.name, args.subdir)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("delete-nfs-acl", "filesystem",
                                        e.err_text, e.err_code)
       
        
# routine to list the acls of a share .


def fileshare_acl_list(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        res = obj.list_acl(
            args.tenant + "/" + args.project + "/" + args.name,
            args.share)
        if ( res == {}):
            print " No ACLs for the share"
        else:
            from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
            TableGenerator(res['acl'], ['errorType','filesystem_id','permission','share_name','user']).printTable() 
        
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("list-acl", "filesystem",
                                        e.err_text, e.err_code)
        
        


#NFS ACL LIST PARSER


def nfs_acl_list(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    resultList = []
    try:
        res = obj.nfs_acl_list(
            args.tenant + "/" + args.project + "/" + args.name ,args.alldir, args.subdir)
        
        if ( len(res) == 0 ):
            print " No NFSv4 ACLs for the Filesystem/Subdirectory"
        else:
            from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
            TableGenerator(res['nfs_acl'], ['domain','user','permissions','permission_type','type']).printTable() 
        
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("list-nfs-acl", "filesystem",
                                        e.err_text, e.err_code)
       
# Fileshare Export routines




def fileshare_export(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")

    try:
        if(args.protocol == "CIFS"):
            if(args.share is None):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               'error: -share is required for CIFS export')
            if(args.desc is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    'error: -description is required for CIFS export')
        else:

            if(args.security is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    'error: -security is required for ' +
                    args.protocol + ' export')
            if(args.permission is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    'error: -permission is required for ' +
                    args.protocol + ' export')
            if(args.root_user is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    'error: -rootuser is required for ' +
                    args.protocol + ' export')
            if(args.endpoint is None):
                raise SOSError(
                    SOSError.CMD_LINE_ERR,
                    'error: -endpoint is required for ' +
                    args.protocol + ' export')

        if(not args.tenant):
            args.tenant = ""
        obj = Fileshare(args.ip, args.port)
        res = obj.export(
            args.tenant + "/" + args.project + "/" + args.name,
            args.security, args.permission, args.root_user, args.endpoint,
            args.protocol, args.share, args.desc,
            args.permission_type, args.subdir, args.sync,args.synctimeout)

#        if(args.sync == False):
#            return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Export failed: " + e.err_text)
        else:
            raise e


# Fileshare UnExport routines


def fileshare_unexport(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    try:

        if(args.protocol == "CIFS"):
            if(args.share is None):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               'error: -share is required for CIFS unexport')
        
        obj = Fileshare(args.ip, args.port)
        if(not args.tenant):
            args.tenant = ""
        res = obj.unexport(args.tenant + "/" + args.project + "/" + args.name, args.protocol, args.share, args.subdir, args.alldir, args.sync,args.synctimeout)
#        if(args.sync == False):
#            return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Unexport failed: " + e.err_text)
        else:
            raise e


# fileshare ingest routines





def unmanaged_filesystem_ingest(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.unmanaged_filesystem_ingest(
            args.tenant, args.project,
            args.varray, args.vpool, args.filesystems)
    except SOSError as e:
        raise e


def unmanaged_filesystem_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        res = obj.unmanaged_filesystem_show(args.filesystem)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e

# Fileshare Show routines


def fileshare_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.show(args.tenant + "/" + args.project + "/" + args.name,
                       False, args.xml)
        if(args.xml):
            return commoncoprhdapi.format_xml(res)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e







def fileshare_exports_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.get_exports(
            args.tenant + "/" + args.project + "/" + args.name, args.subdir, args.alldir)
        if(res):
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e




def fileshare_export_rule(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(not args.roothosts and not args.readonlyhosts and not args.readwritehosts):
            raise SOSError(SOSError.CMD_LINE_ERR, "At least one of the arguments : roothosts or readonlyhosts or readwritehosts should be provided to add/Update/delete the export rule")
        if(not args.user):
            raise SOSError(SOSError.CMD_LINE_ERR, "Anonymous user should be provided to add/update/delete export rule")
        res = obj.export_rule(
            args.tenant + "/" + args.project + "/" + args.name, args.operation, args.securityflavor, args.user, args.roothosts, args.readonlyhosts, args.readwritehosts, args.subdir)

    except SOSError as e:
        raise e



def fileshare_shares_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.get_shares(
            args.tenant + "/" + args.project + "/" + args.name)
        if(res):
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e


# Fileshare List routines


def fileshare_list(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        result = obj.list_fileshares(args.tenant + "/" + args.project)
        if(len(result) > 0):
            if(not args.verbose):
                for record in result:
                    if("fs_exports" in record):
                        del record["fs_exports"]
                    if("project" in record and "name" in record["project"]):
                        del record["project"]["name"]
                    if("vpool" in record and "vpool_params" in record["vpool"]
                       and record["vpool"]["vpool_params"]):
                        for vpool_param in record["vpool"]["vpool_params"]:
                            record[vpool_param["name"]] = vpool_param["value"]
                        record["vpool"] = None

                # show a short table
                from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
                if(not args.long):
                    TableGenerator(result, ['name', 'capacity_gb',
                                            'protocols']).printTable()
                else:
                    TableGenerator(
                        result,
                        ['name', 'capacity_gb', 'protocols',
                         'thinly_provisioned', 'tags']).printTable()
            # show all items in json format
            else:
                return commoncoprhdapi.format_json_object(result)

        else:
            return
    except SOSError as e:
        raise e


# Fileshare mount routines


def fileshare_mount(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.mount(args.tenant + "/" + args.project + "/" + args.name,
                        args.mount_dir)
    except SOSError as e:
        raise e





def fileshare_list_tasks(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        if(args.id):
            res = obj.list_tasks(
                args.tenant + "/" + args.project,
                args.name, args.id)
            if(res):
                return commoncoprhdapi.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant + "/" + args.project, args.name)
            if(res and len(res) > 0):
                if(args.verbose):
                    return commoncoprhdapi.format_json_object(res)
                else:
                    from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
                    TableGenerator(res,
                                   ["module/id", "name", "state"]).printTable()
        else:
            res = obj.list_tasks(args.tenant + "/" + args.project)
            if(res and len(res) > 0):
                if(not args.verbose):
                    from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
                    TableGenerator(res,
                                   ["module/id", "name", "state"]).printTable()
                else:
                    return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        raise e




def fileshare_expand(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    size = commoncoprhdapi.to_bytes(args.size)
    if(not size):
        raise SOSError(SOSError.CMD_LINE_ERR, 'error: Invalid input for -size')

    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        res = obj.expand(args.tenant + "/" + args.project +
                         "/" + args.name, size, args.sync,args.synctimeout)
    except SOSError as e:
        raise e




def fileshare_tag(args):
    if(not args.tenant):
        args.tenant = ""

    try:
        if(args.add is None and args.remove is None):
            raise SOSError(
                SOSError.CMD_LINE_ERR,
                "viprcli fileshare tag: error: at least one of " +
                "the arguments -add -remove is required")

        obj = Fileshare(args.ip, args.port)
        res = obj.tag(args.tenant + "/" + args.project + "/" + args.name,
                      args.add,
                      args.remove)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("fileshare", "tag",
                                        e.err_text, e.err_code)
        

def assign_policy(args):
    try:
        from schedulepolicy import Schedulepolicy
        policy = Schedulepolicy(args.ip,
                        args.port).get_policy_from_name(args.polname, args.tenant)
        policyid = policy['id']
        obj = Fileshare(args.ip, args.port)
        
        res = obj.assign_policy(args.tenant + "/" + args.project + "/" + args.name,
                      args.polname,
                      args.tenant, policyid)
        return
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("fileshare", "assign",
                                        e.err_text, e.err_code)
        




def unassign_policy(args):
    try:
        from schedulepolicy import Schedulepolicy
        policy = Schedulepolicy(args.ip,
                        args.port).get_policy_from_name(args.polname, args.tenant)
        policyid = policy['id']
        obj = Fileshare(args.ip, args.port)
        
        res = obj.unassign_policy(args.tenant + "/" + args.project + "/" + args.name,
                      args.polname,
                      args.tenant, policyid)
        return
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("fileshare", "un-assign",
                                        e.err_text, e.err_code)
        



def policy_list(args):
    try:
        obj = Fileshare(args.ip, args.port)
        res = obj.policy_list(args.tenant + "/" + args.project + "/" + args.name)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("fileshare", "assign",
                                        e.err_text, e.err_code)

        


def continous_copies_start(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_start(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    



def continous_copies_pause(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_pause(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    


def continous_copies_resume(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_resume(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    

def continous_copies_stop(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_stop(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    
    



def continous_copies_failover(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_failover(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    


def continous_copies_failback(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_failback(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    

def continous_copies_create(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_create(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.target, args.synctimeout)
        return
    except SOSError as e:
        raise e
    
    

def continous_copies_deactivate(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_deactivate(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    



def continous_copies_refresh(args):
    if not args.sync and args.synctimeout !=0:
        raise SOSError(SOSError.CMD_LINE_ERR,"error: Cannot use synctimeout without Sync ")
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.continous_copies_refresh(args.tenant + "/" + args.project + "/" + args.name, args.sync, args.synctimeout)
        return
    except SOSError as e:
        raise e
    


def change_vpool(args):
    obj = Fileshare(args.ip, args.port)
    from virtualpool import VirtualPool
    vpool_obj = VirtualPool(args.ip, args.port)
    vpoolid = vpool_obj.vpool_query(args.vpool, "file")
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.change_vpool(args.tenant + "/" + args.project + "/" + args.name, vpoolid)
        return
    except SOSError as e:
        raise e
    


def schedule_snapshots_list(args):
    try:
        from schedulepolicy import Schedulepolicy
        policy = Schedulepolicy(args.ip,
                        args.port).get_policy_from_name(args.polname, args.tenant)
        policyid = policy['id']
        obj = Fileshare(args.ip, args.port)
        
        res = obj.schedule_snapshots_list(args.tenant + "/" + args.project + "/" + args.name,
                      args.polname,
                      args.tenant, policyid)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("fileshare", "schedule snapshots",
                                        e.err_text, e.err_code)


#
# Fileshare Main parser routine
#
