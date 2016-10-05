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

from common import SOSError


class VcenterDatacenter(object):

    '''
    The class definition for operations on 'VcenterDatacenter'.
    '''

    # Commonly used URIs for the 'vcenterdatacenters' module
    URI_SERVICES_BASE = ''
    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_VCENTER = URI_SERVICES_BASE + '/compute/vcenters/{0}'
    URI_VCENTER_DATACENTERS = URI_VCENTER + '/vcenter-data-centers'
    URI_DATACENTERS = URI_SERVICES_BASE + '/compute/vcenter-data-centers'
    URI_DATACENTER = URI_SERVICES_BASE + '/compute/vcenter-data-centers/{0}'
    URI_DATACENTER_CLUSTERS = URI_DATACENTER + '/clusters'
    URI_DATACENTER_HOSTS = URI_DATACENTER + '/hosts'

    URI_DATACENTERS_CREATE_CLUSTER = \
                            URI_DATACENTERS + "/{0}/create-vcenter-cluster"
    URI_DATACENTERS_UPDATE_CLUSTER = \
                            URI_DATACENTERS + "/{0}/update-vcenter-cluster"
    DATACENTERS_FROM_ALL_TENANTS = "No-Filter";
    DATACENTERS_WITH_NO_TENANTS = "Not-Assigned";

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def vcenterdatacenter_query(self, name, vcenter, tenantname):
        '''
        Returns the UID of the vcenterdatacenter specified by the name
        '''
        if (common.is_uri(name)):
            return name

        vcenterdatacenters = self.vcenterdatacenter_list(vcenter, tenantname)

        for vcenterdatacenter in vcenterdatacenters:
            if (vcenterdatacenter['name'] == name):
                return vcenterdatacenter['id']

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "vcenterdatacenter " + name + ": not found")

    def vcenterdatacenter_list(self, vcenter, tenantname):
        '''
        Returns all the vcenterdatacenters in a vdc
        Parameters:
        Returns:
                JSON payload of vcenterdatacenter list
        '''

        from vcenter import VCenter
        obj = VCenter(self.__ipAddr, self.__port)
        uri = obj.vcenter_query(vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VcenterDatacenter.URI_VCENTER_DATACENTERS.format(uri), VcenterDatacenter.DATACENTERS_FROM_ALL_TENANTS)

        o = common.json_decode(s)

        return o['vcenter_data_center']

    def vcenterdatacenter_get_clusters(self, label, vcenter,
                                       tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details of a vcenterdatacenter
        based on its UUID
        '''
        uri = self.vcenterdatacenter_query(label, vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VcenterDatacenter.URI_DATACENTER_CLUSTERS.format(uri),
            None, None, xml)

        o = common.json_decode(s)

        from cluster import Cluster
        obj = Cluster(self.__ipAddr, self.__port)

        dtlslst = obj.cluster_get_details_list(o['cluster'])

        return dtlslst

    def vcenterdatacenter_get_hosts(self, label,
                                    vcenter, tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details of a
        vcenterdatacenter  based on its UUID
        '''
        uri = self.vcenterdatacenter_query(label, vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VcenterDatacenter.URI_DATACENTER_HOSTS.format(uri),
            None, None, xml)

        from host import Host
        obj = Host(self.__ipAddr, self.__port)

        o = common.json_decode(s)
        hostsdtls = obj.show(o['host'])

        return hostsdtls

    def vcenterdatacenter_show(self, label, vcenter, tenantname, xml=False):
        '''
        Makes a REST API call to retrieve details of a vcenterdatacenter
        based on its UUID
        '''
        uri = self.vcenterdatacenter_query(label, vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VcenterDatacenter.URI_DATACENTER.format(uri),
            None, None, xml)

        if(not xml):
            o = common.json_decode(s)

            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o

    def vcenterdatacenter_show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a vcenterdatacenter
        based on its UUID
        '''

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            VcenterDatacenter.URI_DATACENTER.format(uri),
            None, None, xml)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s

        return o

    def vcenterdatacenter_create(self, label, vcenter, tenantname):
        '''
        creates a vcenterdatacenter
        parameters:
            label:  label of the vcenterdatacenter
        Returns:
            JSON payload response
        '''
        try:
            check = self.vcenterdatacenter_show(label, vcenter, tenantname)
            if(not check):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "vcenterdatacenter " + label + ": not found")

        except SOSError as e:
            if(e.err_code == SOSError.NOT_FOUND_ERR):
                from vcenter import VCenter
                obj = VCenter(self.__ipAddr, self.__port)

                vcenteruri = obj.vcenter_query(vcenter, tenantname)

                var = dict()
                params = dict()
                params['name'] = label

                body = json.dumps(params)
                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, "POST",
                    VcenterDatacenter.URI_VCENTER_DATACENTERS.format(
                        vcenteruri), body)
                o = common.json_decode(s)
                return o
            else:
                raise e

        if(check):
            raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                           "vcenterdatacenter with name " + label +
                           " already exists")

    def vcenterdatacenter_delete(self, label, vcenter, tenantname):
        '''
        Makes a REST API call to delete a vcenterdatacenter by its UUID
        '''
        uri = self.vcenterdatacenter_query(label, vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            self.URI_RESOURCE_DEACTIVATE.format(
                VcenterDatacenter.URI_DATACENTER.format(uri)), None)
        return str(s) + " ++ " + str(h)

    def vcenterdatacenter_get_details(self, vcenterdatacenters):
        lst = []
        for iter in vcenterdatacenters:
            dtls = self.vcenterdatacenter_show_by_uri(iter['id'])
            if(dtls):
                lst.append(dtls)

        return lst

        '''
        Create a new vCenter cluster with all hosts and datastores
        '''
    def vcenterdatacenter_create_cluster(self, name, vcenter, cluster,
                                                        tenantname):
        from cluster import Cluster
        cl_uri = Cluster(self.__ipAddr, self.__port).cluster_query(cluster, name ,vcenter, tenantname)

        dc_uri = self.vcenterdatacenter_query(name, vcenter, tenantname)

        params = {'id': cl_uri}
        body = json.dumps(params)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            VcenterDatacenter.URI_DATACENTERS_CREATE_CLUSTER.format(dc_uri),
            body)
        return common.json_decode(s)

        '''
        Updates an existing vCenter cluster with new hosts and datastores
        '''
    def vcenterdatacenter_update_cluster(self, name, vcenter, cluster,
                                                        tenantname):
        from cluster import Cluster
        cl_uri = Cluster(self.__ipAddr, self.__port).cluster_query(cluster, name, vcenter, tenantname)

        dc_uri = self.vcenterdatacenter_query(name, vcenter, tenantname)

        params = {'id': cl_uri}

        body = json.dumps(params)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            VcenterDatacenter.URI_DATACENTERS_UPDATE_CLUSTER.format(dc_uri),
            body)
        return common.json_decode(s)

    def vcenterdatacenter_update(self, label, vcenter, tenantname, newtenantname):
        '''
        updates a vcenterdatacenter
        parameters:
            label:  label of the vcenterdatacenter
        Returns:
            JSON payload response
        '''
        try:
            check = self.vcenterdatacenter_show(label, vcenter, tenantname)
            if check:
                raise SOSError(SOSError.ENTRY_ALREADY_EXISTS_ERR,
                               "vcenterdatacenter " + label + ": found")

        except SOSError as e:
            if e.err_code == SOSError.ENTRY_ALREADY_EXISTS_ERR:

                uri = self.vcenterdatacenter_query(label, vcenter, VcenterDatacenter.DATACENTERS_FROM_ALL_TENANTS)

                params = dict()
                params['name'] = label
                
                if newtenantname is not None and newtenantname != 'null':
                    from tenant import Tenant
                    obj = Tenant(self.__ipAddr, self.__port)
                    params['tenant'] = obj.tenant_query(newtenantname)
                elif newtenantname is not None:
                     params['tenant'] = newtenantname

                body = json.dumps(params)

                (s, h) = common.service_json_request(
                    self.__ipAddr, self.__port, "PUT",
                    VcenterDatacenter.URI_DATACENTER.format(uri), body)

                o = common.json_decode(s)

                return o
            else:
                raise e

        if not check:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "vcenterdatacenter with name " + label +
                           " dost not exist")


# datacenter Create routines

def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR vcenterdatacenter Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a vcenterdatacenter')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vcenterdatacenter',
                                metavar='<vcenterdatacentername>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)

    create_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    create_parser.set_defaults(func=vcenterdatacenter_create)


def vcenterdatacenter_create(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_create(args.name,
                                           args.vcenter, args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "vcenterdatacenter",
                                        e.err_text, e.err_code)


# datacenter Delete routines

def delete_parser(subcommand_parsers, common_parser):
    # delete command parser
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR vcenterdatacenter Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a vcenterdatacenter')

    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenterdatacenter',
                                dest='name',
                                metavar='<vcenterdatacentername>',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)

    delete_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    delete_parser.set_defaults(func=vcenterdatacenter_delete)


def vcenterdatacenter_delete(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_delete(args.name,
                                           args.vcenter, args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "vcenterdatacenter",
                                        e.err_text, e.err_code)

# datacenter Show routines


def show_parser(subcommand_parsers, common_parser):
    # show command parser
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR vcenterdatacenter Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a vcenterdatacenter')

    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenterdatacenter',
                                dest='name',
                                metavar='<vcenterdatacentername>',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
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

    show_parser.set_defaults(func=vcenterdatacenter_show)


def vcenterdatacenter_show(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_show(args.name, args.vcenter,
                                         args.tenant, args.xml)

        if(not res):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "vcenterdatacenter " + args.name + ": not found")

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "vcenterdatacenter",
                                        e.err_text, e.err_code)


# datacenter get hosts routines
def get_hosts_parser(subcommand_parsers, common_parser):
    # show command parser
    get_hosts_parser = subcommand_parsers.add_parser(
        'get-hosts',
        description='ViPR vcenterdatacenter get hosts CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the hosts of a vcenterdatacenter')

    mandatory_args = get_hosts_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenterdatacenter',
                                dest='name',
                                metavar='<vcenterdatacentername>',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
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

    get_hosts_parser.set_defaults(func=vcenterdatacenter_get_hosts)


def vcenterdatacenter_get_hosts(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_get_hosts(args.name,
                                              args.vcenter, args.tenant)

        if(len(res) > 0):
            if(args.verbose):
                return common.format_json_object(res)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(res, ['name', 'type', 'job_discovery_status',
                                     'job_metering_status']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("get hosts", "vcenterdatacenter",
                                        e.err_text, e.err_code)


# datacenter get clusters routines
def get_clusters_parser(subcommand_parsers, common_parser):
    # show command parser
    get_clusters_parser = subcommand_parsers.add_parser(
        'get-clusters',
        description='ViPR vcenterdatacenter get clusters CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the clusters of  a vcenterdatacenter')

    mandatory_args = get_clusters_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenterdatacenter',
                                dest='name',
                                metavar='<vcenterdatacentername>',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
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

    get_clusters_parser.set_defaults(func=vcenterdatacenter_get_clusters)


def vcenterdatacenter_get_clusters(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_get_clusters(args.name,
                                                 args.vcenter, args.tenant)

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
        common.format_err_msg_and_raise("get clusters", "vcenterdatacenter",
                                        e.err_text, e.err_code)

# datacenter Query routines


def query_parser(subcommand_parsers, common_parser):
    # query command parser
    query_parser = subcommand_parsers.add_parser(
        'query',
        description='ViPR vcenterdatacenter Query CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Query a vcenterdatacenter')

    mandatory_args = query_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of vcenterdatacenter',
                                dest='name',
                                metavar='<vcenterdatacentername>',
                                required=True)
    query_parser.add_argument('-tenant', '-tn',
                              help='Name of Tenant',
                              metavar='<tenant>',
                              dest='tenant',
                              default=None)

    query_parser.set_defaults(func=vcenterdatacenter_query)


def vcenterdatacenter_query(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_query(args.name, args.tenant)
        return common.format_json_object(res)
    except SOSError as e:
        if(e.err_code == SOSError.NOT_FOUND_ERR):
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "vcenterdatacenter query failed: " + e.err_text)
        else:
            raise e

# datacenter List routines


def list_parser(subcommand_parsers, common_parser):
    # list command parser
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR vcenterdatacenter List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List of vcenterdatacenters')

    mandatory_args = list_parser.add_argument_group('mandatory arguments')

    list_parser.add_argument('-verbose', '-v',
                             action='store_true',
                             help='List vcenterdatacenters with details',
                             dest='verbose')

    list_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List vcenterdatacenters with more details in tabular form',
        dest='long')

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)
    list_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    list_parser.set_defaults(func=vcenterdatacenter_list)


def vcenterdatacenter_list(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        uris = obj.vcenterdatacenter_list(args.vcenter, args.tenant)
        output = []
        outlst = []

        for uri in uris:
            temp = obj.vcenterdatacenter_show_by_uri(uri['id'], False)
            if(temp):
                output.append(temp)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):
                from common import TableGenerator
                TableGenerator(output,
                               ['name', 'auto_san_zoning',
                                'auto_tier_policy']).printTable()
            else:
                from common import TableGenerator
                TableGenerator(output, ['name']).printTable()

    except SOSError as e:
        raise e

# datacenter Create cluster routines


def create_cluster_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create-cluster',
        description='ViPR vcenterdatacenter Create-cluster CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a new vCenter cluster')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vcenterdatacenter',
                                metavar='<vcenterdatacentername>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)
    mandatory_args.add_argument('-cluster',
                                help='name of cluster',
                                dest='cluster',
                                metavar='<cluster>',
                                required=True)

    create_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    create_parser.set_defaults(func=vcenterdatacenter_create_cluster)


def vcenterdatacenter_create_cluster(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_create_cluster(args.name,
                                           args.vcenter,
                                           args.cluster,
                                           args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("create-cluster", "vcenterdatacenter",
                                        e.err_text, e.err_code)


# datacenter Create cluster routines


def update_cluster_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'update-cluster',
        description='ViPR vcenterdatacenter Update-cluster CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a new vCenter cluster')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vcenterdatacenter',
                                metavar='<vcenterdatacentername>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)
    mandatory_args.add_argument('-cluster',
                                help='name of cluster',
                                dest='cluster',
                                metavar='<cluster>',
                                required=True)

    create_parser.add_argument('-tenant', '-tn',
                               help='Name of Tenant',
                               metavar='<tenant>',
                               dest='tenant',
                               default=None)

    create_parser.set_defaults(func=vcenterdatacenter_update_cluster)


def vcenterdatacenter_update_cluster(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_update_cluster(args.name,
                                           args.vcenter,
                                           args.cluster,
                                           args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("update-cluster", "vcenterdatacenter",
                                        e.err_text, e.err_code)

#
# vcenterdatacenter update routines
#

def update_parser(subcommand_parsers, common_parser):
    # create command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR vCenterDataCenter Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a vCenterDataCenter')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of vCenterDataCenter',
                                metavar='<vcenterdatacentername>',
                                dest='name',
                                required=True)

    mandatory_args.add_argument('-vcenter',
                                help='vcenter',
                                dest='vcenter',
                                metavar='<vcenter>',
                                required=True)

    mandatory_args.add_argument('-tenant', '-tn',
                                help='Name of Tenant',
                                metavar='<tenant>',
                                dest='tenant',
                                required=True)

    update_parser.add_argument('-newtenant', '-ntn',
                               help='Name of the new Tenant to be updated. Provide null if want to remove the exsiting tenant from the datacetner',
                               metavar='<newtenant>',
                               dest='newtenant',
                               default=None)

    update_parser.set_defaults(func=vcenterdatacenter_update)


def vcenterdatacenter_update(args):
    obj = VcenterDatacenter(args.ip, args.port)
    try:
        res = obj.vcenterdatacenter_update(args.name,
                                           args.vcenter, args.tenant, args.newtenant)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "vcenterdatacenter",
                                        e.err_text, e.err_code)


#
# vcenterdatacenter Main parser routine
#

def vcenterdatacenter_parser(parent_subparser, common_parser):
    # main vcenterdatacenter parser
    parser = parent_subparser.add_parser(
        'vcenterdatacenter',
        description='ViPR vcenterdatacenter CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on vcenterdatacenter')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # get clusters parser
    get_clusters_parser(subcommand_parsers, common_parser)

    # get hosts parser
    get_hosts_parser(subcommand_parsers, common_parser)

    # create vcenter cluster parser
    create_cluster_parser(subcommand_parsers, common_parser)

    # update vcenter cluster parser
    update_cluster_parser(subcommand_parsers, common_parser)

    # update vcenter datacenter parser
    update_parser(subcommand_parsers, common_parser)
