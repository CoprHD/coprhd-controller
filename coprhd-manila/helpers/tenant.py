#!/usr/bin/python
# Copyright (c) 2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

from manila.share.drivers.coprhd.helpers import common as commoncoprhdapi
import json
from manila.share.drivers.coprhd.helpers.common import SOSError
import quota


class Tenant(object):

    '''
    The class definition for operations on 'Project'.
    '''

    URI_SERVICES_BASE = ''
    URI_TENANT = URI_SERVICES_BASE + '/tenant'
    URI_TENANTS = URI_SERVICES_BASE + '/tenants/{0}'
    URI_TENANTS_SUBTENANT = URI_TENANTS + '/subtenants'
    URI_TENANT_CONTENT = URI_TENANT
    URI_TENANT_ROLES = URI_TENANTS + '/role-assignments'
    URI_SUBTENANT = URI_TENANT + '/subtenants'
    URI_SUBTENANT_INFO = URI_SUBTENANT + '/{0}'
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_TENANT_HOSTS = URI_TENANTS + '/hosts'
    URI_TENANT_CLUSTERS = URI_TENANTS + '/clusters'
    URI_TENANT_VCENTERS = URI_TENANTS + '/vcenters'

    URI_NAMESPACE_COMMON = URI_SERVICES_BASE + '/object/namespaces'
    URI_NAMESPACE_BASE = URI_NAMESPACE_COMMON + '/namespace'
    URI_NAMESPACE_INSTANCE = URI_NAMESPACE_BASE + '/{0}'
    URI_NAMESPACE_TENANT_BASE = URI_NAMESPACE_COMMON + '/tenant'
    URI_NAMESPACE_TENANT_INSTANCE = URI_NAMESPACE_TENANT_BASE + '/{0}'
    
    URI_LIST_NAMESPACES = '/vdc/object-namespaces'
    URI_NAMESPACE_SHOW = '/vdc/object-namespaces/{0}'
    
    #New APIs for listing namespaces associated with a storagesystem
     
    URI_LIST_SS = "/vdc/storage-systems/{0}/object-namespaces"
    URI_LIST_SS_NAMESPACE = "/vdc/storage-systems/{0}/object-namespaces/{1}"

    PROVIDER_TENANT = "Provider Tenant"
    TENANT_ROLES = ['TENANT_ADMIN', 'PROJECT_ADMIN', 'TENANT_APPROVER']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def tenant_assign_role(self, tenant_name, roles, subject_id, group):
        '''
        Makes a REST API call to assign admin role
         '''
        tenant_uri = self.get_tenant_by_name(tenant_name)

        parms = {
            'role_assignments': [{
                'role': roles,
                'subject_id': subject_id,
                'group': group
            }]
        }
        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             Tenant.URI_TENANT_ROLES.format(
                                                 tenant_uri),
                                             body)

    def tenant_update_role(self, tenant_name, role, subject_id, group):
        '''
        Makes a REST API call to assign admin role
         '''
        tenant_uri = self.get_tenant_by_name(tenant_name)

        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group

        parms = {
            "add": [{"role": [role], objecttype: objectname}]
        }

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             Tenant.URI_TENANT_ROLES.format(
                                                 tenant_uri),
                                             body)

    def tenant_delete_role(self, tenant_name, role, subject_id, group):
        '''
        Makes a REST API call to assign admin role
         '''
        tenant_uri = self.get_tenant_by_name(tenant_name)

        if(subject_id):
            objecttype = 'subject_id'
            objectname = subject_id
        else:
            objecttype = 'group'
            objectname = group

        parms = {
            "remove": [{"role": [role], objecttype: objectname}]
        }

        body = json.dumps(parms)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "PUT",
                                             Tenant.URI_TENANT_ROLES.format(
                                                 tenant_uri),
                                             body)

    def tenant_get_namespace(self, tenant_name):
        '''
        Makes a REST API call to assign admin role
         '''
        tenant_uri = self.get_tenant_by_name(tenant_name)

        try:
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, commoncoprhdapi.OBJCTRL_PORT, "GET",
                Tenant.URI_NAMESPACE_TENANT_INSTANCE.format(tenant_uri),
                None)
            o = commoncoprhdapi.json_decode(s)

            if (not o):
                return {}
            return o
        except SOSError as e:
            if(tenant_name is None or len(tenant_name) == 0):
                tenant_name = Tenant.PROVIDER_TENANT
            if('HTTP code: 404' in e.err_text):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                       "No name space under tenant " + tenant_name )
            else:
                raise e


    def namespace_query(self, label):
        '''
        Returns the UID of the tenant specified by the hierarchial name
        (ex tenant`1/tenant2/tenant3)
        '''

        namespaces = self.list_namespaces()

        for namespace in namespaces:
            if (namespace == label):
                return label

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Namespace " + label + ": not found")
        
        
    def show_namespace (self , namespace):
        
        uri_namespace = self.get_nsid_from_name(namespace)
        
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, commoncoprhdapi.OBJCTRL_PORT, "GET",
            Tenant.URI_NAMESPACE_SHOW.format(uri_namespace),
            None)

        o = commoncoprhdapi.json_decode(s)

        if (not o):
            return {}

        return o
        
        

    def show_by_uri_namespace(self, namespace_id):
        '''
        Makes a REST API call to assign admin role
         '''
        
        

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, commoncoprhdapi.OBJCTRL_PORT, "GET",
            Tenant.URI_NAMESPACE_SHOW.format(namespace_id),
            None)

        o = commoncoprhdapi.json_decode(s)

        if (not o):
            return {}

        return o
    
    def get_nsid_from_name(self, namespace):
        
        '''
        Get the Namespace id from the namespacename
        '''
        all_obj = self.list_namespaces()
        new = all_obj['object_namespace']
        if(len(new) > 0):
           for ns in all_obj['object_namespace']:
                if(namespace in ns['name']):
                    return ns['id']
                
        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "namespace :" + namespace + ": not found")

        
        

    
    def tenant_delete_namespace(self, namespace):
        '''
        Makes a REST API call to assign admin role
         '''

        uri = self.namespace_query(namespace)

        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, commoncoprhdapi.OBJCTRL_PORT, "POST",
            Tenant.URI_RESOURCE_DEACTIVATE.format(
                Tenant.URI_NAMESPACE_INSTANCE.format(namespace)),
            None)
        


    def list_namespaces(self):
        '''
        Makes a REST API call to assign admin role
         '''

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr,
                                             commoncoprhdapi.OBJCTRL_PORT, "GET",
                                             Tenant.URI_LIST_NAMESPACES,
                                             None)

        o = commoncoprhdapi.json_decode(s)
        return o

        

    def tenant_create_namespace(self, tenant_name, namespace, project, cos):
        '''
        Makes a REST API call to assign admin role
         '''
        try:
            ret = self.namespace_query(namespace)
            if(ret):
                raise SOSError(
                    SOSError.ENTRY_ALREADY_EXISTS_ERR,
                    "Namespace create failed: " +
                    "namespace with same name already exists")

        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                tenant_uri = self.get_tenant_by_name(tenant_name)

                cos_uri = None
                project_uri = None

                #if(cos):
                    #from objectvpool import ObjectVpool
                    #obj = ObjectVpool(self.__ipAddr, self.__port)
                    #cos_uri = obj.objectvpool_query(cos)

                if(project):
                    from manila.share.drivers.coprhd.helpers.project import Project
                    obj = Project(self.__ipAddr, self.__port)

                    qualifiedname = project
                    if(tenant_name):
                        qualifiedname = tenant_name + "/" + project
                    project_uri = obj.project_query(qualifiedname)

                parms = {
                    'namespace': namespace,
                    'tenant': tenant_uri
                }

                if (project_uri is not None):
                    parms['default_object_project'] = project_uri

                if (cos_uri is not None):
                    parms['default_data_services_vpool'] = cos_uri

                body = json.dumps(parms)

                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr,
                    commoncoprhdapi.OBJCTRL_PORT, "POST", self.URI_NAMESPACE_BASE,
                    body, None)

            else:
                raise e

    def tenant_get_role(self, tenant_name, subject_id, group, xml=False):
        '''
        Makes a REST API call to assign admin role
         '''
        tenant_uri = self.get_tenant_by_name(tenant_name)

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANT_ROLES.format(
                                                 tenant_uri),
                                             None, None, False)

        o = commoncoprhdapi.json_decode(s)
        if('inactive' in o):
            if(o['inactive']):
                return None

        if(not xml):
            return o

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANT_ROLES.format(
                                                 tenant_uri),
                                             None, None, xml)

        return s

    def get_tenant_by_name(self, tenant):
        uri = None
        if (not tenant):
            uri = self.tenant_getid()
        else:
            if not commoncoprhdapi.is_uri(tenant):
                uri = self.tenant_query(tenant)
            else:
                uri = tenant
            if (not uri):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               'Tenant ' + tenant + ': not found')
        return uri

    def tenant_query(self, label):
        '''
        Returns the UID of the tenant specified by the hierarchial name
        (ex tenant`1/tenant2/tenant3)
        '''

        if (commoncoprhdapi.is_uri(label)):
            return label

        id = self.tenant_getid()

        if not label:
            return id

        subtenants = self.tenant_list(id)
        subtenants.append(self.tenant_show(None))

        for tenant in subtenants:
            if (tenant['name'] == label):
                rslt = self.tenant_show_by_uri(tenant['id'])
                if(rslt):
                    return tenant['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Tenant " + label + ": not found")

    def tenant_list(self, uri=None):
        '''
        Returns all the tenants under a parent tenant
        Parameters:
            parent: The parent tenant name
        Returns:
                JSON payload of tenant list
        '''

        if (not uri):
            uri = self.tenant_getid()

        tenantdtls = self.tenant_show_by_uri(uri, False)

        if(tenantdtls and not ('parent_tenant' in tenantdtls
                               and ("id" in tenantdtls['parent_tenant']))):
            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port,
                "GET", self.URI_TENANTS_SUBTENANT.format(uri), None)

            o = commoncoprhdapi.json_decode(s)
            return o['subtenant']

        else:
            return []



    def tenant_show(self, label, xml=False):
        '''
        Returns the details of the tenant based on its name
        '''
        if label:
            id = self.tenant_query(label)
        else:
            id = self.tenant_getid()

        return self.tenant_show_by_uri(id, xml)

    def tenant_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        '''
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANTS.format(uri),
                                             None, None, xml)

        if(not xml):
            o = commoncoprhdapi.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o
    
    # Functions for Namespace Object Listing and Namespace object show 
    
    def list_object_namespaces(self, nsstsystem):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        
        '''
        nsstsystem_uri = None
        from manila.share.drivers.coprhd.helpers.storagesystem import StorageSystem
        obj = StorageSystem(self.__ipAddr, self.__port)
                 
        nsstsystem_uri = obj.query_by_name_and_type(nsstsystem, "ecs")
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_LIST_SS.format(nsstsystem_uri),
                                             None)

        o = commoncoprhdapi.json_decode(s)
        return o
    
    
    def show_object_namespaces(self, nsstsystem , namespace):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        
        '''
        nsstsystem_uri = None
        from manila.share.drivers.coprhd.helpers.storagesystem import StorageSystem
        obj = StorageSystem(self.__ipAddr, self.__port)
                 
        nsstsystem_uri = obj.query_by_name_and_type(nsstsystem, "ecs")
        uri_namespace = self.get_nsid_from_name(namespace)
       
        
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_LIST_SS_NAMESPACE.format(nsstsystem_uri ,uri_namespace),
                                             None)

        
        o = commoncoprhdapi.json_decode(s)
        return o

        

    def tenant_get_hosts(self, label, xml=False):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        '''
        if label:
            id = self.tenant_query(label)
        else:
            id = self.tenant_getid()

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANT_HOSTS.format(
                                                 id),
                                             None, None, xml)

        o = commoncoprhdapi.json_decode(s)

        from manila.share.drivers.coprhd.helpers.host import Host
        obj = Host(self.__ipAddr, self.__port)

        hostsdtls = obj.show(o['host'])

        return hostsdtls

    def tenant_get_clusters(self, label, xml=False):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        '''
        if label:
            id = self.tenant_query(label)
        else:
            id = self.tenant_getid()

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANT_CLUSTERS.format(
                                                 id),
                                             None, None, xml)

        o = commoncoprhdapi.json_decode(s)

        from manila.share.drivers.coprhd.helpers.cluster import Cluster
        obj = Cluster(self.__ipAddr, self.__port)

        dtlslst = obj.cluster_get_details_list(o['cluster'])

        return dtlslst

    def tenant_get_vcenters(self, label, xml=False):
        '''
        Makes a REST API call to retrieve details of a tenant based on its UUID
        '''
        if label:
            id = self.tenant_query(label)
        else:
            id = self.tenant_getid()

        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Tenant.URI_TENANT_VCENTERS.format(
                                                 id),
                                             None, None, xml)

        o = commoncoprhdapi.json_decode(s)

        from manila.share.drivers.coprhd.helpers.vcenter import VCenter
        obj = VCenter(self.__ipAddr, self.__port)

        dtlslst = obj.vcenter_get_details_list(o['vcenter'])

        return dtlslst

    def tenant_quota_update(self, tenant, quota_enable, quota_gb):

        if tenant:
            tenant_id = self.tenant_query(tenant)
        else:
            tenant_id = self.tenant_getid()

        # update quota
        if(quota_enable is not None or quota_gb is not None):
            from manila.share.drivers.coprhd.helpers.quota import Quota
            quota_obj = Quota(self.__ipAddr, self.__port)
            quota_obj.update(quota_enable, quota_gb, "tenant", tenant_id)

    def tenant_getid(self):
        (s, h) = commoncoprhdapi.service_json_request(self.__ipAddr, self.__port,
                                             "GET", Tenant.URI_TENANT, None)

        o = commoncoprhdapi.json_decode(s)
        return o['id']

    def tenant_create(self, name, key, value, domain , namespace):
        '''
        creates a tenant
        parameters:
            label:  label of the tenant
            parent: parent tenant of the tenant
        Returns:
            JSON payload response
        '''

        try:
            check = self.tenant_show(name)
        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                parms = dict()
                parms = {
                    'name': name
                }
                parms ['namespace'] = namespace
                
                
                keyval = dict()

                if(key):
                    keyval['key'] = key
                if(value):
                    vallst = []
                    vallst.append(value)
                    keyval['value'] = vallst

                usermappinglst = []
                attrlst = []

                if(('key' in keyval) or ('value' in keyval)):
                    attrlst.append(keyval)

                usermapping = dict()
                usermapping['attributes'] = attrlst
                usermapping['domain'] = domain
                usermappinglst.append(usermapping)

                parms['user_mappings'] = usermappinglst

                body = json.dumps(parms)
                uri = self.tenant_getid()

                (s, h) = commoncoprhdapi.service_json_request(
                    self.__ipAddr, self.__port,
                    "POST", self.URI_TENANTS_SUBTENANT.format(uri), body)

                o = commoncoprhdapi.json_decode(s)
                return o
            else:
                raise e

        if(check):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "Tenant create failed: subtenant with same" +
                           "name already exists")
            
            
            
            
    # ROUTINE FOR ADD NAMESPACE
    def add_namespace(self, name, namespace, description):
        '''
        creates a tenant
        parameters:
            label:  label of the tenant
            parent: parent tenant of the tenant
        Returns:
            JSON payload response
        '''

        try:
            tenant = self.tenant_show(name)

            

            parms = dict()
            parms['name'] = name
            parms['description'] = description
            parms['namespace'] = namespace
            
            body = json.dumps(parms)
           

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                self.URI_TENANTS.format(tenant['id']), body)

        except SOSError as e:
            raise e

    def tenant_add_attribute(self, label, key, value, domain):
        '''
        creates a tenant
        parameters:
            label:  label of the tenant
            parent: parent tenant of the tenant
        Returns:
            JSON payload response
        '''

        try:
            tenant = self.tenant_show(label)

            user_mappings = tenant['user_mappings']

            for user_mapping in user_mappings:
                if(domain == user_mapping['domain']):
                    for attribute in user_mapping['attributes']:
                        if (key == attribute['key'] and
                           value in attribute['value']):
                            if(label):
                                tenname = label
                            else:
                                tenname = self.PROVIDER_TENANT

                            raise SOSError(
                                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                                "Tenant " + tenname +
                                ": already has the key=" + key +
                                " and value=" + value +
                                " combination")

            parms = dict()

            keyval = dict()

            if(key):
                keyval['key'] = key
            if(value):
                vallst = []
                vallst.append(value)
                keyval['value'] = vallst

            usermappinglst = []
            attrlst = []

            if(('key' in keyval) or ('value' in keyval)):
                attrlst.append(keyval)

            usermapping = dict()
            usermapping['attributes'] = attrlst
            usermapping['domain'] = domain
            usermappinglst.append(usermapping)

            adddict = dict()
            adddict['add'] = usermappinglst

            parms['user_mapping_changes'] = adddict

            body = json.dumps(parms)

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                self.URI_TENANTS.format(tenant['id']), body)

        except SOSError as e:
            raise e

    def tenant_remove_attribute(self, label, key, value, domain):
        '''
        creates a tenant
        parameters:
            label:  label of the tenant
            parent: parent tenant of the tenant
        Returns:
            JSON payload response
        '''

        try:
            tenant = self.tenant_show(label)

            user_mappings = tenant['user_mappings']

            parms = dict()
            parms = {
                'user_mapping_changes': {
                    'remove': [{
                        'domain': domain,
                        'attributes': [{
                            'key': key,
                            'value': [value]
                        }],
                    }]}
            }

            body = json.dumps(parms)

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                self.URI_TENANTS.format(tenant['id']), body)

        except SOSError as e:
            raise e

    def tenant_add_group(self, label, ingroup, domain):
        '''
        creates a tenant
        parameters:
            label:  label of the tenant
            parent: parent tenant of the tenant
        Returns:
            JSON payload response
        '''

        try:
            tenant = self.tenant_show(label)

            user_mappings = tenant['user_mappings']

            for user_mapping in user_mappings:
                if(domain == user_mapping['domain']):
                    for group in user_mapping['groups']:
                        if (group == ingroup):
                            if(label):
                                tenname = label
                            else:
                                tenname = self.PROVIDER_TENANT

                            raise SOSError(
                                SOSError.ENTRY_ALREADY_EXISTS_ERR,
                                "Tenant " + tenname +
                                ": already contains group mapping " +
                                group + " already")

            parms = dict()
            parms = {
                'user_mapping_changes': {
                    'add': [{
                        'domain': domain,
                        'groups': [ingroup],
                    }]}
            }

            body = json.dumps(parms)

            (s, h) = commoncoprhdapi.service_json_request(
                self.__ipAddr, self.__port, "PUT",
                self.URI_TENANTS.format(tenant['id']), body)

        except SOSError as e:
            raise e

    def tenant_delete_by_uri(self, uri):
        '''
        Makes a REST API call to delete a tenant by its UUID
        '''
        (s, h) = commoncoprhdapi.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            self.URI_RESOURCE_DEACTIVATE.format(self.URI_TENANTS.format(uri)),
            None)
        return

    def tenant_delete(self, label):
        '''
        deletes a tenant by name
        '''
        uri = self.tenant_query(label)
        return self.tenant_delete_by_uri(uri)

# TENANT Create routines





def tenant_create(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_create(args.name, args.key, args.value, args.domain , args.namespace)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Tenant " +
                           args.name + ": Create failed\n" + e.err_text)
        else:
            raise e
        





def add_namespace_create(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.add_namespace(args.name , args.namespace ,args.description)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Add Namespace " +
                           args.name + ": failed\n" + e.err_text)
        else:
            raise e




# TENANT add attribute routines



def tenant_add_attribute(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_add_attribute(
            args.name,
            args.key,
            args.value,
            args.domain)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            if(args.name):
                tenname = args.name
            else:
                tenname = Tenant.PROVIDER_TENANT

            raise SOSError(e.err_code, "Tenant " +
                           tenname + ": Add attribute failed\n" + e.err_text)
        else:
            raise e





def tenant_remove_attribute(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_remove_attribute(
            args.name,
            args.key,
            args.value,
            args.domain)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):
            raise SOSError(e.err_code, "Tenant " +
                           args.name + ": Remove attribute failed\n" +
                           e.err_text)
        else:
            raise e


def tenant_add_group(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_add_group(args.name, args.group, args.domain)
    except SOSError as e:
        if (e.err_code in [SOSError.NOT_FOUND_ERR,
                           SOSError.ENTRY_ALREADY_EXISTS_ERR]):

            if(args.name):
                tenname = args.name
            else:
                tenname = Tenant.PROVIDER_TENANT
            raise SOSError(e.err_code, "Tenant " +
                           tenname + ": Add group failed\n" + e.err_text)
        else:
            raise e
# TENANT Delete routines


def tenant_delete(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_delete(args.name)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Tenant delete failed: " + e.err_text)
        else:
            raise e

# TENANT Show routines



def tenant_show(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_show(args.name, args.xml)

        if(args.xml):
            return commoncoprhdapi.format_xml(str(res))

        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Tenant show failed: " + e.err_text)
        else:
            raise e


# TENANT Query routines

def tenant_query(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.tenant_query(args.name)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR, "Tenant query failed: " +
                           e.err_text)
        else:
            raise e

# TENANT List routines


def tenant_list(args):
    obj = Tenant(args.ip, args.port)

    from manila.share.drivers.coprhd.helpers.quota import Quota
    quota_obj = Quota(args.ip, args.port)

    try:
        uris = obj.tenant_list()

        output = []

        myid = obj.tenant_getid()
        tenant_details = obj.tenant_show(myid)
        # append quota attributes
        quota_obj.append_quota_attributes("tenant", myid, tenant_details)
        output.append(tenant_details)

        for uri in uris:
            uri_details = obj.tenant_show(uri['id'])
            if(uri_details):
                # append quota attributes
                quota_obj.append_quota_attributes(
                    "tenant",
                    uri['id'],
                    uri_details)
                output.append(uri_details)
        if(len(output) > 0):
            if(args.verbose):
                return commoncoprhdapi.format_json_object(output)
            elif(args.long):
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(
                    output,
                    ['module/name',
                     'quota_current_capacity',
                     'quota_gb',
                     'description']).printTable()
            else:
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(output, ['module/name']).printTable()
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Tenant list failed: " + e.err_text)
        else:
            raise e

# TENANT Role addition


def assign_role(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_assign_role(
            args.name,
            args.roles,
            args.subjectid,
            args.group)
    except SOSError as e:
        raise e


def update_role(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_update_role(
            args.name,
            args.roles,
            args.subjectid,
            args.group)
    except SOSError as e:
        raise e


def delete_role(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_delete_role(
            args.name,
            args.roles,
            args.subjectid,
            args.group)
    except SOSError as e:
        raise e



def get_role(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_get_role(args.name, None, None, args.xml)
        if(args.xml):
            return commoncoprhdapi.format_xml(str(res))

        return commoncoprhdapi.format_json_object(res)

    except SOSError as e:
        raise e



def get_tenant_hosts(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_get_hosts(args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return commoncoprhdapi.format_json_object(res)
            elif(args.long):
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(
                    res,
                    ['name',
                     'type',
                     'job_discovery_status',
                     'job_metering_status']).printTable()
            else:
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        raise e



def get_tenant_clusters(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_get_clusters(args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return commoncoprhdapi.format_json_object(res)
            elif(args.long):
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(res, ['name']).printTable()
            else:
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        raise e


def get_tenant_vcenters(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_get_vcenters(args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return commoncoprhdapi.format_json_object(res)
            elif(args.long):
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(
                    res,
                    ['name',
                     'ip_address',
                     'job_discovery_status',
                     'job_metering_status']).printTable()
            else:
                from manila.share.drivers.coprhd.helpers.common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        raise e



def tenant_create_namespace(args):
    obj = Tenant(args.ip, args.port)

    try:
        obj.tenant_create_namespace(
            args.tenant,
            args.namespace,
            args.project,
            args.objectvpool)
    except SOSError as e:
        raise e



def tenant_get_namespace(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_get_namespace(args.tenant)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e


def show_namespace(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.show_namespace(args.namespace)
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        raise e


def delete_namespace(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.tenant_delete_namespace(args.namespace)
        return res
    except SOSError as e:
        raise e



def list_namespaces(args):
    obj = Tenant(args.ip, args.port)

    try:
        res = obj.list_namespaces()
        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        commoncoprhdapi.format_err_msg_and_raise(
            "namespaces",
            "list",
            e.err_text,
            e.err_code)
        

    except SOSError as e:
        raise e
    
    
#New parser for list object namespaces 


def list_object_namespaces(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.list_object_namespaces(args.nsstsystem)

        

        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Object Namespace list failed: " + e.err_text)
        else:
            raise e
        

def show_object_namespaces(args):
    obj = Tenant(args.ip, args.port)
    try:
        res = obj.show_object_namespaces(args.nsstsystem, args.namespace)

        

        return commoncoprhdapi.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Object Namespace show failed: " + e.err_text)
        else:
            raise e


#
# Tenant Main parser routine
#