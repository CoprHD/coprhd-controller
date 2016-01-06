#!/usr/bin/python
# Copyright (c)2012 EMC Corporation
# All Rights Reserved

# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import json
import common
import sys
import getpass


from common import SOSError


class VCenter(object):

    '''
    The class definition for operations on 'VCenter'.
    '''

    # Commonly used URIs for the 'vcenters' module
    URI_SERVICES_BASE = ''
    URI_TENANTS = URI_SERVICES_BASE + '/tenants/{0}'
    URI_TENANT = URI_SERVICES_BASE + '/tenant'
    URI_TENANTS_VCENTERS = URI_TENANTS + '/vcenters'
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_VCENTERS = URI_SERVICES_BASE + '/compute/vcenters'
    URI_VCENTER = URI_SERVICES_BASE + '/compute/vcenters/{0}'
    URI_VCENTER_DATACENTERS = URI_VCENTER + '/vcenter-data-centers?tenant={1}'
    URI_VCENTER_HOSTS = URI_VCENTER + '/hosts'
    URI_VCENTER_CLUSTERS = URI_VCENTER + '/clusters'
    URI_VCENTER_DISCOVER = URI_VCENTER + '/discover'
    URI_VCENTER_ACL = URI_VCENTERS + '/{0}/acl'
    URI_VCENTERS_WITH_TENANT_PARAM = URI_VCENTERS + '?tenant={0}'
    URI_VCENTERS_WITH_DISCOVER_PARAM = URI_VCENTERS + '?discover_vcenter={0}'
    URI_VCENTER_WITH_DISCOVER_PARAM = URI_VCENTER + '?discover_vcenter={1}'
    URI_WHO_AM_I = "/user/whoami";
    VCENTERS_FROM_ALL_TENANTS = "No-Filter";
    VCENTERS_WITH_NO_TENANTS = "Not-Assigned";
    USER_ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def get_tenant_uri_from_name(self, tenantname):
        if (tenantname is None or
            tenantname == "" or
            tenantname.lower() == VCenter.VCENTERS_FROM_ALL_TENANTS.lower() or
            tenantname.lower() == VCenter.VCENTERS_WITH_NO_TENANTS.lower()):
            tenanturi = tenantname
        else:
            from tenant import Tenant
            obj = Tenant(self.__ipAddr, self.__port)

            tenanturi = obj.tenant_query(tenantname)

        return tenanturi

    def vcenter_query(self, name, tenantname):
        '''
        Returns the UID of the vcenter specified by the name
        '''
        if (common.is_uri(name)):
            return name

        tenanturi = self.get_tenant_uri_from_name(tenantname)

        vcenters = self.vcenter_list(tenanturi)
        for vcenter in vcenters:
            if (vcenter['name'] == name):
                return vcenter['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "vcenter " + name + ": not found")

    def vcenter_list(self, tenant):
        '''
        Returns all the vcenters associated with a tenant
        Parameters:
        Returns:
                JSON payload of vcenter list
        '''
        uri = self.get_vcenter_uri_with_tenant_param(tenant)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            uri, None)

        o = common.json_decode(s)

        return o['vcenter']

    def get_vcenter_uri_with_tenant_param(self, tenant):
        uri = ""
        if (tenant is None or
            tenant == "" or
            tenant.lower() == VCenter.VCENTERS_FROM_ALL_TENANTS.lower()):
            uri = VCenter.URI_VCENTERS_WITH_TENANT_PARAM.format(VCenter.VCENTERS_FROM_ALL_TENANTS)
        elif tenant.lower() == VCenter.VCENTERS_WITH_NO_TENANTS.lower():
            uri = VCenter.URI_VCENTERS_WITH_TENANT_PARAM.format(tenant)
        else:
            from tenant import Tenant
            obj = Tenant(self.__ipAddr, self.__port)

            tenant_uri = obj.tenant_query(tenant)
            uri = VCenter.URI_VCENTERS_WITH_TENANT_PARAM.format(tenant_uri)
        return uri

    def vcenter_get_details_list(self, detailslst):
        rsltlst = []
        for iter in detailslst:
            tmp = self.vcenter_show(iter['id'], None)
            if(tmp):
                rsltlst.append(tmp)

        return rsltlst

    def vcenter_get_datacenters(self, label, tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details
        of a vcenter  based on its UUID
        '''

        uri = self.vcenter_query(label, tenantname)
        if (tenantname is None or
            tenantname == "") :
            tenantname = VCenter.VCENTERS_FROM_ALL_TENANTS
            
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VCenter.URI_VCENTER_DATACENTERS.format(uri, self.get_tenant_uri_from_name(tenantname)),
            None, None, xml)

        o = common.json_decode(s)

        from vcenterdatacenter import VcenterDatacenter
        obj = VcenterDatacenter(self.__ipAddr, self.__port)

        dtlslst = obj.vcenterdatacenter_get_details(o['vcenter_data_center'])

        return dtlslst

    def vcenter_get_clusters(self, label, tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details of all clusters
        associated with a vcenter
        '''

        uri = self.vcenter_query(label, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VCenter.URI_VCENTER_CLUSTERS.format(uri),
            None, None, xml)

        o = common.json_decode(s)

        from cluster import Cluster
        obj = Cluster(self.__ipAddr, self.__port)

        dtlslst = obj.cluster_get_details_list(o['cluster'])

        return dtlslst

    def vcenter_get_hosts(self, label, tenantname):
        '''
        Makes a REST API call to retrieve details of a vcenter
        based on its UUID
        '''

        uri = self.vcenter_query(label, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VCenter.URI_VCENTER_HOSTS.format(uri),
            None, None, False)

        from host import Host
        obj = Host(self.__ipAddr, self.__port)

        o = common.json_decode(s)
        hostsdtls = obj.show(o['host'])

        return hostsdtls

    def vcenter_show(self, label, tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details of a vcenter
        based on its UUID
        '''

        uri = self.vcenter_query(label, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VCenter.URI_VCENTER.format(uri),
            None, None, xml)

        if(not xml):
            o = common.json_decode(s)

            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o

    def vcenter_create(self, label, tenant, ipaddress, devport,
                       username, password, osversion, usessl, cascade_tenancy):
        '''
        creates a vcenter
        parameters:
            label:  label of the vcenter
        Returns:
            JSON payload response
        '''
        try:
            check = self.vcenter_show(label, tenant)
            if(not check):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "vcenter " + label + ": not found")

        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):

                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, "GET", VCenter.URI_WHO_AM_I, None)

                user_info = common.json_decode(s)

                sys_admin = False
                vdc_roles = []

                if user_info['vdc_roles']:
                    vdc_roles = user_info['vdc_roles']

                if VCenter.USER_ROLE_SYSTEM_ADMIN in vdc_roles:
                    sys_admin = True

                var = dict()
                params = dict()
                params = {'name': label,
                          'ip_address': ipaddress,
                          'os_version': osversion,
                          'port_number': devport,
                          'user_name': username,
                          'password': password,
                          'use_ssl': usessl
                          }

                if sys_admin:
                    params['cascade_tenancy'] = cascade_tenancy;
                    discover = 'false';
                    if tenant is None:
                        discover = 'true';

                    body = json.dumps(params)
                    (s, h) = common.service_json_request(
                        self.__ipAddr, self.__port, "POST", VCenter.URI_VCENTERS_WITH_DISCOVER_PARAM.format(discover), body)
                else:
                    body = json.dumps(params)

                    from tenant import Tenant
                    obj = Tenant(self.__ipAddr, self.__port)
                    tenant_uri = obj.tenant_query(tenant)

                    (s, h) = common.service_json_request(
                        self.__ipAddr, self.__port, "POST", VCenter.URI_TENANTS_VCENTERS.format(str(tenant_uri)), body)

                o = common.json_decode(s)
                if sys_admin and tenant is not None:
                    tenant_uri = None
                    if tenant is not None:
                        from tenant import Tenant
                        obj = Tenant(self.__ipAddr, self.__port)
                        tenant_uri = obj.tenant_query(tenant)
                        
                    vcenter_id = (o['resource'])['id']
                    acls_params = dict()
                    acls_params = {'add' : [{
                                            'tenant' : str(tenant_uri),
                                            'privilege' : ['USE']
                                            }]
                                   }
                    body = json.dumps(acls_params)
                    (s, h) = common.service_json_request(
                        self.__ipAddr, self.__port, "PUT", VCenter.URI_VCENTER_ACL.format(vcenter_id), body)
                    o = common.json_decode(s)

                return o

            else:
                raise e

        if(check):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "vcenter with name " + label + " already exists")

    def vcenter_delete(self, label, tenantname):
        '''
        Makes a REST API call to delete a vcenter by its UUID
        '''
        uri = self.vcenter_query(label, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_RESOURCE_DEACTIVATE.format
            (VCenter.URI_VCENTER.format(uri)),
            None)
        return str(s) + " ++ " + str(h)

    def vcenter_discover(self, label, tenantname):
        '''
        Makes a REST API call to delete a vcenter by its UUID
        '''
        uri = self.vcenter_query(label, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_VCENTER_DISCOVER.format(uri),
            None)

        o = common.json_decode(s)
        return o

    def list_tasks(self, tenant_name, vcenter_name=None, task_id=None):

        uri = self.vcenter_query(vcenter_name, tenant_name)

        if(vcenter_name):
            vcenter = self.vcenter_show(uri, True)
            if(vcenter != None):
                if(vcenter['name'] == vcenter_name):
                    if(not task_id):
                        return common.get_tasks_by_resourceuri(
                            "vcenter", vcenter["id"],
                            self.__ipAddr, self.__port)
                    else:
                        res = common.get_task_by_resourceuri_and_taskId(
                            "vcenter", vcenter["id"], task_id,
                            self.__ipAddr, self.__port)
                        if(res):
                            return res
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Vcenter with name: " +
                vcenter_name +
                " not found")

    def add_or_remove_vcenter_acls(self, vcenter_name, tenants, operation):
        vcenter_uri = self.vcenter_query(vcenter_name, VCenter.VCENTERS_FROM_ALL_TENANTS)

        tenants_array = []
        tenants_array = tenants.split(',')

        from tenant import Tenant
        obj = Tenant(self.__ipAddr, self.__port)

        acl_entries = []
        for tenant in tenants_array:
            acl_entry = dict()
            acl_entry['privilege'] = ['USE']
            acl_entry['tenant'] = str(obj.tenant_query(tenant))
            acl_entries.append(acl_entry)

        request = {operation: acl_entries}

        body = json.dumps(request)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "PUT",
                        VCenter.URI_VCENTER_ACL.format(vcenter_uri), body)

        o = common.json_decode(s)

        return o

    def get_vcenter_acls(self, vcenter_name):
        vcenter_uri = self.vcenter_query(vcenter_name, VCenter.VCENTERS_FROM_ALL_TENANTS)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                        VCenter.URI_VCENTER_ACL.format(vcenter_uri), None)

        o = common.json_decode(s)

        return o
    
    def vcenter_update(self, label, tenant, vcenter_ip, vcenter_port, osversion, usessl, username, password, cascade_tenancy):
        '''
        updates a vcenter
        parameters:
            label:  label of the vcenter
        Returns:
            JSON payload response
        '''
        
        uri = self.vcenter_query(label, tenant)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET", VCenter.URI_WHO_AM_I, None)
        
        user_info = common.json_decode(s)

        sys_admin = False
        vdc_roles = []

        if user_info['vdc_roles']:
            vdc_roles = user_info['vdc_roles']

        if VCenter.USER_ROLE_SYSTEM_ADMIN in vdc_roles:
            sys_admin = True
        
        params = dict()
        params = {'name': label,
                  'ip_address': vcenter_ip,
                  'os_version': osversion,
                  'port_number': vcenter_port,
                  'user_name': username,
                  'password': password,
                  'use_ssl': usessl
                  }
        discover = 'false';
        if tenant is None:
            discover = 'true';
        if sys_admin:
            params['cascade_tenancy'] = cascade_tenancy;
            body = json.dumps(params)
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "PUT", VCenter.URI_VCENTER_WITH_DISCOVER_PARAM.format(uri, discover), body)
        else:
            body = json.dumps(params)
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "PUT", VCenter.URI_VCENTER_WITH_DISCOVER_PARAM.format(uri, discover), body)
        o = common.json_decode(s)
        return o



# Create routines
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR vcenter Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a vcenter')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vcenter',
                                metavar='<vcentername>',
                                dest='name',
                                required=True)

    create_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    mandatory_args.add_argument('-vcenter_ip', '-vcip',
                                help='IP of Vcenter',
                                metavar='<vcenter_ip>',
                                dest='vcenter_ip',
                                required=True)

    mandatory_args.add_argument('-vcenter_port', '-vcpo',
                                help='Port of Vcenter',
                                metavar='<vcenter_port>',
                                dest='vcenter_port',
                                required=True)

    mandatory_args.add_argument('-user', '-u',
                                help='Name of user',
                                metavar='<user>',
                                dest='user',
                                required=True)

    create_parser.add_argument('-osversion', '-ov',
                               help='osversion',
                               dest='osversion',
                               metavar='<osversion>',
                               default=None)

    create_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')

    create_parser.add_argument('-cascade_tenancy', '-cascade',
                               dest='cascade_tenancy',
                               choices=['true', 'false'],
                               help='Specify true, to cascade the vCenter tenancy to all its Datacenters, Clusters and Hosts. Specify false otherwise')

    create_parser.set_defaults(func=vcenter_create)


def vcenter_create(args):    
    passwd = None
    if (args.user and len(args.user) > 0):
        passwd = common.get_password("vcenter")

    obj = VCenter(args.ip, args.port)

    try:
        validate_tenant = obj.get_tenant_uri_from_name(args.tenant)
        res = obj.vcenter_create(args.name, args.tenant, args.vcenter_ip,
                                 args.vcenter_port, args.user, passwd,
                                 args.osversion, args.usessl, args.cascade_tenancy)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "vcenter", e.err_text,
                                        e.err_code)


# vcenter Delete routines
def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR vcenter Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a vcenter')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    delete_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    delete_parser.set_defaults(func=vcenter_delete)


def vcenter_delete(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_delete(args.name, args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "vcenter",
                                        e.err_text, e.err_code)


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR vcenter Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a vcenter')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    show_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=vcenter_show)


def vcenter_show(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_show(args.name, args.tenant, args.xml)

        if(res is None):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "vcenter " + args.name + ": not found")

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "vcenter",
                                        e.err_text, e.err_code)


def get_hosts_parser(subcommand_parsers, common_parser):
    # get hosts command parser
    get_hosts_parser = subcommand_parsers.add_parser(
        'get-hosts',
        description='ViPR vcenter get hosts CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the hosts of a vcenter')

    mandatory_args = get_hosts_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    get_hosts_parser.add_argument('-tenant', '-tn',
                                  help='Name of Tenant',
                                  metavar='<tenant>',
                                  dest='tenant',
                                  default=None)

    get_hosts_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List vcenters with more details in tabular form',
        dest='long')

    get_hosts_parser.add_argument('-verbose', '-v',
                                  action='store_true',
                                  help='List vcenters with details',
                                  dest='verbose')

    get_hosts_parser.set_defaults(func=vcenter_get_hosts)


def vcenter_get_hosts(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_get_hosts(args.name, args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return common.format_json_object(res)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(res, ['name', 'type',
                               'job_discovery_status',
                                     'job_metering_status']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("get hosts", "vcenter",
                                        e.err_text, e.err_code)


def get_datacenters_parser(subcommand_parsers, common_parser):
    # show command parser
    get_datacenters_parser = subcommand_parsers.add_parser(
        'get-datacenters',
        description='ViPR vcenter get clusters CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the clusters of  a vcenter')

    mandatory_args = get_datacenters_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    get_datacenters_parser.add_argument('-tenant', '-tn',
                                        help='Name of Tenant',
                                        metavar='<tenant>',
                                        dest='tenant',
                                        default=None)

    get_datacenters_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List vcenters with more details in tabular form',
        dest='long')

    get_datacenters_parser.add_argument('-verbose', '-v',
                                        action='store_true',
                                        help='List vcenters with details',
                                        dest='verbose')

    get_datacenters_parser.set_defaults(func=vcenter_get_datacenters)


def vcenter_get_datacenters(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_get_datacenters(args.name, args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return common.format_json_object(res)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("get clusters", "vcenter",
                                        e.err_text, e.err_code)


# vcenter get clusters routines

def get_clusters_parser(subcommand_parsers, common_parser):
    # get clusters  command parser
    get_clusters_parser = subcommand_parsers.add_parser(
        'get-clusters',
        description='ViPR vcenter get clusters CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the clusters of  a vcenter')

    mandatory_args = get_clusters_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    get_clusters_parser.add_argument('-tenant', '-tn',
                                     help='Name of Tenant',
                                     metavar='<tenant>',
                                     dest='tenant',
                                     default=None)
    get_clusters_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List vcenters with more details in tabular form',
        dest='long')

    get_clusters_parser.add_argument('-verbose', '-v',
                                     action='store_true',
                                     help='List vcenters with details',
                                     dest='verbose')

    get_clusters_parser.set_defaults(func=vcenter_get_clusters)


def vcenter_get_clusters(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_get_clusters(args.name, args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return common.format_json_object(res)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("get clusters", "vcenter",
                                        e.err_text, e.err_code)


# vcenter query routines

def query_parser(subcommand_parsers, common_parser):
    # query command parser
    query_parser = subcommand_parsers.add_parser(
        'query',
        description='ViPR vcenter Query CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Query a vcenter')

    mandatory_args = query_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    query_parser.add_argument('-tenant', '-tn',
                              help='Name of Tenant',
                              metavar='<tenant>',
                              dest='tenant',
                              default=None)

    query_parser.set_defaults(func=vcenter_query)


def vcenter_query(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_query(args.name, args.tenant)
        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("query", "vcenter",
                                        e.err_text, e.err_code)

# vcenter List routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR vcenter List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List of vcenters')

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List vcenters with details',
                             dest='verbose')

    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List vcenters with more details in tabular form',
        dest='long')

    list_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    list_parser.set_defaults(func=vcenter_list)


def vcenter_list(args):
    obj = VCenter(args.ip, args.port)
    try:
        uris = obj.vcenter_list(args.tenant)
        output = []
        outlst = []

        for uri in uris:
            temp = obj.vcenter_show(uri['id'], uri)
            if(temp):
                output.append(temp)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(
                    output, ['name', 'ip_address', 'job_discovery_status',
                             'job_metering_status']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "vcenter",
                                        e.err_text, e.err_code)


def discover_parser(subcommand_parsers, common_parser):
    # show command parser
    discover_parser = subcommand_parsers.add_parser(
        'discover',
        description='ViPR vcenter Discover CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover a vcenter')

    mandatory_args = discover_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    discover_parser.add_argument('-tenant', '-tn',
                                 help='Name of Tenant',
                                 metavar='<tenant>',
                                 dest='tenant',
                                 default=None)

    discover_parser.add_argument('-xml',
                                 dest='xml',
                                 action='store_true',
                                 help='XML response')

    discover_parser.set_defaults(func=vcenter_discover)


def vcenter_discover(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_discover(args.name, args.tenant)

    except SOSError as e:
        common.format_err_msg_and_raise("discover", "vcenter",
                                        e.err_text, e.err_code)


def task_parser(subcommand_parsers, common_parser):
    # show command parser
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR vcenter tasks  CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='check tasks of a vcenter')

    mandatory_args = task_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenter',
                                dest='name',
                                metavar='<vcentername>',
                                required=True)

    task_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')

    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')

    task_parser.set_defaults(func=vcenter_list_tasks)


def vcenter_list_tasks(args):
    obj = VCenter(args.ip, args.port)

    try:
        if(not args.tenant):
            args.tenant = ""
        if(args.id):
            res = obj.list_tasks(args.tenant, args.name, args.id)
            if(res):
                return common.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant, args.name)
            if(res and len(res) > 0):
                if(args.verbose):
                    return common.format_json_object(res)
                else:
                    from common import TableGenerator
                    TableGenerator(res, ["module/id", "name",
                                         "state"]).printTable()
        else:
            res = obj.list_tasks(args.tenant)
            if(res and len(res) > 0):
                if(not args.verbose):
                    from common import TableGenerator
                    TableGenerator(res, ["module/id", "name",
                                         "state"]).printTable()
                else:
                    return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise("get tasks list", "vcenter",
                                        e.err_text, e.err_code)

def add_acls_parser(subcommand_parsers, common_parser):
    add_acls_parser = subcommand_parsers.add_parser('add-acls',
                    description='ViPR add vCenter ACL CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Add vCenter ACL details')
    mandatory_args = add_acls_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of vCenter',
                                required=True)
    mandatory_args.add_argument('-tns', '-tenants',
                                metavar='<tenants>',
                                dest='tenants',
                                help='A comma separated list of tenant names',
                                required=True)
    add_acls_parser.set_defaults(func=add_vcenter_acls)


def add_vcenter_acls(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.add_or_remove_vcenter_acls(args.name, args.tenants, "add")
        if res:
            return common.format_json_object(res)
    except SOSError as e:
        raise e

def remove_acls_parser(subcommand_parsers, common_parser):
    remove_acls_parser = subcommand_parsers.add_parser('remove-acls',
                    description='ViPR remove vCenter ACL CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Remove vCenter ACL details')
    mandatory_args = remove_acls_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of vCenter',
                                required=True)
    mandatory_args.add_argument('-tns', '-tenants',
                                metavar='<tenants>',
                                dest='tenants',
                                help='A comma separated list of tenant names',
                                required=True)
    remove_acls_parser.set_defaults(func=remove_vcenter_acls)


def remove_vcenter_acls(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.add_or_remove_vcenter_acls(args.name, args.tenants, "remove")
        if res:
            return common.format_json_object(res)
    except SOSError as e:
        raise e

def get_acls_parser(subcommand_parsers, common_parser):
    get_acls_parser = subcommand_parsers.add_parser('get-acls',
                    description='ViPR get vCenter ACL CLI usage.',
                    parents=[common_parser],
                    conflict_handler='resolve',
                    help='Get vCenter ACL details')
    mandatory_args = get_acls_parser.add_argument_group(
                                    'mandatory arguments')
    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of vCenter',
                                required=True)
    get_acls_parser.set_defaults(func=get_vcenter_acls)


def get_vcenter_acls(args):
    obj = VCenter(args.ip, args.port)
    try:
        res = obj.get_vcenter_acls(args.name)
        if res:
            return common.format_json_object(res)
    except SOSError as e:
        raise e
    

def update_parser(subcommand_parsers, common_parser):
    # update command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR vcenter Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='update a vcenter')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vcenter',
                                metavar='<vcentername>',
                                dest='name',
                                required=True)

    update_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    update_parser.add_argument('-vcenter_ip', '-vcip',
                                help='IP of Vcenter',
                                metavar='<vcenter_ip>',
                                dest='vcenter_ip')

    update_parser.add_argument('-vcenter_port', '-vcpo',
                                help='Port of Vcenter',
                                metavar='<vcenter_port>',
                                dest='vcenter_port')

    update_parser.add_argument('-osversion', '-ov',
                               help='osversion',
                               dest='osversion',
                               metavar='<osversion>',
                               default=None)

    update_parser.add_argument('-ssl', '-usessl',
                               dest='usessl',
                               action='store_true',
                               help='Use SSL or not')

    mandatory_args.add_argument('-user', '-u',
                                help='Name of user',
                                metavar='<user>',
                                dest='user',
                                required=True)

    update_parser.add_argument('-cascade_tenancy', '-cascade',
                               dest='cascade_tenancy',
                               help='Specify true, to cascade the vCenter tenancy to all its Datacenters, Clusters and Hosts. Specify false otherwise',
                               choices=['true', 'false'],
                               default=None)

    update_parser.set_defaults(func=vcenter_update)


def vcenter_update(args):    
    passwd = None
    if (args.user and len(args.user) > 0):
        passwd = common.get_password("vcenter")

    obj = VCenter(args.ip, args.port)
    try:
        res = obj.vcenter_update(args.name, args.tenant, args.vcenter_ip, args.vcenter_port, args.osversion, args.usessl, args.user, passwd,
                                  args.cascade_tenancy)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "vcenter", e.err_text,
                                        e.err_code)


#
# vcenter Main parser routine
#
def vcenter_parser(parent_subparser, common_parser):
    # main vcenter parser
    parser = parent_subparser.add_parser('vcenter',
                                         description='ViPR vcenter CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on vcenter')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # list clusters command parser
    get_clusters_parser(subcommand_parsers, common_parser)

    # list hosts  command parser
    get_hosts_parser(subcommand_parsers, common_parser)

    # list datacemters  command parser
    get_datacenters_parser(subcommand_parsers, common_parser)

    discover_parser(subcommand_parsers, common_parser)

    task_parser(subcommand_parsers, common_parser)

    #add vCenter acls parser
    add_acls_parser(subcommand_parsers, common_parser)

    #remove vCenter acls parser
    remove_acls_parser(subcommand_parsers, common_parser)

    #get vCenter acls parser
    get_acls_parser(subcommand_parsers, common_parser)
    
    # update command parser
    update_parser(subcommand_parsers, common_parser)
