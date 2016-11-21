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
from manila.share.drivers.coprhd.helpers.commoncoprhdapi import SOSError
import json
import sys
import tag
import quota


class Project(object):

    '''
    The class definition for operations on 'Project'.
    '''

    # Commonly used URIs for the 'Project' module
    URI_PROJECT_LIST = '/tenants/{0}/projects'
    URI_PROJECT = '/projects/{0}'
    URI_PROJECT_RESOURCES = '/projects/{0}/resources'
    URI_PROJECT_ACL = '/projects/{0}/acl'
    URI_DEACTIVATE = URI_PROJECT + '/deactivate'
    URI_TAG_PROJECT = URI_PROJECT + "/tags"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def project_create(self, name, tenant_name):
        '''
        Makes REST API call to create project under a tenant
        Parameters:
            name: name of project
            tenant_name: name of the tenant under which project
                         is to be created
        Returns:
            Created project details in JSON response payload
        '''
        from tenant import Tenant
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        try:
            tenant_uri = tenant_obj.tenant_query(tenant_name)
        except SOSError as e:
            raise e

        project_already_exists = True

        try:
            if(not tenant_name):
                tenant_name = ""
            self.project_query(tenant_name + "/" + name)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                project_already_exists = False
            else:
                raise e

        if(project_already_exists):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "Project with name: " + name +
                           " already exists")

        body = commoncoprhdapi.json_encode('name', name)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port,
                     "POST",
                    Project.URI_PROJECT_LIST.format(tenant_uri), body)
        o = commoncoprhdapi.json_decode(s)
        return o

    def project_list(self, tenant_name):
        '''
        Makes REST API call and retrieves projects based on tenant UUID
        Parameters: None
        Returns:
            List of project UUIDs in JSON response payload
        '''
        from tenant import Tenant
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        try:
            tenant_uri = tenant_obj.tenant_query(tenant_name)
        except SOSError as e:
            raise e
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                    Project.URI_PROJECT_LIST.format(tenant_uri), None)
        o = commoncoprhdapi.json_decode(s)

        if("project" in o):
            return commoncoprhdapi.get_list(o, 'project')
        return []

    def project_show_by_uri(self, uri, xml=False):
        '''
        Makes REST API call and retrieves project derails based on UUID
        Parameters:
            uri: UUID of project
        Returns:
            Project details in JSON response payload
        '''
        if(xml):
            (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port,
                            "GET", Project.URI_PROJECT.format(uri),
                             None, None, xml)
            return s

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port,
                    "GET", Project.URI_PROJECT.format(uri), None)
        o = commoncoprhdapi.json_decode(s)
        inactive = commoncoprhdapi.get_node_value(o, 'inactive')
        if(inactive == True):
            return None

        return o

    def project_show(self, name, xml=False):
        '''
        Retrieves project derails based on project name
        Parameters:
            name: name of the project
        Returns:
            Project details in JSON response payload
        '''
        project_uri = self.project_query(name)
        project_detail = self.project_show_by_uri(project_uri, xml)
        return project_detail
    
    
    #Routine for project resource 
    def project_resource_show(self, fullname ,xml=False):
        project_uri = self.project_query(fullname)
        
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                        Project.URI_PROJECT_RESOURCES.format(project_uri), None)
        o = commoncoprhdapi.json_decode(s)
     
        if xml:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "GET",
                Project.URI_PROJECT_RESOURCES.format(project_uri),
                None, None, xml)
            return s
        else :
            return o
    
    
    
    def project_query(self, name):
        '''
        Retrieves UUID of project based on its name
        Parameters:
            name: name of project
        Returns: UUID of project
        Throws:
            SOSError - when project name is not found
        '''
        if (commoncoprhdapi.is_uri(name)):
            return name
        (tenant_name, project_name) = commoncoprhdapi.get_parent_child_from_xpath(name)

        from tenant import Tenant
        tenant_obj = Tenant(self.__ipAddr, self.__port)

        try:
            tenant_uri = tenant_obj.tenant_query(tenant_name)
            projects = self.project_list(tenant_uri)
            if(projects and len(projects) > 0):
                for project in projects:
                    if (project):
                        project_detail = self.project_show_by_uri(
                                                project['id'])
                        if(project_detail and
                           project_detail['name'] == project_name):
                            return project_detail['id']
            raise SOSError(SOSError.NOT_FOUND_ERR,
                            'Project: ' + project_name + ' not found')
        except SOSError as e:
            raise e

    def project_delete_by_uri(self, uri):
        '''
        Deletes a project based on project UUID
        Parameters:
            uri: UUID of project
        '''
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port,
                    "POST", Project.URI_DEACTIVATE.format(uri), None)
        return

    def project_delete(self, name):
        '''
        Deletes a project based on project name
        Parameters:
            name: name of project
        '''
        project_uri = self.project_query(name)
        return self.project_delete_by_uri(project_uri)

    def update(self, project_name, new_name, new_owner,
                quota_enable, quota_capacity):
        '''
        Makes REST API call and updates project name and owner
        Parameters:
            project_name: name of project
            new_name: new name of the project

        Returns:
            List of project resources in response payload
        '''
        project_uri = self.project_query(project_name)

        request = dict()
        if(new_name and len(new_name) > 0):
            request["name"] = new_name
        if(new_owner and len(new_owner) > 0):
            request["owner"] = new_owner

        body = json.dumps(request)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                    Project.URI_PROJECT.format(project_uri), body)

        # update quota
        if(quota_enable is not None or quota_capacity is not None):
            from quota import Quota
            quota_obj = Quota(self.__ipAddr, self.__port)
            quota_obj.update(quota_enable, quota_capacity,
                              "project", project_uri)

    def tag(self, project_name, addtags, removetags):
        '''
        Makes REST API call and tags project
        Parameters:
            project_name: name of project
            addtags : tags to be added
            removetags : tags to be removed

        Returns:
            response of the tag operation
        '''
        project_uri = self.project_query(project_name)

        return (
            tag.tag_resource(self.__ipAddr, self.__port,
                              Project.URI_TAG_PROJECT,
                               project_uri, addtags, removetags)
        )

    def get_acl(self, project_name):

        project_uri = self.project_query(project_name)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                        Project.URI_PROJECT_ACL.format(project_uri), None)
        o = commoncoprhdapi.json_decode(s)
        return o

    def full_update_acl(self, project_name):
        project_uri = self.project_query(project_name)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                        Project.URI_PROJECT_ACL.format(project_uri), None)
        o = commoncoprhdapi.json_decode(s)
        return o

    def add_remove_acl(self, project_name, operation,
                        privilege, subject_id, group):
        project_uri = self.project_query(project_name)

        aclChanges = dict()
        aclChanges['privilege'] = [privilege]

        if(subject_id):
            aclChanges['subject_id'] = subject_id

        if(group):
            aclChanges['group'] = group

        if("add" == operation):
            request = {'add': [aclChanges]}
        else:
            request = {'remove': [aclChanges]}
        body = json.dumps(request)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                        Project.URI_PROJECT_ACL.format(project_uri), body)



def project_create(args):
    obj = Project(args.ip, args.port)
    try:
        obj.project_create(args.name, args.tenantname)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                            SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code,
                           "Project create failed: " + e.err_text)
        else:
            raise e


def project_delete(args):
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        obj.project_delete(args.tenant + "/" + args.name)

    except SOSError as e:
        if (e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Project delete failed: " + e.err_text)
        else:
            raise e

def project_show(args):
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.project_show(args.tenant + "/" + args.name, args.xml)
        if(res):
            if (args.xml == True):
                return commoncoprhdapi.format_xml(res)
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e
    

def project_resource_show(args):
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.project_resource_show(args.tenant + "/" + args.name, args.xml)
        if(res):
            if (args.xml == True):
                return commoncoprhdapi.format_xml(res)
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e

def project_list(args):
    obj = Project(args.ip, args.port)

    from quota import Quota
    quota_obj = Quota(args.ip, args.port)
    try:
        from manila.share.drivers.coprhd.helpers.commoncoprhdapi import TableGenerator
        projects = obj.project_list(args.tenantname)
        records = []
        for project in projects:
            project_uri = project['id']
            proj_detail = obj.project_show_by_uri(project_uri)
            if(proj_detail):
                # append quota attributes
                quota_obj.append_quota_attributes("project",
                                                   project_uri, proj_detail)
                if("tenant" in proj_detail and
                    "name" in proj_detail["tenant"]):
                    del proj_detail["tenant"]["name"]
                records.append(proj_detail)

        if(len(records) > 0):
            if(args.verbose == True):
                return commoncoprhdapi.format_json_object(records)

            elif(args.largetable == True):
                TableGenerator(records, ['name', 'owner',
                                          'quota_current_capacity',
                                          'quota_gb', 'tags']).printTable()
            else:
                TableGenerator(records, ['name']).printTable()

        else:
            return

    except SOSError as e:
        raise e


# update project command parser


def update_project(args):

    if(args.newname is None and args.newowner is None and
                             args.quota_enable is None and
                             args.quota_capacity is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
            "viprcli project update: error: at least one of " +
            "the arguments -np/-newname -o/-newowner  \
            -qe/-quota_enable -qc/-quota_capacity is required")
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        obj.update(args.tenant + "/" + args.name, args.newname,
                    args.newowner,
                     args.quota_enable,
                      args.quota_capacity)
    except SOSError as e:
        raise e


def get_project_acl(args):
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        res = obj.get_acl(args.tenant + "/" + args.name)
        if(res):
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e


def update_project_acl(args):
    obj = Project(args.ip, args.port)
    try:
        if(args.subjectid is None and args.group is None):
            raise SOSError(SOSError.CMD_LINE_ERR,
                sys.argv[0] + " " + sys.argv[1] + \
                " " + sys.argv[2] + \
                ": error:" + "At least one of the arguments :" \
                "-subjectid -group "
                " should be provided to update ACL assignments")
        if(not args.tenant):
            args.tenant = ""
        res = obj.add_remove_acl(args.tenant + "/" + args.name, args.operation,
                                 args.privilege, args.subjectid, args.group)
        if(res):
            return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e


def tag_project(args):

    if(args.add is None and args.remove is None):
        raise SOSError(SOSError.CMD_LINE_ERR,
                        "viprcli project tag: error: at least one of " +
                       "the arguments -add -remove is required")
    obj = Project(args.ip, args.port)
    try:
        if(not args.tenant):
            args.tenant = ""
        obj.tag(args.tenant + "/" + args.name,
                args.add, args.remove)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise("project", "tag", e.err_text,
                                         e.err_code)