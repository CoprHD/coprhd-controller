#!/usr/bin/python

# Copyright (c) 2012-13 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
import tag
import json
import socket
import commands
from common import SOSError
from threading import Timer


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
            common.search_by_project_and_name(
                projectName, fileshareName,
                Fileshare.URI_SEARCH_FILESHARES_BY_PROJECT_AND_NAME,
                self.__ipAddr, self.__port)
        )

    def search_fileshares(self, project_uri, fileshare=None):

        uri = Fileshare.URI_SEARCH_FILESHARES.format(project_uri)
        if (fileshare is not None):
            uri = Fileshare.URI_SEARCH_FILESHARES_BY_PROJECT_AND_NAME.format(
                project_uri, fileshare)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET", uri, None)
        o = common.json_decode(s)
        if not o:
            return []

        fileshare_uris = []
        resources = common.get_node_value(o, "resource")
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

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Fileshare.URI_PROJECT_RESOURCES.format(project_uri), None)
        o = common.json_decode(s)
        if not o:
            return []

        fileshare_uris = []
        resources = common.get_node_value(o, "project_resource")
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

        if (common.is_uri(name)):
            return name

        (pname, label) = common.get_parent_child_from_xpath(name)
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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port,
                "GET", Fileshare.URI_FILESHARE.format(uri),
                None, None, xml)
            return s

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET", Fileshare.URI_FILESHARE.format(uri), None)
        if(not s):
            return None
        o = common.json_decode(s)
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_UNMANAGED_FILESYSTEM_INGEST,
            body)
        o = common.json_decode(s)
        return o

    def unmanaged_filesystem_show(self, filesystem):
        '''
        This function is to show the details of unmanaged filesystem
        from  ViPR.
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_UNMANAGED_FILESYSTEM_SHOW.format(filesystem),
            None)
        o = common.json_decode(s)
        return o

    # Creates a fileshare given label, project, vpool, size and id for vnx file
    def create(self, project, label, size, varray, vpool, id, protocol, sync):
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
            'fs_id' : id
        }

        if(protocol):
            parms["protocols"] = protocol

        body = json.dumps(parms)

        proj = Project(self.__ipAddr, self.__port)
        project_uri = proj.project_query(project)

        try:
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "POST",
                Fileshare.URI_FILESHARE_CREATE.format(project_uri),
                body)
            o = common.json_decode(s)
            if(sync):
                #fileshare = self.show(name, True)
                return self.check_for_sync(o, sync)
            
            else:
                return o
        except SOSError as e:
            errorMessage = str(e).replace(vpool_uri, vpool)
            errorMessage = errorMessage.replace(varray_uri, varray)
            common.format_err_msg_and_raise("create", "filesystem",
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

    # Update a fileshare information
    def update(self, name, label, vpool):
        '''
        Makes REST API call to update a fileshare information
        Parameters:
            name: name of the fileshare to be updated
            label: new name of the fileshare
            vpool: name of vpool
        Returns
            Created task details in JSON response payload
        '''
        fileshare_uri = self.fileshare_query(name)

        from virtualpool import VirtualPool

        vpool_obj = VirtualPool(self.__ipAddr, self.__port)
        vpool_uri = vpool_obj.vpool_query(vpool, "file")

        body = json.dumps({'share':
                           {
                               'label': label,
                               'vpool': {"id": vpool_uri}
                           }
                           })

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "PUT",
            Fileshare.URI_FILESHARE.format(fileshare_uri), body)
        o = common.json_decode(s)
        return o

    # Exports a fileshare to a host given a fileshare name and the host name
    def export(
            self, name, security_type, permission, root_user,
            endpoints, protocol, share_name, share_description,
            permission_type, sub_dir, sync):
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

                (s, h) = common.service_json_request(
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
                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    Fileshare.URI_FILESHARE_EXPORTS.format(fileshare_uri),
                    body)
            if(not s):
                return None
            o = common.json_decode(s)
            if(sync):
                return self.check_for_sync(o, sync)
            else:
                return o
        except SOSError as e:
            errorMessage = str(e)
            if(common.is_uri(fileshare_uri)):
                errorMessage = str(e).replace(fileshare_uri, name)
            common.format_err_msg_and_raise("export", "filesystem",
                                            errorMessage, e.err_code)

    # Unexports a fileshare from a host given a fileshare name, type of
    # security and permission
    def unexport(self, name, protocol, share_name, sub_dir, all_dir, sync):
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
            (s, h) = common.service_json_request(
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
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "DELETE",
                request_uri, None)
        if(not s):
            return None
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
        else:
            return o

        # file system exports update for endpoints.
    def filesystem_export_update(
            self, name, security_type, permission, root_user,
            protocol, add_endpoints, remove_endpoints, sync):
        '''
        Makes REST API call to export update
        Parameters:
            name             :name of filesystem
            security_type    :type of security
            permission       :Permissions
            root_user        :root_user mapping
            protocol         :Protocol valid values - NFS,NFSv4
            add_endpoints    :list client end points to add
            remove_endpoints :list client end points to remove
            sync             :synchronous mode
        Returns:
            filesystem Modified result
          '''

        fileshare_uri = self.fileshare_query(name)
        if(add_endpoints or remove_endpoints):
            if(add_endpoints):
                updateparam = {
                    'add': add_endpoints
                }

            if(remove_endpoints):
                updateparam = {
                    'remove': remove_endpoints
                }
        else:
            updateparam = {}

        body = json.dumps(updateparam)

        request_uri = Fileshare.URI_FILESHARE_UNEXPORTS.format(
            fileshare_uri, protocol,
            security_type, permission, root_user)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             request_uri, body)
        if(not s):
            return None
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
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

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT", Fileshare.URI_FILESHARE_SHOW_EXPORTS.format(fileshare_uri) + params, body)
        o = common.json_decode(s)
        return o



    # Deletes a fileshare given a fileshare name
    def delete(self, name, forceDelete=False, delete_type='FULL', sync=False):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
        '''
        fileshare_uri = self.fileshare_query(name)
        return self.delete_by_uri(fileshare_uri, forceDelete, delete_type, sync)
    
        # Deletes a fileshare given a fileshare name
    def delete_acl(self, name, sharename):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
        
        '''
        
        fileshare_uri = self.fileshare_query(name)
        (s, h) = common.service_json_request(
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Fileshare.URI_CIFS_ACL.format(fileshare_uri, sharename),
            None)
        o = common.json_decode(s)
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "DELETE",
            Fileshare.URI_NFS_ACL.format(fileshare_uri),
            None)
        return 
    
    def nfs_acl_list(self, name, alldir, subdir):
        '''
        Deletes a fileshare based on fileshare name
        Parameters:
            name: name of fileshare
            
        '''
        uri_nfs_qp = Fileshare.URI_NFS_ACL
        if(alldir == True):
            uri_nfs_qp = Fileshare.URI_NFS_ACL + '?' + "allDir=true"
        if(subdir is not None):
            uri_nfs_qp = Fileshare.URI_NFS_ACL + '?' + "subDir=" + subdir
        fileshare_uri = self.fileshare_query(name)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            uri_nfs_qp.format(fileshare_uri),
            None)
        o = common.json_decode(s)
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
        
        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, 
                    "PUT", 
                    Fileshare.URI_CIFS_ACL.format(fs_uri, sharename) , body)
        o = common.json_decode(s)
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
        
        (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, 
                    "PUT", 
                    Fileshare.URI_NFS_ACL.format(fs_uri) , body)
        o = common.json_decode(s)
        return o



    # Deletes a fileshare given a fileshare uri
    def delete_by_uri(self, uri, forceDelete=False, delete_type='FULL', sync=False):
        '''
        Deletes a fileshare based on fileshare uri
        Parameters:
            uri: uri of fileshare
        '''
        request = {"forceDelete": forceDelete,"delete_type": delete_type}
        body = json.dumps(request)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_DEACTIVATE.format(uri),
            body)
        if(not s):
            return None
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_FILESHARE_SHOW_EXPORTS.format(uri) + params,
            None)
        if(not s):
            return None
        o = common.json_decode(s)
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
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Fileshare.URI_FILESHARE_SMB_EXPORTS.format(uri),
            None)
        if (not s):
            return None
        o = common.json_decode(s)
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
        if (common.is_uri(name)):
            return name
        (pname, label) = common.get_parent_child_from_xpath(name)
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
    def check_for_sync(self, result, sync):
        if(sync):
            if(len(result["resource"]) > 0):
                resource = result["resource"]
                return (
                    common.block_until_complete("fileshare", resource["id"],
                                                result["id"], self.__ipAddr,
                                                self.__port)
                )
            else:
                raise SOSError(
                    SOSError.SOS_FAILURE_ERR,
                    "error: task list is empty, no task response found")
        else:
            return result


    def list_tasks(self, project_name, fileshare_name=None, task_id=None):
        return (
            common.list_tasks(self.__ipAddr, self.__port, "fileshare",
                              project_name, fileshare_name, task_id)
        )
    
    def expand(self, name, new_size, sync=False):

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

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Fileshare.URI_EXPAND.format(fileshare_detail["id"]),
            body)
        if(not s):
            return None
        o = common.json_decode(s)
        if(sync):
            return self.check_for_sync(o, sync)
        return o

# Fileshare Create routines


def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Filesystem Create CLI usage.',
        parents=[common_parser], conflict_handler='resolve',
        help='Create a filesystem')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    mandatory_args.add_argument(
        '-size', '-s',
        help='Size of filesystem: {number}[unit]. ' +
        'A size suffix of K for kilobytes, M for megabytes, ' +
        'G for  gigabytes, T  for terabytes is optional.' +
        'Default unit is bytes.',
        metavar='<filesharesize[kKmMgGtT]>',
        dest='size',
        required=True)
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of Project',
                                required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-vpool', '-vp',
                                metavar='<vpoolname>', dest='vpool',
                                help='Name of vpool',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                help='Name of varray',
                                metavar='<varray>',
                                dest='varray',
                                required=True)
    create_parser.add_argument('-id', '-id',
                                help='Optional Id for VNX FileSystem',
                                metavar='<filesystemid>',
                                dest='id',
                                required=False)
    create_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    create_parser.set_defaults(func=fileshare_create)


def fileshare_create(args):

    size = common.to_bytes(args.size)
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
                         args.sync)
#        if(args.sync == False):
#            return common.format_json_object(res)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Create failed: " + e.err_text)
        else:
            raise e

# fileshare Update routines


def update_parser(subcommand_parsers, common_parser):
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
            raise e


# Fileshare Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Filesystem Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a filesystem')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    delete_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    delete_parser.add_argument(
        '-forceDelete', '-fd',
        metavar='<forceDelete>',
        dest='forceDelete',
        help='Delete fileshare forecibly, default false',
        default=False)
    delete_parser.add_argument(
        '-deleteType', '-dt',
        metavar='<delete_type>',
        dest='delete_type',
        help='Delete fileshare either from Inventory only or full delete, default FULL',
        default='FULL',
        choices=["FULL", "VIPR_ONLY"])
    delete_parser.set_defaults(func=fileshare_delete)


def fileshare_delete(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        obj.delete(
            args.tenant + "/" + args.project + "/" + args.name,
            args.forceDelete, args.delete_type, args.sync)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "filesystem",
                                        e.err_text, e.err_code)
    # cifs acl update parser
def cifs_acl_parser(subcommand_parsers, common_parser):
    cifs_acl_parser = subcommand_parsers.add_parser(
        'share-acl',
        description='ViPR Filesystem Export rule CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add/Update/Delete ACLs rules for file Share ')
    mandatory_args = cifs_acl_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    mandatory_args.add_argument('-share', '-sh',
                               help='Name of SMB share',
                               dest='share',
                               required=True)
    mandatory_args.add_argument('-operation', '-op',
                                choices=["add", "update", "delete"],
                                dest='operation',
                                metavar='<acloperation>',
                                help='cifs acl operation',
                                required=True)
    cifs_acl_parser.add_argument('-permission', '-perm',
                                    dest='permission',
                                    choices=["FullControl", "Change", "Read"],
                                    metavar='<permission>',
                                    help='Provide permission for Acl')
    cifs_acl_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    cifs_acl_parser.add_argument('-user', '-u',
                                    dest='user',
                                    metavar='<user>',
                                    help='User')
    cifs_acl_parser.add_argument('-domain','-dom',
                                    dest='domain',
                                    metavar='<domain>',
                                    help='Domain')
    cifs_acl_parser.add_argument('-group', '-grp',
                                    dest='group',
                                    metavar='<group>',
                                    help='Group')				    
    
    cifs_acl_parser.set_defaults(func=fileshare_acl)




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
                
        common.format_err_msg_and_raise("share-acl", "filesystem",
                                        e.err_text, e.err_code)



#NFSv4 ACL parser

def nfs_acl_parser(subcommand_parsers, common_parser):
    nfs_acl_parser = subcommand_parsers.add_parser(
        'nfs-acl',
        description='ViPR Filesystem NFSv4 ACL CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add/Update/Delete ACLs rules for FileSystem ')
    mandatory_args = nfs_acl_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    mandatory_args.add_argument('-operation', '-op',
                                choices=["add", "update", "delete"],
                                dest='operation',
                                metavar='<acloperation>',
                                help='nfs acl operation',
                                required=True)
    mandatory_args.add_argument('-permissions', '-perms',
                                    dest='permissions',
                                    choices=["Read", "Write", "Execute","Read,write" ,"Read,Execute","Read,Write,Execute"],
                                    metavar='<permissions>',
                                    help='Provide permissions for Acl',
                                    required=True)
    nfs_acl_parser.add_argument('-permissiontype', '-permtype',
                                    dest='permissiontype',
                                    choices=["allow", "deny"],
                                    metavar='<permission_type>',
                                    help='Provide permission type for Acl')
    nfs_acl_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-user', '-u',
                                    dest='user',
                                    metavar='<user>',
                                    help='User',
                                    required=True)
    nfs_acl_parser.add_argument('-domain','-dom',
                                    dest='domain',
                                    metavar='<domain>',
                                    help='Domain')
    
    nfs_acl_parser.add_argument('-type','-t',
                                    dest='type',
                                    metavar='<type>',
                                    choices = ["user","group", "wellknown"],
                                    help='Type')
    nfs_acl_parser.add_argument('-subdirectory', '-subdir',
                                    dest='subdir',
                                    metavar='<subdirectory>',
                                    help='Subdirectory Name ')  
                  
    nfs_acl_parser.set_defaults(func=nfs_acl)


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
        common.format_err_msg_and_raise("nfs-acl", "filesystem",
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
        common.format_err_msg_and_raise("delete-acl", "filesystem",
                                        e.err_text, e.err_code)
 

def nfs_acl_delete_parser(subcommand_parsers, common_parser):
    nfs_acl_delete_parser = subcommand_parsers.add_parser(
        'nfs-delete-acl',
        description='ViPR ACL Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a ACL of Filesystem')
    mandatory_args = nfs_acl_delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    nfs_acl_delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    nfs_acl_delete_parser.add_argument('-subdirectory', '-subdir',
                                    dest='subdir',
                                    metavar='<subdirectory>',
                                    help='Subdirectory Name ') 
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    nfs_acl_delete_parser.set_defaults(func=nfs_acl_delete)


def nfs_acl_delete(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    try:
        res = obj.nfs_acl_delete(
            args.tenant + "/" + args.project + "/" + args.name, args.subdir)
    except SOSError as e:
        common.format_err_msg_and_raise("delete-nfs-acl", "filesystem",
                                        e.err_text, e.err_code)
       
        
# routine to list the acls of a share .

def acl_list_parser(subcommand_parsers, common_parser):
    acl_list_parser = subcommand_parsers.add_parser(
        'list-acl',
        description='ViPR ACL List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='LIST ACL of share')
    mandatory_args = acl_list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    acl_list_parser.add_argument('-tenant', '-tn',
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
    
 
    acl_list_parser.set_defaults(func=fileshare_acl_list)


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
            from common import TableGenerator
            TableGenerator(res['acl'], ['errorType','filesystem_id','permission','share_name','user']).printTable() 
        
    except SOSError as e:
        common.format_err_msg_and_raise("list-acl", "filesystem",
                                        e.err_text, e.err_code)
        
        


#NFS ACL LIST PARSER

def nfs_acl_list_parser(subcommand_parsers, common_parser):
    nfs_acl_list_parser = subcommand_parsers.add_parser(
        'nfs-list-acl',
        description='ViPR NFS ACL List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='LIST ACL of Filesystem')
    mandatory_args = nfs_acl_list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<filesystemname>',
                                dest='name',
                                help='Name of Filesystem',
                                required=True)
    nfs_acl_list_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    nfs_acl_list_parser.add_argument('-subdirectory', '-subdir',
                                    dest='subdir',
                                    metavar='<subdirectory>',
                                    help='Subdirectory Name ')  
    nfs_acl_list_parser.add_argument('-alldirectories', '-alldir',
                                    dest='alldir',
                                    action = 'store_true',
                                    help='Enable All Directories')        
    
    nfs_acl_list_parser.set_defaults(func=nfs_acl_list)


def nfs_acl_list(args):
    if(not args.tenant):
        args.tenant = ""
    obj = Fileshare(args.ip, args.port)
    resultList = []
    try:
        res = obj.nfs_acl_list(
            args.tenant + "/" + args.project + "/" + args.name ,args.alldir, args.subdir)
        
        if ( len(res['nfs_acl']) == 0 ):
            print " No NFSv4 ACLs for the Filesystem/Subdirectory"
        else:
            from common import TableGenerator
            TableGenerator(res['nfs_acl'][0]['ace'], ['domain','user','permissions','permission_type','type']).printTable() 
        
    except SOSError as e:
        common.format_err_msg_and_raise("list-nfs-acl", "filesystem",
                                        e.err_text, e.err_code)
       
# Fileshare Export routines


def export_parser(subcommand_parsers, common_parser):
    export_parser = subcommand_parsers.add_parser(
        'export',
        description='ViPR Filesystem Export CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Export a filesystem')
    mandatory_args = export_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    export_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    export_parser.add_argument('-security', '-sec',
                               metavar='<security>',
                               dest='security',
                               help='Security type')
    export_parser.add_argument('-permission', '-pe',
                               metavar='<permission>',
                               dest='permission',
                               help='Permission')
    export_parser.add_argument('-rootuser', '-ru',
                               metavar='<root_user>',
                               dest='root_user',
                               help='root user')
    export_parser.add_argument(
        '-endpoint', '-e',
        metavar='<endpoint>', dest='endpoint', nargs='+',
        help='Endpoints: host names, IP addresses, or netgroups')
    mandatory_args.add_argument('-protocol', '-pl',
                                help='Protocol',
                                choices=["NFS", "NFSv4", "CIFS"],
                                dest='protocol',
                                required=True)
    export_parser.add_argument('-share', '-sh',
                               help='Name of SMB share',
                               dest='share')
    export_parser.add_argument('-description', '-desc',
                               help='Description of SMB share',
                               dest='desc')
    export_parser.add_argument('-permission_type', '-pt',
                               choices=['allow', 'deny'],
                               help='Type of permission of SMB share',
                               dest='permission_type')
    export_parser.add_argument('-subdir',
                               metavar="<sub directory>",
                               help='Export to FileSystem subdirectory',
                               dest='subdir')
    export_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    export_parser.set_defaults(func=fileshare_export)


def fileshare_export(args):

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
            args.permission_type, args.subdir, args.sync)

#        if(args.sync == False):
#            return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Export failed: " + e.err_text)
        else:
            raise e


# Fileshare UnExport routines

def unexport_parser(subcommand_parsers, common_parser):
    unexport_parser = subcommand_parsers.add_parser(
        'unexport',
        description='ViPR Filesystem Unexport CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Unexport a filesystem')
    mandatory_args = unexport_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    unexport_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='Protocol',
                                choices=["NFS", "NFSv4", "CIFS"],
                                dest='protocol',
                                required=True)
    unexport_parser.add_argument('-share', '-sh',
                                 help='Name of SMB share',
                                 dest='share')
    unexport_parser.add_argument('-subdir',
                                 metavar="<sub directory>",
                                 help='Unexport from FileSystem sub-directory',
                                 dest='subdir')
    unexport_parser.add_argument('-alldir',
                                 action="store_true",
                                 help='Unexport FileSystem from all directories including sub-directories',
                                 dest='alldir')
    unexport_parser.add_argument('-synchronous', '-sync',
                                 dest='sync',
                                 help='Execute in synchronous mode',
                                 action='store_true')
    unexport_parser.set_defaults(func=fileshare_unexport)


def fileshare_unexport(args):
    try:

        if(args.protocol == "CIFS"):
            if(args.share is None):
                raise SOSError(SOSError.CMD_LINE_ERR,
                               'error: -share is required for CIFS unexport')
        
        obj = Fileshare(args.ip, args.port)
        if(not args.tenant):
            args.tenant = ""
        res = obj.unexport(args.tenant + "/" + args.project + "/" + args.name, args.protocol, args.share, args.subdir, args.alldir, args.sync)
#        if(args.sync == False):
#            return common.format_json_object(res)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(e.err_code, "Unexport failed: " + e.err_text)
        else:
            raise e


def export_update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update-export',
        description='ViPR Filesystem export update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='export update of filesystem')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='name of filesystem for export update',
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
    mandatory_args.add_argument('-security', '-sec',
                                metavar='<security>',
                                dest='security',
                                help='Security type',
                                required=True)
    mandatory_args.add_argument('-permission', '-pe',
                                metavar='<permission>',
                                dest='permission',
                                help='file share access permissions ',
                                required=True)
    mandatory_args.add_argument('-rootuser', '-ru',
                                metavar='<root_user>',
                                dest='root_user',
                                help='root user',
                                required=True)
    mandatory_args.add_argument('-protocol', '-pl',
                                help='access protocol',
                                choices=["NFS", "NFSv4"],
                                dest='protocol',
                                required=True)
    update_parser.add_argument('-add_endpoints', '-aep',
                               metavar="<AddEndPoints>",
                               help='list of client endpoints to add',
                               dest='add_endpoints',
                               nargs='+')
    update_parser.add_argument('-remove_endpoints', '-rep',
                               metavar="<RemoveEndPoints>",
                               help='list of client endpoints to remove',
                               dest='remove_endpoints',
                               nargs='+')
    update_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    update_parser.set_defaults(func=fileshare_export_update)


def fileshare_export_update(args):
    try:

        obj = Fileshare(args.ip, args.port)
        if(not args.tenant):
            args.tenant = ""
        obj.filesystem_export_update(
            args.tenant + "/" + args.project + "/" + args.name,
            args.security, args.permission, args.root_user, args.protocol,
            args.add_endpoints, args.remove_endpoints, args.sync)

    except SOSError as e:
        common.format_err_msg_and_raise("update", "filesystem",
                                        e.err_text, e.err_code)

# fileshare ingest routines


def unmanaged_parser(subcommand_parsers, common_parser):
    unmanaged_parser = subcommand_parsers.add_parser(
        'unmanaged',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Unmanaged volume operations')
    subcommand_parsers = unmanaged_parser.add_subparsers(
        help='Use one of the commands')

    # ingest unmanaged volume
    ingest_parser = subcommand_parsers.add_parser(
        'ingest',
        parents=[common_parser],
        conflict_handler='resolve',
        help='ingest unmanaged fileshares into ViPR')
    mandatory_args = ingest_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-vpool', '-vp',
                                metavar='<vpool>',
                                dest='vpool',
                                help='Name of vpool',
                                required=True)
    ingest_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                metavar='<varray>',
                                dest='varray',
                                help='Name of varray',
                                required=True)
    mandatory_args.add_argument('-filesystems', '-fs',
                                metavar='<filesystems>',
                                dest='filesystems',
                                help='Name or id of filesystem',
                                nargs='+',
                                required=True)

    # show unmanaged volume
    umshow_parser = subcommand_parsers.add_parser('show',
                                                  parents=[common_parser],
                                                  conflict_handler='resolve',
                                                  help='Show unmanaged volume')
    mandatory_args = umshow_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-filesystem', '-fs',
                                metavar='<filesystem>',
                                dest='filesystem',
                                help='Name or id of filesystem',
                                required=True)

    ingest_parser.set_defaults(func=unmanaged_filesystem_ingest)

    umshow_parser.set_defaults(func=unmanaged_filesystem_show)


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
        return common.format_json_object(res)
    except SOSError as e:
        raise e

# Fileshare Show routines


def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Filesystem Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of filesystem')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action="store_true",
                             help='Display in XML format')
    show_parser.set_defaults(func=fileshare_show)


def fileshare_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.show(args.tenant + "/" + args.project + "/" + args.name,
                       False, args.xml)
        if(args.xml):
            return common.format_xml(res)
        return common.format_json_object(res)
    except SOSError as e:
        raise e


def show_exports_parser(subcommand_parsers, common_parser):
    show_exports_parser = subcommand_parsers.add_parser(
        'show-exports',
        description='ViPR Filesystem Show exports CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show export details of filesystem')
    mandatory_args = show_exports_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    show_exports_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_exports_parser.add_argument('-subdir', 
                                     metavar='<subDirectory>',
                                     dest='subdir',
                                     help='Name of the Sub directory')
    show_exports_parser.add_argument('-alldir',
                                     dest='alldir',
                                     action='store_true',
                                     help='Show File System export information for All Directories')
    
    show_exports_parser.set_defaults(func=fileshare_exports_show)




def fileshare_exports_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.get_exports(
            args.tenant + "/" + args.project + "/" + args.name, args.subdir, args.alldir)
        if(res):
            return common.format_json_object(res)
    except SOSError as e:
        raise e



def export_rule_parser(subcommand_parsers, common_parser):
    export_rule_parser = subcommand_parsers.add_parser(
        'export-rule',
        description='ViPR Filesystem Export rule CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Add/Update/Delete Export rules for File Systems')
    mandatory_args = export_rule_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    mandatory_args.add_argument('-operation', '-op',
                                choices=["add", "update", "delete"],
                                dest='operation',
                                metavar='<exportruleoperation>',
                                help='Export rule operation',
                                required=True)
    export_rule_parser.add_argument('-roothosts', '-rhosts',
                                    dest='roothosts',
                                    nargs = '+',
                                    metavar='<roothosts>',
                                    help='Root host names')
    export_rule_parser.add_argument('-readonlyhosts', '-rohosts',
                                    dest='readonlyhosts',
                                    nargs = '+',
                                    metavar='<readonlyhosts>',
                                    help='Read only host names')
    export_rule_parser.add_argument('-readwritehosts', '-rwhosts',
                                    dest='readwritehosts',
                                    nargs = '+',
                                    metavar='<readwritehosts>',
                                    help='Read write host names')

    export_rule_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-securityflavor', '-sec',
                                choices=["sys", "krb5", "krb5i", "krb5p"],
                                metavar='<securityflavor>',
                                dest='securityflavor',
                                help='Name of Security flavor',
                                required=True)  
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    export_rule_parser.add_argument('-subdir', 
                                     metavar='<subDirectory>',
                                     dest='subdir',
                                     help='Name of the Sub Directory')
    export_rule_parser.add_argument('-user', '-u',
                                    dest='user',
                                    metavar='<user>',
                                    help='User')
    
    export_rule_parser.set_defaults(func=fileshare_export_rule)




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



def show_shares_parser(subcommand_parsers, common_parser):
    show_exports_parser = subcommand_parsers.add_parser(
        'show-shares',
        description='ViPR Filesystem Show Shares CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show shares of filesystem')
    mandatory_args = show_exports_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    show_exports_parser.add_argument('-tenant', '-tn',
                                     metavar='<tenantname>',
                                     dest='tenant',
                                     help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    show_exports_parser.set_defaults(func=fileshare_shares_show)


def fileshare_shares_show(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.get_shares(
            args.tenant + "/" + args.project + "/" + args.name)
        if(res):
            return common.format_json_object(res)
    except SOSError as e:
        raise e


# Fileshare List routines

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Filesystem List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List filesystems')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of Project',
                                required=True)
    list_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    list_parser.add_argument('-verbose', '-v',
                             dest='verbose',
                             help='List filesystems with details',
                             action='store_true')
    list_parser.add_argument('-long', '-l',
                             dest='long',
                             help='List filesystems having more headers',
                             action='store_true')
    list_parser.set_defaults(func=fileshare_list)


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
                from common import TableGenerator
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
                return common.format_json_object(result)

        else:
            return
    except SOSError as e:
        raise e


# Fileshare mount routines

def mount_parser(subcommand_parsers, common_parser):
    mount_parser = subcommand_parsers.add_parser(
        'mount',
        description='ViPR Filesystem Mount CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Mount a filesystem')
    mandatory_args = mount_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-mountdir', '-d',
                                metavar='<mountdir>',
                                dest='mount_dir',
                                help='Path of mount directory',
                                required=True)
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Fileshare',
                                required=True)
    mount_parser.add_argument('-tenant', '-tn',
                              metavar='<tenantname>',
                              dest='tenant',
                              help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)

    mount_parser.set_defaults(func=fileshare_mount)


def fileshare_mount(args):
    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.mount(args.tenant + "/" + args.project + "/" + args.name,
                        args.mount_dir)
    except SOSError as e:
        raise e


def task_parser(subcommand_parsers, common_parser):
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR Filesystem List tasks CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show details of filesystem tasks')
    mandatory_args = task_parser.add_argument_group('mandatory arguments')

    task_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    task_parser.add_argument('-name', '-n',
                             dest='name',
                             metavar='<filesystemname>',
                             help='Name of filesystem')
    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')
    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')
    task_parser.set_defaults(func=fileshare_list_tasks)


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
                return common.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant + "/" + args.project, args.name)
            if(res and len(res) > 0):
                if(args.verbose):
                    return common.format_json_object(res)
                else:
                    from common import TableGenerator
                    TableGenerator(res,
                                   ["module/id", "name", "state"]).printTable()
        else:
            res = obj.list_tasks(args.tenant + "/" + args.project)
            if(res and len(res) > 0):
                if(not args.verbose):
                    from common import TableGenerator
                    TableGenerator(res,
                                   ["module/id", "name", "state"]).printTable()
                else:
                    return common.format_json_object(res)

    except SOSError as e:
        raise e


def expand_parser(subcommand_parsers, common_parser):
    expand_parser = subcommand_parsers.add_parser(
        'expand',
        description='ViPR Filesystem Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Expand the filesystem')
    mandatory_args = expand_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                dest='name',
                                metavar='<filesystemname>',
                                help='Name of Filesystem',
                                required=True)
    expand_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    mandatory_args.add_argument('-project', '-pr',
                                metavar='<projectname>',
                                dest='project',
                                help='Name of project',
                                required=True)
    mandatory_args.add_argument(
        '-size', '-s',
        help='New size of filesystem: {number}[unit]. ' +
        'A size suffix of K for kilobytes, M for megabytes, ' +
        'G for gigabytes, T for terabytes is optional.' +
        'Default unit is bytes.',
        metavar='<filesystemsize[kKmMgGtT]>',
        dest='size', required=True)
    expand_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')
    expand_parser.set_defaults(func=fileshare_expand)


def fileshare_expand(args):
    size = common.to_bytes(args.size)
    if(not size):
        raise SOSError(SOSError.CMD_LINE_ERR, 'error: Invalid input for -size')

    obj = Fileshare(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""

        res = obj.expand(args.tenant + "/" + args.project +
                         "/" + args.name, size, args.sync)
    except SOSError as e:
        raise e


def tag_parser(subcommand_parsers, common_parser):
    tag_parser = subcommand_parsers.add_parser(
        'tag',
        description='ViPR Fileshare Tag CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Tag a filesystem')
    mandatory_args = tag_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of filesystem',
                                metavar='<filesystemname>',
                                dest='name',
                                required=True)
    tag_parser.add_argument('-tenant', '-tn',
                            metavar='<tenantname>',
                            dest='tenant',
                            help='Name of tenant')

    tag.add_mandatory_project_parameter(mandatory_args)

    tag.add_tag_parameters(tag_parser)

    tag_parser.set_defaults(func=fileshare_tag)


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
        common.format_err_msg_and_raise("fileshare", "tag",
                                        e.err_text, e.err_code)


#
# Fileshare Main parser routine
#
def fileshare_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser(
        'filesystem',
        description='ViPR filesystem CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on filesystem')
    subcommand_parsers = parser.add_subparsers(help='Use one of subcommands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # update command parser
    # update_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # show exports command parser
    show_exports_parser(subcommand_parsers, common_parser)

    # show shares command parser
    show_shares_parser(subcommand_parsers, common_parser)

    # export update command parser
    export_update_parser(subcommand_parsers, common_parser)

    # export command parser
    export_parser(subcommand_parsers, common_parser)

    # unexport command parser
    unexport_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # mount command parser
    mount_parser(subcommand_parsers, common_parser)

    # expand fileshare parser
    expand_parser(subcommand_parsers, common_parser)

    # task list command parser
    task_parser(subcommand_parsers, common_parser)

    # unmanaged filesystem  command parser
    unmanaged_parser(subcommand_parsers, common_parser)

    # tag  filesystem  command parser
    tag_parser(subcommand_parsers, common_parser)

    # Export rule filesystem command parser
    export_rule_parser(subcommand_parsers, common_parser)
    
    # acl delete command parser
    acl_delete_parser(subcommand_parsers, common_parser)
    
    #ACL LIST Parser 
    
    acl_list_parser(subcommand_parsers, common_parser)

    #ACL fileshare command parser 
    cifs_acl_parser(subcommand_parsers, common_parser)
    
    #ACL FOR NFS FILESYSTEM
    nfs_acl_parser(subcommand_parsers, common_parser)
    
    #ACL LIST PARSER
    nfs_acl_list_parser(subcommand_parsers, common_parser)
    
    
    #ACL DELETE PARSER
    nfs_acl_delete_parser(subcommand_parsers, common_parser)
