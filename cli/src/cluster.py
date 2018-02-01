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
import json
import sys
from common import SOSError
from tenant import Tenant
from vcenterdatacenter import VcenterDatacenter
from common import TableGenerator


class Cluster(object):

    '''
    The class definition for operations on 'Cluster'.
    '''
    URI_SERVICES_BASE = ''
    URI_TENANT = URI_SERVICES_BASE + '/tenant'
    URI_TENANTS = URI_SERVICES_BASE + '/tenants/{0}'
    URI_TENANTS_CLUSTERS = URI_TENANTS + '/clusters'

    URI_CLUSTERS = URI_SERVICES_BASE + '/compute/clusters'
    URI_CLUSTER = URI_SERVICES_BASE + '/compute/clusters/{0}'
    URI_CLUSTERS_BULKGET = URI_CLUSTERS + '/bulk'
    URI_CLUSTER_DETACH = URI_CLUSTER + '/detach-storage'

    URI_CLUSTER_SEARCH = URI_SERVICES_BASE + '/compute/clusters/search'
    URI_CLUSTER_SEARCH_NAME = URI_CLUSTER_SEARCH + '?name={0}'
    URI_CLUSTER_HOSTS = URI_CLUSTER + '/hosts'

    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'
    URI_GET_CLUSTER = '/compute/vcenter-data-centers/{0}/clusters'
    URI_CLUSTER_LIST_UM_VOLUMES = URI_CLUSTER + "/unmanaged-volumes"
    URI_CLUSTER_LIST_UM_EXPORT_MASKS = URI_CLUSTER + "/unmanaged-export-masks"
    BOOL_TYPE_LIST = ['true', 'false']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

        '''
        create cluster action
        Parameters:
            name      : Name of the cluster
            tenant    : name of tenant
            datacenter: Name of datacenter
            vcenter   : name of vcenter
        Returns:
            result of the action.
        '''

    def cluster_create(self, label, tenant, datacenter, vcenter):
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        vdatacenterobj = VcenterDatacenter(self.__ipAddr, self.__port)

        if(tenant is None):
            tenant_uri = tenant_obj.tenant_getid()
        else:
            tenant_uri = tenant_obj.tenant_query(tenant)

        parms = {'name': label}

        # datacenter
        if(datacenter):
            # on failure, query raise exception
            parms['vcenter_data_center'] = \
                vdatacenterobj.vcenterdatacenter_query(
                    datacenter, vcenter, tenant)
                

        body = json.dumps(parms)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Cluster.URI_TENANTS_CLUSTERS.format(tenant_uri),
            body)
        o = common.json_decode(s)

    '''
        list cluster action
        Parameters:
            tenant : name of tenant
        Returns:
            return cluster id list
        '''

    def cluster_list(self, tenant):
        uri = Tenant(self.__ipAddr, self.__port).tenant_query(tenant)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_TENANTS_CLUSTERS.format(uri), None)
        o = common.json_decode(s)
        return o['cluster']

        '''
        show cluster action
        Parameters:
            label : Name of the cluster
            tenant : name of tenant
            xml    : content-type
        Returns:
            cluster detail information
        '''

    def cluster_show(self, label,datacenter,vcenter, tenant=None, xml=False):

        uri = self.cluster_query(label,datacenter,vcenter, tenant)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Cluster.URI_CLUSTER.format(uri),
                                             None, None, xml)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s
        return o
    '''
        Makes a REST API call to retrieve details of a cluster based on UUID
        Parameters:
            uri : uri of the cluster
        Returns:
            cluster detail information
        '''

    def cluster_show_uri(self, uri):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Cluster.URI_CLUSTER.format(uri),
                                             None, None, False)
        o = common.json_decode(s)

        if(o['inactive'] is False):
            return o

        return None

    '''
        search cluster action
        Parameters:
            name : Name of the cluster
        Returns:
            return clusters list
        '''

    
    def vcenterdatacenter_get_clusters(self, datacenter_uri):
        '''
        Makes a REST API call to retrieve details of a vcenterdatacenter
        based on its UUID
        '''
        

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_GET_CLUSTER.format(datacenter_uri) , None)

        o = common.json_decode(s)
        
        return o
    
    def cluster_search(self, name):

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_CLUSTER_SEARCH_NAME.format(name), None)
        o = common.json_decode(s)
        return o['resource']

    
    
    def cluster_query(self, name, datacenter, vcenter, tenant=None):
        
        if(datacenter is None and vcenter is None):
            resources = self.cluster_search(name)
            for resource in resources:
                details = self.cluster_show_uri(resource['id'])
                if (details.get('vcenter_data_center') is None and details['name'] == name):
                    return resource['id']

            raise SOSError(SOSError.NOT_FOUND_ERR,
                       "cluster " + name + ": not found")
        
        
        else:
        #find the uri of the datacenter
            from vcenterdatacenter import VcenterDatacenter
            datacenter_uri = self.get_datacenter_uri(datacenter,vcenter,tenant)
        
            result = self.vcenterdatacenter_get_clusters(datacenter_uri)

            for cluster in result['cluster']:
                if cluster['name'] == name:
                    return cluster['id']        
        
        
    
        '''
        
        delete cluster action
        Parameters:
            name : Name of the cluster
            tenant : name of tenant
        Returns:
            result of the action.
        '''

    def cluster_delete(self, name,datacenter,vcenter, tenant=None, detachstorage=False):

        uri = self.cluster_query(name,datacenter,vcenter, tenant)

        formaturi = self.URI_RESOURCE_DEACTIVATE.format(
            Cluster.URI_CLUSTER.format(uri))

        if(detachstorage):
            formaturi = formaturi + "?detach-storage=true"

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", formaturi,
            None)
        return

        '''
        detach cluster action
        Parameters:
            name : Name of the cluster
            tenant : name of tenant
        Returns:
            result of the action.
        '''

    def cluster_detach(self, name,datacenter,vcenter, tenant=None):

        uri = self.cluster_query(name,datacenter,vcenter, tenant)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Cluster.URI_CLUSTER_DETACH.format(uri),
            None)

        return

    def cluster_update(self, name, tenant, datacenter, vcenter, label, newdatacenter, newvcenter, updateExports=False):
        '''
        update cluster with datacenter, label
        Parameters:
            name      : Name of the cluster
            tenant    : name of tenant
            datacenter: Name of datacenter
            vcenter   : name of vcenter
            ndatacenter : name of new datacenter
            nvcenter : name of the new vcenter 
            label     : new name to existing cluster
        Returns:
            result of the action.
        '''
        parms = {}
        # new name
        if(label):
            parms['name'] = label
        
        # datacenter
        if(newdatacenter is not None):
            vdatacenterobj = VcenterDatacenter(self.__ipAddr, self.__port)
            data_uri = vdatacenterobj.vcenterdatacenter_query(
                newdatacenter, newvcenter, tenant)
            
            parms['vcenter_data_center'] = data_uri

        # get the cluster uri query to the right cluster ..
        
        cluster_uri = self.cluster_query(name, datacenter , vcenter ,tenant)


        if(updateExports is not None):
            cluster_uri = cluster_uri + "?update-exports=" + updateExports


        body = json.dumps(parms)
        common.service_json_request(self.__ipAddr, self.__port, "PUT",
                                    Cluster.URI_CLUSTER.format(cluster_uri),
                                    body)
        return

    '''
        get the uri of a datacenter
        Parameters:
            datacenter : Name of the datacenter
            vcenter : name of vcenter

        Returns:
            uri of datacenter
        '''

    def get_datacenter_uri(self, datacenter, vcenter,tenant):
        vdatacenterobj = VcenterDatacenter(self.__ipAddr, self.__port)
        return vdatacenterobj.vcenterdatacenter_query(datacenter, vcenter,tenant)

    def cluster_get_details_list(self, detailslst):
        rsltlst = []
        for item in detailslst:
            detail = self.cluster_show_uri(item['id'])
            if(detail):
                rsltlst.append(self.cluster_show_uri(item['id']))

        return rsltlst

    def list_tasks(self, tenant_name, cluster_name=None,datacenter=None,vcenter=None, task_id=None):

        uri = self.cluster_query(cluster_name,datacenter,vcenter, tenant_name)

        if(cluster_name):
            cluster = self.cluster_show_uri(uri)
            if(cluster['name'] == cluster_name):
                if(not task_id):
                    return common.get_tasks_by_resourceuri(
                        "cluster", cluster["id"],
                        self.__ipAddr, self.__port)

                else:
                    res = common.get_task_by_resourceuri_and_taskId(
                        "cluster", cluster["id"], task_id,
                        self.__ipAddr, self.__port)
                    if(res):
                        return res
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Cluster with name: " +
                cluster_name +
                " not found")

    '''
        get the list of hosts associated with Cluster '''

    def cluster_get_hosts(self, label, datacenter, vcenter, tenantname):

        '''
        Makes a REST API call to retrieve details of a hosts
        associated with cluster
        '''

        uri = self.cluster_query(label, datacenter, vcenter, tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_CLUSTER_HOSTS.format(uri),
            None, None, False)

        from host import Host
        obj = Host(self.__ipAddr, self.__port)

        o = common.json_decode(s)
        hostsdtls = obj.show(o['host'])

        return hostsdtls

    def list_um_exportmasks(self, clusterName ,datacenter, vcenter):

        cluster_uri = self.cluster_query(clusterName, datacenter, vcenter, None)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_CLUSTER_LIST_UM_EXPORT_MASKS.format(cluster_uri),
            None)
        if(not s):
            return []
        o = common.json_decode(s)
        res = o["unmanaged_export_mask"]
        return res

    def list_um_volumes(self, clusterName, datacenter, vcenter):

        cluster_uri = self.cluster_query(clusterName, datacenter, vcenter, None)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_CLUSTER_LIST_UM_VOLUMES.format(cluster_uri),
            None)
        if(not s):
            return []
        o = common.json_decode(s)
        res = o["unmanaged_volume"]
        return res

    def show_um_export_mask(self, export_mask_id):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Cluster.URI_UM_EXPORT_MASK.format(export_mask_id),
            None)
        if(not s):
            return None
        o = common.json_decode(s)
        return o


# create command parser
def create_parser(subcommand_parsers, common_parser):

    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Cluster Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Create a Cluster')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name', '-n',
        metavar='<name>',
        dest='name',
        help='name for the cluster',
        required=True)
    create_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='name of tenant',
                               default=None)
    create_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of a datacenter')
    create_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>')
                               
    create_parser.set_defaults(func=cluster_create)


def cluster_create(args):
    obj = Cluster(args.ip, args.port)
    try:
        if(args.datacenter or args.vcenter):
            if(args.datacenter is None or args.vcenter is None):
                print ("Both vCenter and Data Center details are required")
                return
        obj.cluster_create(args.name, args.tenant,
                           args.datacenter, args.vcenter)
    except SOSError as e:
        common.format_err_msg_and_raise("create", "cluster",
                                        e.err_text, e.err_code)

# delete command parser


def delete_parser(subcommand_parsers, common_parser):

    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Cluster Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a Cluster')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    delete_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    delete_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='name of tenant',
                               default=None)
    delete_parser.add_argument('-detachstorage', '-ds',
                               dest='detachstorage',
                               action='store_true',
                               help='Detach storege before deactivation')

    delete_parser.set_defaults(func=cluster_delete)


def cluster_delete(args):
    obj = Cluster(args.ip, args.port)
    try:
        obj.cluster_delete(args.name, args.tenant,args.datacenter, args.vcenter, args.detachstorage)
        return
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "cluster",
                                        e.err_text, e.err_code)

# show command parser


def show_parser(subcommand_parsers, common_parser):

    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Cluster Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show a Cluster')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    show_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    show_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='name of tenant',
                             default=None)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=cluster_show)


def cluster_show(args):
    obj = Cluster(args.ip, args.port)
    try:
        res = obj.cluster_show(args.name, args.datacenter, args.vcenter, args.tenant, args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "cluster",
                                        e.err_text, e.err_code)


# list command parser
def list_parser(subcommand_parsers, common_parser):

    list_parser = subcommand_parsers.add_parser(
        'list',
        description='StorageOS Cluster List CLI usage.',
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
        help='List cluster with more details in tabular form',
        dest='long')

    list_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    list_parser.set_defaults(func=cluster_list)


def cluster_list(args):
    obj = Cluster(args.ip, args.port)
    try:
        clusters = obj.cluster_list(args.tenant)
        output = []
        vdatacenterobj = VcenterDatacenter(args.ip, args.port)
        for cluster_uri in clusters:
            clobj = obj.cluster_show_uri(cluster_uri['id'])
            if(clobj):
                # add vdatacenter name to cluster object
                if('vcenter_data_center' in clobj and args.long):
                    vobj = vdatacenterobj.vcenterdatacenter_show_by_uri(
                        clobj['vcenter_data_center']['id'])
                    clobj['vcenter_data_center'] = vobj['name']
                output.append(clobj)

        if(len(output) > 0):
            if(args.verbose):
                return common.format_json_object(output)
            elif(args.long):

                TableGenerator(output,
                               ['name', 'vcenter_data_center']).printTable()
            else:
                TableGenerator(output, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "cluster",
                                        e.err_text, e.err_code)


# update command parser
def update_parser(subcommand_parsers, common_parser):

    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Cluster Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Update a Cluster')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)

    update_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='new name of tenant',
                               default=None)
    update_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    update_parser.add_argument('-newdatacenter', '-ndc',
                               metavar='<newdatacentername>',
                               dest='newdatacenter',
                               help='new name of datacenter')
    update_parser.add_argument('-label', '-l',
                               metavar='<label>',
                               dest='label',
                               help='new label for the cluster')
    update_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    update_parser.add_argument('-newvcenter', '-nvc',
                               help='new name of a vcenter',
                               dest='newvcenter',
                               metavar='<newvcentername>')    
    update_parser.add_argument('-updateExports' , '-updateEx' ,
                               help="Updates the exports during cluster update" ,
                               dest='updateExports' ,
                               default='false' ,
                               choices = Cluster.BOOL_TYPE_LIST)

    update_parser.set_defaults(func=cluster_update)


def cluster_update(args):
    obj = Cluster(args.ip, args.port)
    try:
        if(args.label is None and args.tenant is None and
           args.datacenter is None and args.vcenter is None):
            raise SOSError(
                SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
                " " + sys.argv[2] + ": error:" + "At least one of the"
                " arguments :-tenant -label -vcenter -datacenter"                
                " should be provided to update the cluster")

        if(args.datacenter or args.vcenter):
            if(args.datacenter is None or args.vcenter is None):
                raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " +
                               sys.argv[1] + " " + sys.argv[2] + ": error:" +
                               "For a vcenter associated cluster, both " +
                               "vcenter and datacenter needs to be specified")

        obj.cluster_update(args.name, args.tenant, args.datacenter,
                           args.vcenter, args.label ,args.newdatacenter, args.newvcenter, args.updateExports)
    except SOSError as e:
        common.format_err_msg_and_raise("update", "cluster",
                                        e.err_text, e.err_code)


def detach_parser(subcommand_parsers, common_parser):

    detach_parser = subcommand_parsers.add_parser(
        'detach',
        description='ViPR Cluster Detach CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Detach a Cluster')
    mandatory_args = detach_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    detach_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    detach_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    detach_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='name of tenant',
                               default=None)
    detach_parser.set_defaults(func=cluster_detach)


def cluster_detach(args):
    obj = Cluster(args.ip, args.port)
    try:
        obj.cluster_detach(args.name,args.datacenter,args.vcenter, args.tenant)
    except SOSError as e:
        common.format_err_msg_and_raise("detach", "cluster",
                                        e.err_text, e.err_code)


def task_parser(subcommand_parsers, common_parser):
    # show command parser
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR cluster tasks  CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='check tasks of a vcenter')

    mandatory_args = task_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    task_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    task_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    task_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='name of tenant',
                             default=None)

    task_parser.add_argument('-id',
                             dest='id',
                             metavar='<id>',
                             help='Task ID')

    task_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action="store_true",
                             help='List all tasks')

    task_parser.set_defaults(func=cluster_list_tasks)


def cluster_list_tasks(args):
    obj = Cluster(args.ip, args.port)

    try:
        if(not args.tenant):
            args.tenant = ""
        if(args.id):
            res = obj.list_tasks(args.tenant, args.name,args.datacenter, args.vcenter, args.id)
            if(res):
                return common.format_json_object(res)
        elif(args.name):
            res = obj.list_tasks(args.tenant, args.name, args.datacenter, args.vcenter)
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
        common.format_err_msg_and_raise("get tasks list", "cluster",
                                        e.err_text, e.err_code)


def get_hosts_parser(subcommand_parsers, common_parser):
    # get hosts command parser
    get_hosts_parser = subcommand_parsers.add_parser(
        'get-hosts',
        description='ViPR Cluster get hosts CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show the hosts of a cluster')

    mandatory_args = get_hosts_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='name of cluster',
                                dest='name',
                                metavar='<clustername>',
                                required=True)
    get_hosts_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    get_hosts_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)

    get_hosts_parser.add_argument('-tenant', '-tn',
                                  help='Name of Tenant',
                                  metavar='<tenant>',
                                  dest='tenant',
                                  default=None)

    get_hosts_parser.add_argument(
        '-long', '-l',
        action='store_true',
        help='List hosts with more details in tabular form',
        dest='long')

    get_hosts_parser.add_argument('-verbose', '-v',
                                  action='store_true',
                                  help='List hosts with details',
                                  dest='verbose')

    get_hosts_parser.set_defaults(func=cluster_get_hosts)


def cluster_get_hosts(args):
    obj = Cluster(args.ip, args.port)
    try:
        res = obj.cluster_get_hosts(args.name,args.datacenter, args.vcenter, args.tenant)

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
        common.format_err_msg_and_raise("get hosts", "cluster",
                                        e.err_text, e.err_code)


def list_exportmasks_parser(subcommand_parsers, common_parser):
    list_exportmasks_parser = subcommand_parsers.add_parser(
        'list-umexportmasks',
        description='ViPR Cluster list-umexportmasks CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists unmanaged export masks')
    mandatory_args = list_exportmasks_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    list_exportmasks_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    list_exportmasks_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    list_exportmasks_parser.add_argument('-v', '-verbose',
        dest='verbose',
        action='store_true',
        help='Lists unmanaged export masks with details')

    list_exportmasks_parser.set_defaults(func=cluster_list_exportmasks)


def cluster_list_exportmasks(args):
    clusterObj = Cluster(args.ip, args.port)

    try:
        um_export_mask_list = clusterObj.list_um_exportmasks(args.name, args.datacenter, args.vcenter)

        if(len(um_export_mask_list) > 0):

            if(args.verbose):
                return common.format_json_object(um_export_mask_list)
            else:
                TableGenerator(um_export_mask_list, ['id']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise("list-umexportmasks", "cluster",
                                        e.err_text, e.err_code)


def list_umvolumes_parser(subcommand_parsers, common_parser):
    list_umvols_parser = subcommand_parsers.add_parser(
        'list-umvolumes',
        description='ViPR Cluster list-umvolumes CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists unmanaged volumes')
    mandatory_args = list_umvols_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                metavar='<name>',
                                dest='name',
                                help='name of a the cluster',
                                required=True)
    list_umvols_parser.add_argument('-datacenter', '-dc',
                               metavar='<datacentername>',
                               dest='datacenter',
                               help='name of datacenter',
                               default=None)
    list_umvols_parser.add_argument('-vcenter', '-vc',
                               help='name of a vcenter',
                               dest='vcenter',
                               metavar='<vcentername>',
                               default=None)
    list_umvols_parser.add_argument('-v', '-verbose',
        dest='verbose',
        action='store_true',
        help='Lists unmanaged volumes with details')

    list_umvols_parser.set_defaults(func=cluster_list_umvolumes)


def cluster_list_umvolumes(args):
    clusterObj = Cluster(args.ip, args.port)

    try:
        um_volume_list = clusterObj.list_um_volumes(args.name, args.datacenter, args.vcenter)

        if(len(um_volume_list) > 0):

            if(args.verbose):
                return common.format_json_object(um_volume_list)
            else:
                TableGenerator(um_volume_list, ['id']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise("list-umvolumes", "cluster",
                                        e.err_text, e.err_code)


def cluster_parser(parent_subparser, common_parser):
    # main cluster parser
    parser = parent_subparser.add_parser(
        'cluster',
        description='ViPR Cluster CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Cluster')
    subcommand_parsers = parser.add_subparsers(
        help='Use one of sub commands(create, list, show, delete, update)')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show command parser
    show_parser(subcommand_parsers, common_parser)

    # delete command parser
    delete_parser(subcommand_parsers, common_parser)

    # update command parser
    update_parser(subcommand_parsers, common_parser)

    # detach  command parser
    detach_parser(subcommand_parsers, common_parser)

    # task command parser
    task_parser(subcommand_parsers, common_parser)

    # list hosts  command parser
    get_hosts_parser(subcommand_parsers, common_parser)

    list_exportmasks_parser(subcommand_parsers, common_parser)

    list_umvolumes_parser(subcommand_parsers, common_parser)
