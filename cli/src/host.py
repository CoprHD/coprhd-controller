#!/usr/bin/python

#
# Copyright (c) 2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
#

import common
import json
from common import SOSError
from tenant import Tenant
from cluster import Cluster
from vcenterdatacenter import VcenterDatacenter
from volume import Volume
from virtualarray import VirtualArray
from computeimage import ComputeImage
from computevpool import ComputeVpool
import sys

'''
The class definition for the operation on the ViPR Host
'''


class Host(object):
    # Indentation START for the class

    # All URIs for the Host operations
    URI_HOST_DETAILS = "/compute/hosts/{0}"
    URI_HOST_DEACTIVATE = "/compute/hosts/{0}/deactivate"
    URI_HOST_DETACH_STORAGE = "/compute/hosts/{0}/detach-storage"
    URI_HOST_LIST_INITIATORS = "/compute/hosts/{0}/initiators"
    URI_HOST_LIST_IPINTERFACES = "/compute/hosts/{0}/ip-interfaces"
    URI_HOST_DISCOVER = URI_HOST_DETAILS + "/discover"
    URI_COMPUTE_HOST = "/compute/hosts"
    URI_COMPUTE_HOST_PROV_BARE_METEL = \
                    URI_COMPUTE_HOST + "/provision-bare-metal"
    URI_COMPUTE_HOST_OS_INSTALL = URI_COMPUTE_HOST + "/{0}/os-install"
    URI_HOSTS_SEARCH_BY_NAME = "/compute/hosts/search?name={0}"
    URI_HOST_LIST_UM_EXPORT_MASKS = "/compute/hosts/{0}/unmanaged-export-masks"
    URI_HOST_LIST_UM_VOLUMES = "/compute/hosts/{0}/unmanaged-volumes"

    HOST_TYPE_LIST = ['Windows', 'HPUX', 'Linux',\
                      'Esx', 'Other', 'AIXVIO', 'AIX', 'No_OS','SUNVCS']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    '''
    Search the host matching the hostName and
    tenant if tenantName is provided. tenantName is optional
    '''

    def query_by_name(self, hostName, tenant=None):

        hostList = self.list_all(tenant)

        for host in hostList:
            hostUri = host['id']
            hostDetails = self.show_by_uri(hostUri)
            if(hostDetails):
                if(hostDetails['name'] == hostName):
                    return hostUri

        raise SOSError(SOSError.NOT_FOUND_ERR,
                       "Host with name '" + hostName + "' not found")

    '''
    search the hosts for a given name
    '''

    def search_by_name(self, host_name):
        '''
        Search host by its name
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            self.URI_HOSTS_SEARCH_BY_NAME.format(host_name), None)
        o = common.json_decode(s)
        if not o:
            return []
        return common.get_node_value(o, "resource")

    '''
    List of host uris/ids
    '''

    def list_host_uris(self):
        hostUris = []
        hostList = self.list_all(None, None)

        if(hostList.__len__ > 0):
            for host in hostList:
                hostUri = host['id']
                hostUris.append(hostUri)

        return hostUris

    '''
    Host creation operation
    '''

    def create(self, hostname, hosttype, label, tenant, port,
               username, passwd, usessl, osversion, cluster,
               datacenter, vcenter, autodiscovery,
               bootvolume, project, testconnection):
        '''
        Takes care of creating a host system.
        Parameters:
            hostname: The short or fully qualified host name or IP address
                of the host management interface.
            hosttype : The host type.
            label : The user label for this host.
            osversion : The operating system version of the host.
            port: The integer port number of the host management interface.
            username: The user credential used to login to the host.
            passwd: The password credential used to login to the host.
            tenant: The tenant name to which the host needs to be assigned
            cluster: The id of the cluster if the host is in a cluster.
            use_ssl: One of {True, False}
            datacenter: The id of a vcenter data center if the host is an
                ESX host in a data center.
            autodiscovery : Boolean value to indicate autodiscovery
                true or false
        Reurns:
            Response payload
        '''

        request = {'type': hosttype,
                   'name': label,
                   'host_name': hostname,
                   'port_number': port,
                   'user_name': username,
                   'password': passwd,
                   'discoverable': autodiscovery,
                   'use_ssl': usessl
                   }

        '''
        check if the host is already present in this tenant
        '''
        tenantId = self.get_tenant_id(tenant)
        if(tenantId):
            request['tenant'] = tenantId

        if(osversion):
            request['os_version'] = osversion

        if(cluster):
            request['cluster'] = self.get_cluster_id(cluster, tenant)

        if(datacenter):
            request['vcenter_data_center'] = self.get_vcenterdatacenter_id(
                datacenter, vcenter, tenant)

        if(bootvolume and project):
            path = tenant + "/" + project + "/" + bootvolume
            volume_id = Volume(self.__ipAddr, self.__port).volume_query(path)
            request['boot_volume'] = volume_id

        restapi = Host.URI_COMPUTE_HOST
        if(testconnection):
            restapi = restapi + "?validate_connection=true"

        body = json.dumps(request)
        (s, h) = common.service_json_request(
                                             self.__ipAddr, self.__port,
                                             "POST",
                                             restapi,
                                             body)
        o = common.json_decode(s)

        return o

    '''
    Host update operation
    '''

    def update(self, hostname, hosttype, label, tenant, port,
               username, passwd, usessl, osversion, cluster,
               datacenter, vcenter, newlabel, autodiscovery,
               bootvolume, project):
        '''
        Takes care of creating a host system.
        Parameters:
            hostname: The new short or fully qualified host name or IP address
                of the host management interface.
            hosttype : The new host type.
            label : The user label to be searched.
            osversion : The new operating system version of the host.
            port: The new integer port number of the host management interface.
            username: The new user credential used to login to the host.
            passwd: The new password credential used to login to the host.
            tenant: The tenant name in which the host needs to be searched
            cluster: The new id of the cluster if the host is in a cluster.
            use_ssl: One of {True, False}
            datacenter: The new id of a vcenter data center if the host is
                an ESX host in a data center.
            autodiscovery : Boolean value to indicate autodiscovery
                true or false
        Reurns:
            Response payload
        '''

        hostUri = self.query_by_name(label, tenant)

        request = dict()

        if(hosttype):
            request['type'] = hosttype
        if(newlabel):
            request['name'] = newlabel

        if(hostname):
            request['host_name'] = hostname

        if(port):
            request['port_number'] = port

        if(username):
            request['user_name'] = username
            request['password'] = passwd

        tenantId = self.get_tenant_id(tenant)
        if(tenantId):
            request['tenant'] = tenantId

        if(osversion):
            request['os_version'] = osversion

        if(usessl):
            request['use_ssl'] = usessl

        if(cluster is not None):
            request['cluster'] = self.get_cluster_id(cluster, tenant)

        if(datacenter):
            request['vcenter_data_center'] = self.get_vcenterdatacenter_id(
                datacenter, vcenter, tenant)

        if(autodiscovery):
            request['discoverable'] = autodiscovery

        if(bootvolume and project):
            path = tenant + "/" + project + "/" + bootvolume
            volume_id = Volume(self.__ipAddr, self.__port).volume_query(path)
            request['boot_volume'] = volume_id

        restapi = Host.URI_HOST_DETAILS.format(hostUri)



        body = json.dumps(request)
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            restapi,
            body)
        o = common.json_decode(s)
        return o

    '''
    Deletes the host
    '''

    def delete(self, host_uri, detach_storage=False):
        '''
        Makes a REST API call to delete a storage system by its UUID
        '''
        uri = Host.URI_HOST_DEACTIVATE.format(host_uri)

        if(detach_storage):
            uri = uri + "?detach-storage=true"

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            uri,
            None)
        return

    '''
    Detaches the host
    '''

    def detach(self, host_uri):
        '''
        Makes a REST API call to delete a storage system by its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Host.URI_HOST_DETACH_STORAGE.format(host_uri),
            None)

        return

    '''
    Discover the host
    '''

    def discover(self, host_uri):
        '''
        Makes a REST API call to delete a storage system by its UUID
        '''
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST",
            Host.URI_HOST_DISCOVER.format(host_uri),
            None)

        o = common.json_decode(s)
        return o
    '''
    Gets the ids and self links for all compute elements.
    '''
    def list_all(self, tenant):
        restapi = self.URI_COMPUTE_HOST
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        if(tenant is None):
            tenant_uri = tenant_obj.tenant_getid()
        else:
            tenant_uri = tenant_obj.tenant_query(tenant)
        restapi = restapi + "?tenant=" + tenant_uri

        (s, h) = common.service_json_request(
                            self.__ipAddr, self.__port,
                            "GET",
                            restapi,
                            None)
        o = common.json_decode(s)
        return o['host']

    '''
    Gets the list of Initiators belonging to a given Host
    '''

    def list_initiators(self, hostName):
        '''
         Lists all initiators for the given host
         Parameters
             hostName : The name of the host
        '''
        if(not common.is_uri(hostName)):
            hostUri = self.query_by_name(hostName, None)
        else:
            hostUri = hostName

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Host.URI_HOST_LIST_INITIATORS.format(hostUri),
            None)
        o = common.json_decode(s)

        if(not o or "initiator" not in o):
            return []

        return common.get_node_value(o, 'initiator')

    '''
    Gets the list of IP-Interfaces belonging to a given Host
    '''

    def list_ipinterfaces(self, hostName):
        '''
         Lists all IPInterfaces belonging to a given host
         Parameters
             hostName : The name of the host
        '''
        if(not common.is_uri(hostName)):
            hostUri = self.query_by_name(hostName, None)
        else:
            hostUri = hostName

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Host.URI_HOST_LIST_IPINTERFACES.format(hostUri),
            None)
        o = common.json_decode(s)

        if(not o or "ip_interface" not in o):
            return []

        return common.get_node_value(o, 'ip_interface')

    def list_um_exportmasks(self, hostName):

        if(not common.is_uri(hostName)):
            hostUri = self.query_by_name(hostName, None)
        else:
            hostUri = hostName

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Host.URI_HOST_LIST_UM_EXPORT_MASKS.format(hostUri),
            None)
        if(not s):
            return []
        o = common.json_decode(s)
        res = o["unmanaged_export_mask"]
        return res

    def list_um_volumes(self, hostName):

        if(not common.is_uri(hostName)):
            hostUri = self.query_by_name(hostName, None)
        else:
            hostUri = hostName

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Host.URI_HOST_LIST_UM_VOLUMES.format(hostUri),
            None)
        if(not s):
            return []
        o = common.json_decode(s)
        res = o['unmanaged_volume']
        return res

    '''
    show the host details for a given list of hosts
    '''

    def show(self, hostList):
        hostListDetails = []
        if(hostList is not None):
            for host in hostList:
                hostUri = host['id']
                hostDetail = self.show_by_uri(hostUri)
                if(hostDetail is not None and len(hostDetail) > 0):
                    hostListDetails.append(hostDetail)

        return hostListDetails

    '''
    Get the details of hosts matching the host-type
    '''

    def show_by_type(self, hostList, hosttype):
        hostListDetails = []
        if(hostList is not None):
            for host in hostList:
                hostUri = host['id']
                hostDetail = self.show_by_uri(hostUri)
                if(hostDetail is not None and len(hostDetail) > 0
                   and hostDetail['type'] == hosttype):
                    hostListDetails.append(hostDetail)

        return hostListDetails

    '''
    Get the details of host matching the host-type and name
    '''

    def show_by_type_and_name(self, hostList, hosttype, name, xml):
        hostListDetails = None
        if(hostList is not None):
            for host in hostList:
                hostUri = host['id']
                hostDetail = self.show_by_uri(hostUri)
                if(hostDetail is not None and len(hostDetail) > 0
                   and hostDetail['type'] == hosttype
                   and hostDetail['name'] == name):
                    if(xml):
                        hostListDetails = self.show_by_uri(hostUri, xml)
                    else:
                        hostListDetails = hostDetail
                    break

        return hostListDetails

    '''
    Gets the host system details, given its uri/id
    '''

    def show_by_uri(self, uri, xml=False):
        '''
        Makes a REST API call to retrieve details of a Host based on its UUID
        '''
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Host.URI_HOST_DETAILS.format(uri),
                                             None, None)
        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if(inactive):
            return None
        if(xml):
            (s, h) = common.service_json_request(
                self.__ipAddr, self.__port, "GET",
                Host.URI_HOST_DETAILS.format(uri),
                None, None, xml)
            return s
        else:
            return o

    def get_tenant_id(self, tenantName):
        '''
         Fetch the tenant id
        '''
        tenantObj = Tenant(self.__ipAddr, self.__port)
        tenantId = tenantObj.get_tenant_by_name(tenantName)

        return tenantId

    def get_cluster_id(self, clusterName, tenantName):

        if(clusterName == ""):
            return "null"

        clusterObj = Cluster(self.__ipAddr, self.__port)
        clusterId = clusterObj.cluster_query(clusterName, tenantName)

        return clusterId

    def get_vcenterdatacenter_id(self,
                                 datacenterName, vcenterName, tenantName):

        vcenterDatacenterObj = VcenterDatacenter(self.__ipAddr, self.__port)
        vcenterDatacenterId = vcenterDatacenterObj.vcenterdatacenter_query(
            datacenterName, vcenterName, tenantName)

        return vcenterDatacenterId

    def list_tasks(self, tenant_name, host_name=None, task_id=None):

        hostUri = self.query_by_name(host_name, tenant_name)

        if(host_name):
            host = self.show_by_uri(hostUri)
            if(host['name'] == host_name):
                if(not task_id):
                    return common.get_tasks_by_resourceuri(
                        "host", hostUri,
                        self.__ipAddr, self.__port)

                else:
                    res = common.get_task_by_resourceuri_and_taskId(
                        "host", hostUri, task_id,
                        self.__ipAddr, self.__port)
                    if(res):
                        return res
            raise SOSError(
                SOSError.NOT_FOUND_ERR,
                "Host with name: " +
                host_name +
                " not found")
    '''
    Provision bare metal hosts by taking compute elements from
    the compute virtual pool.
    '''
    def create_compute_hosts(self, tenant, varray, computevpool,
                             hostnames, cluster):
        #get tenant uri
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        if(tenant is None):
            tenant_uri = tenant_obj.tenant_getid()
        else:
            tenant_uri = tenant_obj.tenant_query(tenant)

        #get varray uri
        varray_obj = VirtualArray(self.__ipAddr, self.__port)
        varray_uri = varray_obj.varray_query(varray)

        #get compute pool uri
        computevpool_uri = None
        vpool_obj = ComputeVpool(self.__ipAddr, self.__port)
        computevpool_uri = vpool_obj.computevpool_query(computevpool)

        request = {'varray': varray_uri,
                   'tenant': tenant_uri,
                   'compute_vpool': computevpool_uri,
                   'host_name': hostnames
           }

        #compute cluster name
        if(cluster):
            #cluster
            cluster_obj = Cluster(self.__ipAddr, self.__port)
            cluster_uri = cluster_obj.cluster_query(cluster, tenant)
            request['cluster'] = cluster_uri

        body = json.dumps(request)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST", Host.URI_COMPUTE_HOST_PROV_BARE_METEL, body)
        o = common.json_decode(s)
        return o
    '''
    Install operating system on the host.
    '''
    def compute_host_os_install(self, compute_image_name, volumename,
                                project,
                                tenant,
                                hostname,
                                newhostname,
                                hostip,
                                netmask,
                                gateway,
                                ntp_server,
                                dns_servers,
                                management_network,
                                force_installation,
                                root_password,
                                sync):
        # get host id
        host_id = self.query_by_name(hostname, tenant)
        #get compute image id
        compute_image_id = ComputeImage(self.__ipAddr,
                         self.__port).query_computeimage(compute_image_name)
        request = {'compute_image': compute_image_id,
                   'root_password': root_password
        }

        #get volume id
        if(tenant == None):
            tenant = ""
        if(volumename != None and project != None):
            path = tenant + "/" + project + "/" + volumename
            volume_id = Volume(self.__ipAddr, self.__port).volume_query(path)
            request['volume'] = volume_id
        if(hostname):
            request['host_name'] = newhostname
        if(hostip):
            request['host_ip'] = hostip
        if(netmask):
            request['netmask'] = netmask
        if(gateway):
            request['gateway'] = gateway
        if(ntp_server):
            request['ntp_server'] = ntp_server
        if(dns_servers):
            request['dns_servers'] = dns_servers
        if(management_network):
            request['management_network'] = management_network
        if(force_installation):
            request['force_installation'] = force_installation
        body = json.dumps(request)

        (s, h) = common.service_json_request(
                        self.__ipAddr,
                        self.__port,
                        "PUT",
            Host.URI_COMPUTE_HOST_OS_INSTALL.format(host_id),
            body)
        o = common.json_decode(s)

        return o


    # Indentation END for the class
# Start Parser definitions
def create_parser(subcommand_parsers, common_parser):
    # create command parser
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Host create CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Creates a Host')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-t', '-type',
                                choices=Host.HOST_TYPE_LIST,
                                dest='type',
                                help='Type of host',
                                required=True)

    mandatory_args.add_argument('-hl', '-hostlabel',
                                help='Label for the host',
                                dest='hostlabel',
                                metavar='<hostlabel>',
                                required=True)
    mandatory_args.add_argument(
        '-hn', '-viprhostname',
        help='FQDN of host or IP address of management interface',
        metavar='<viprhostname>',
        dest='viprhostname',
        required=True)

    create_parser.add_argument('-hp', '-hostport',
                                metavar='<hostport>',
                                dest='hostport',
                                type=int,
                                help='Management interface port for the host',
                                required=False)

    mandatory_args.add_argument('-un', '-hostusername',
                                help='User name for the host',
                                dest='hostusername',
                                metavar='<hostusername>',
                                required=True)

    create_parser.add_argument('-t', '-tenant',
                               help='Tenant for the host',
                               dest='tenant',
                               metavar='<tenant>',
                               default=None)

    create_parser.add_argument('-ov', '-osversion',
                               help='Host OS version',
                               dest='osversion',
                               metavar='<osversion>')

    create_parser.add_argument('-c', '-cluster',
                    help='Name of the cluster for the host',
                    dest='cluster',
                    metavar='<cluster>')
    create_parser.add_argument('-dc', '-datacenter',
                    help='Name of the datacenter for the host',
                    dest='datacenter',
                    metavar='<datacenter>')
    create_parser.add_argument(
                    '-vc', '-vcenter',
                    help='Name of the vcenter for datacenter name search',
                    dest='vcentername',
                    metavar='<vcentername>')

    create_parser.add_argument(
        '-hostssl', "-hostusessl",
        dest='hostusessl',
        help='SSL flag for the host: true or false',
        default='false',
        choices=['true', 'false'])
    create_parser.add_argument(
        '-autodiscovery', "-discover",
        dest='autodiscovery',
        help='Boolean value to enable/disable auto discovery of host',
        default='true',
        choices=['true', 'false'])

    create_parser.add_argument('-bootvolume', '-bvol',
                            help='name of bootvolume',
                            dest='bootvolume',
                            metavar='<bootvolume>')
    create_parser.add_argument('-project', '-pr',
                            help='name of project',
                            dest='project',
                            metavar='<project>')

    create_parser.add_argument('-testconnection', '-tc',
                               dest='testconnection',
                               help='validate connection',
                               action='store_true')

    create_parser.set_defaults(func=host_create)

'''
Preprocessor for the host create operation
'''


def host_create(args):

    if(not args.tenant):
        tenant = ""
    else:
        tenant = args.tenant

    if(args.datacenter and args.vcentername is None):
        raise SOSError(
            SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
            " " + sys.argv[2] + ": error:" +
            "-vcentername is required to search the datacenter for the host")

    passwd = None
    if (args.hostusername and len(args.hostusername) > 0):
        passwd = common.get_password("host")

    hostObj = Host(args.ip, args.port)
    try:
        hostObj.create(args.viprhostname, args.type, args.hostlabel, tenant,
                       args.hostport, args.hostusername, passwd,
                       args.hostusessl, args.osversion, args.cluster,
                       args.datacenter, args.vcentername, args.autodiscovery,
                       args.bootvolume, args.project, args.testconnection)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create", "host", e.err_text, e.err_code)


# list command parser
def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Host List CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists hosts')
    list_parser.add_argument('-ht', '-hosttype',
                             choices=Host.HOST_TYPE_LIST,
                             dest='hosttype',
                             help='Type of Host')
    list_parser.add_argument('-t', '-tenant',
                             dest='tenant',
                             metavar='<tenant>',
                             help='Tenant for which hosts to be listed',
                             default=None)
    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             action='store_true',
                             help='Lists Hosts with details')
    list_parser.add_argument('-l', '-long',
                             dest='largetable',
                             action='store_true',
                             help='Lists Hosts in a large table')
    list_parser.set_defaults(func=host_list)


'''
Preprocessor for hosts list operation
'''


def host_list(args):

    hostList = None
    hostObj = Host(args.ip, args.port)
    from common import TableGenerator
    if(not args.tenant):
        tenant = ""
    else:
        tenant = args.tenant
    try:
        hostList = hostObj.list_all(tenant)

        if(len(hostList) > 0):
            hostListDetails = []
            if(args.hosttype is None):
                hostListDetails = hostObj.show(hostList)
            else:
                hostListDetails = hostObj.show_by_type(hostList, args.hosttype)

            if(args.verbose):
                return common.format_json_object(hostListDetails)
            else:
                if(args.largetable):
                    TableGenerator(hostListDetails, ['name', 'host_name',
                                   'type', 'user_name',
                                   'registration_status',
                                   'job_discovery_status']).printTable()
                else:
                    TableGenerator(hostListDetails,
                                   ['name', 'host_name', 'type']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "host", e.err_text, e.err_code)


# show command parser
def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Host Show CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show Host details')
    show_parser.add_argument(
        '-xml',
        dest='xml',
        action='store_true',
        help='XML response')
    mutex_group = show_parser.add_mutually_exclusive_group(required=True)
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-t', '-type',
                                dest='type',
                                help='Type of Host',
                                choices=Host.HOST_TYPE_LIST,
                                required=True)

    mutex_group.add_argument('-n', '-name',
                             metavar='<name>',
                             dest='name',
                             help='Name of Host')

    show_parser.add_argument('-tenant', '-tn',
                             metavar='<tenantname>',
                             dest='tenant',
                             help='Name of tenant',
                             default=None)

    show_parser.set_defaults(func=host_show)


def host_show(args):

    try:
        hostList = []
        hostObj = Host(args.ip, args.port)

        hostList = hostObj.list_all(args.tenant)

        hostdetails = None
        if(len(hostList) > 0):
            hostdetails = hostObj.show_by_type_and_name(
                hostList, args.type, args.name, args.xml)

        if(hostdetails is None):
            raise SOSError(
                SOSError.NOT_FOUND_ERR, "Could not find the matching host")

        if(args.xml):
            return common.format_xml(hostdetails)
        return common.format_json_object(hostdetails)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "host", e.err_text, e.err_code)

    return


def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Host delete CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Deletes a Host')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Host',
                                required=True)
    mandatory_args.add_argument('-t', '-type',
                                choices=Host.HOST_TYPE_LIST,
                                dest='type',
                                help='Type of Host',
                                required=True)
    delete_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    delete_parser.add_argument('-detachstorage', '-ds',
                               dest='detachstorage',
                               action='store_true',
                               help='Detach storege before deactivation')
    delete_parser.set_defaults(func=host_delete)


def host_delete(args):
    try:
        hostList = []
        hostObj = Host(args.ip, args.port)

        hostList = hostObj.list_all(args.tenant)

        if(len(hostList) > 0):
            hostdetails = hostObj.show_by_type_and_name(
                hostList, args.type, args.name, False)
            if(hostdetails):
                hostObj.delete(hostdetails['id'], args.detachstorage)
            else:
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Could not find the matching host")
        else:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Could not find the matching host")

    except SOSError as e:
        common.format_err_msg_and_raise(
            "delete", "host", e.err_text, e.err_code)

    return


'''
Update Host Parser
'''


def update_parser(subcommand_parsers, common_parser):
    # create command parser
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Host update CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Updates a Host')

    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    update_parser.add_argument(
        '-nhn', '-newviprhostname',
        help='New FQDN of host or IP address of management interface',
        metavar='<newviprhostname>',
        dest='newviprhostname')
    update_parser.add_argument('-nt', '-newtype',
                               choices=Host.HOST_TYPE_LIST,
                               dest='newtype',
                               help='New type of host')

    mandatory_args.add_argument('-hl', '-hostlabel',
                                help='search label for the host',
                                dest='hostlabel',
                                metavar='<hostlabel>',
                                required=True)
    
    update_parser.add_argument('-nl', '-newlabel',
                               help='New label for the host',
                               dest='newlabel',
                               metavar='<newlabel>')

    update_parser.add_argument(
        '-nhp', '-newhostport',
        help='New Management interface port for the host',
        dest='newhostport',
        metavar='<newhostport>')

    update_parser.add_argument('-nun', '-newhostusername',
                               help='New user name for the host',
                               dest='newhostusername',
                               metavar='<newhostusername>')

    update_parser.add_argument(
        '-tn', '-tenant',
        help='Tenant in which host needs to be searched',
        dest='tenant',
        metavar='<tenant>')

    update_parser.add_argument(
        '-hostssl', "-hostusessl",
        dest='newhostusessl',
        help='SSL flag for the host: true or false',
        default='false',
        choices=['true', 'false'])

    update_parser.add_argument('-ov', '-osversion',
                               help='Host OS version',
                               dest='newosversion',
                               metavar='<osversion>')

    update_parser.add_argument(
        '-nc', '-newcluster',
        help="New name of the cluster or " +
             "empty string to remove the host from cluster",
        dest='newcluster',
        metavar='<cluster>')

    update_parser.add_argument('-ndc', '-newdatacenter',
                               help='New name of the datacenter for the host',
                               dest='newdatacenter',
                               metavar='<newdatacenter>')

    update_parser.add_argument(
        '-vc', '-vcenter',
        help='Name of the vcenter for datacenter name search',
        dest='vcentername',
        metavar='<vcentername>')

    update_parser.add_argument(
        '-autodiscovery', "-discover",
        dest='autodiscovery',
        help='Boolean value to enable/disable auto discovery of host',
        choices=['true', 'false'])

    update_parser.add_argument('-bootvolume', '-bvol',
                            help='name of bootvolume',
                            dest='bootvolume',
                            metavar='<bootvolume>')

    update_parser.add_argument('-project', '-pr',
                            help='name of project',
                            dest='project',
                            metavar='<project>')


    update_parser.set_defaults(func=host_update)


'''
Preprocessor for the host update operation
'''


def host_update(args):
    if(args.tenant is None and args.newviprhostname is None
       and args.newtype is None and args.newhostport is None
       and args.newhostusername is None and args.newosversion is None
       and args.newcluster is None and args.newdatacenter is None
       and args.newlabel is None and args.autodiscovery is None):
        raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
                       " " + sys.argv[2] + ": error:" +
                       "At least one of the arguments :"
                       "-tenant -newviprhostname -newtype -newhostusessl"
                       "-newhostport -newhostusername"
                       "-newosversion -newcluster -newdatacenter -newlabel"
                       " -autodiscovery should be provided to update the Host")
    if(args.newdatacenter and args.vcentername is None):
        raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " + sys.argv[1] +
                       " " + sys.argv[2] + ": error:" +
                       "-vcentername is required to search " +
                       "the datacenter for the host")

    passwd = None
    if (args.newhostusername and len(args.newhostusername) > 0):
        passwd = common.get_password("host")

    hostObj = Host(args.ip, args.port)
    try:
        hostObj.update(args.newviprhostname, args.newtype, args.hostlabel,
                           args.tenant, args.newhostport,
                           args.newhostusername, passwd, args.newhostusessl,
                           args.newosversion, args.newcluster,
                           args.newdatacenter, args.vcentername,
                           args.newlabel, args.autodiscovery,
                           args.bootvolume, args.project)
    except SOSError as e:
        common.format_err_msg_and_raise("update",
                                        "host", e.err_text, e.err_code)


# list initiators command parser
def list_initiator_parser(subcommand_parsers, common_parser):
    list_initiator_parser = subcommand_parsers.add_parser(
        'list-initiators',
        description='ViPR Host list-initiator CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists Initiators')
    mandatory_args = list_initiator_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        help='Label of the host for which initiators to be listed',
        required=True)
    list_initiator_parser.add_argument('-v', '-verbose',
                                       dest='verbose',
                                       action='store_true',
                                       help='Lists initiators with details')
    list_initiator_parser.add_argument(
        '-l', '-long',
        dest='largetable',
        action='store_true',
        help='Lists initiators in a large table')
    list_initiator_parser.set_defaults(func=host_list_initiators)


def host_list_initiators(args):
    hostObj = Host(args.ip, args.port)
    from common import TableGenerator

    try:
        initiatorList = hostObj.list_initiators(args.hostlabel)

        if(len(initiatorList) > 0):
            initiatorListDetails = []
            from hostinitiators import HostInitiator
            hostInitiatorObj = HostInitiator(args.ip, args.port)
            initiatorListDetails = hostInitiatorObj.show(initiatorList)

            for item in initiatorListDetails:
                if(not item["name"]):
                    item["name"]= " "

            if(args.verbose):
                return common.format_json_object(initiatorListDetails)
            else:
                if(args.largetable):
                    for item in initiatorListDetails:
                        if( "name" not in item or item['name']==""):
                            item["name"]=" "
                        if( "protocol" not in item or item['protocol']==""):
                            item["protocol"]=" "
                        if( "initiator_node" not in item or item['initiator_node']==""):
                            item["initiator_node"]=" "
                        if( "initiator_port" not in item or item['initiator_port']==""):
                            item["initiator_port"]=" "
                        if( "hostname" not in item or item['hostname']==""):
                            item["hostname"]=" "
                    TableGenerator(initiatorListDetails,
                                   ['name', 'protocol', 'initiator_node',
                                    'initiator_port', 'hostname']).printTable()
                else:
                    TableGenerator(initiatorListDetails,
                                   ['name', 'protocol',
                                    'hostname']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list-initiators", "host",
                                        e.err_text, e.err_code)


# list initiators command parser
def list_ipinterfaces_parser(subcommand_parsers, common_parser):
    list_ipinterfaces_parser = subcommand_parsers.add_parser(
        'list-ipinterfaces',
        description='ViPR Host list-ipinterfaces CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists ipinterfaces')
    mandatory_args = list_ipinterfaces_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        help='Label of the host for which ipinterfaces to be listed',
        required=True)
    list_ipinterfaces_parser.add_argument('-v', '-verbose',
                                          dest='verbose',
                                          action='store_true',
                                          help='Lists Hosts with details')
    list_ipinterfaces_parser.add_argument('-l', '-long',
                                          dest='largetable',
                                          action='store_true',
                                          help='Lists Hosts in a large table')
    list_ipinterfaces_parser.set_defaults(func=host_list_ipinterfaces)


def host_list_ipinterfaces(args):
    hostObj = Host(args.ip, args.port)
    from common import TableGenerator

    try:
        ipinterfacesList = hostObj.list_ipinterfaces(args.hostlabel)

        if(len(ipinterfacesList) > 0):
            ipinterfacesListDetails = []
            from hostipinterfaces import HostIPInterface
            hostIpinterfaceObj = HostIPInterface(args.ip, args.port)
            ipinterfacesListDetails = hostIpinterfaceObj.show(ipinterfacesList)

            if(args.verbose):
                return common.format_json_object(ipinterfacesListDetails)
            else:
                if(args.largetable):
                    TableGenerator(ipinterfacesListDetails, ['name',
                                   'ip_address', 'protocol', 'netmask',
                                   'prefix_length']).printTable()
                else:
                    TableGenerator(ipinterfacesListDetails, ['name',
                                   'ip_address', 'protocol']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list-ipinterfaces", "host",
                                        e.err_text, e.err_code)


def list_exportmasks_parser(subcommand_parsers, common_parser):
    list_exportmasks_parser = subcommand_parsers.add_parser(
        'list-umexportmasks',
        description='ViPR Host list-umexportmasks CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists unmanaged export masks')
    mandatory_args = list_exportmasks_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        help='Label of the host',
        required=True)
    list_exportmasks_parser.add_argument('-v', '-verbose',
        dest='verbose',
        action='store_true',
        help='Lists unmanaged export masks with details')
    list_exportmasks_parser.set_defaults(func=host_list_exportmasks)


def host_list_exportmasks(args):

    hostObj = Host(args.ip, args.port)

    try:
        um_export_mask_list = hostObj.list_um_exportmasks(args.hostlabel)

        if(len(um_export_mask_list) > 0):

            if(args.verbose):
                return common.format_json_object(um_export_mask_list)
            else:
                from common import TableGenerator
                TableGenerator(um_export_mask_list,
                               ['id']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise("list-umexportmasks", "host",
                                        e.err_text, e.err_code)


def list_umvolumes_parser(subcommand_parsers, common_parser):
    list_umvols_parser = subcommand_parsers.add_parser(
        'list-umvolumes',
        description='ViPR Host list-umvolumes CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Lists unmanaged volumes')
    mandatory_args = list_umvols_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument(
        '-hl', '-hostlabel',
        dest='hostlabel',
        help='Label of the host',
        required=True)
    list_umvols_parser.add_argument('-v', '-verbose',
        dest='verbose',
        action='store_true',
        help='Lists unmanaged volumes with details')
    list_umvols_parser.set_defaults(func=host_list_umvolumes)


def host_list_umvolumes(args):

    hostObj = Host(args.ip, args.port)

    try:
        um_volume_list = hostObj.list_um_volumes(args.hostlabel)

        if(len(um_volume_list) > 0):

            if(args.verbose):
                return common.format_json_object(um_volume_list)

            else:
                from common import TableGenerator
                TableGenerator(um_volume_list,
                               ['id']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list-umvolumes", "host",
                                        e.err_text, e.err_code)


def detach_parser(subcommand_parsers, common_parser):
    detach_parser = subcommand_parsers.add_parser(
        'detach',
        description='ViPR Host detach CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Detach a Host')
    mandatory_args = detach_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Host',
                                required=True)
    mandatory_args.add_argument('-t', '-type',
                                choices=Host.HOST_TYPE_LIST,
                                dest='type',
                                help='Type of Host',
                                required=True)
    detach_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    detach_parser.set_defaults(func=host_detach)


def host_detach(args):
    try:
        hostList = []
        hostObj = Host(args.ip, args.port)

        hostList = hostObj.list_all(args.tenant)

        if(len(hostList) > 0):
            hostdetails = hostObj.show_by_type_and_name(
                hostList, args.type, args.name, False)
            if(hostdetails):
                hostObj.detach(hostdetails['id'])
            else:
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Could not find the matching host")
        else:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Could not find the matching host")

    except SOSError as e:
        common.format_err_msg_and_raise(
            "detach", "host", e.err_text, e.err_code)

    return


def discover_parser(subcommand_parsers, common_parser):
    discover_parser = subcommand_parsers.add_parser(
        'discover',
        description='ViPR Host discover CLI usage ',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Discover a Host')
    mandatory_args = discover_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Host',
                                required=True)
    mandatory_args.add_argument('-t', '-type',
                                choices=Host.HOST_TYPE_LIST,
                                dest='type',
                                help='Type of Host',
                                required=True)
    discover_parser.add_argument('-tenant', '-tn',
                                 metavar='<tenantname>',
                                 dest='tenant',
                                 help='Name of tenant')
    discover_parser.set_defaults(func=host_discover)


def host_discover(args):
    try:
        hostList = []
        hostObj = Host(args.ip, args.port)

        hostList = hostObj.list_all(args.tenant)

        if(len(hostList) > 0):
            hostdetails = hostObj.show_by_type_and_name(
                hostList, args.type, args.name, False)
            if(hostdetails):
                hostObj.discover(hostdetails['id'])
            else:
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "Could not find the matching host")
        else:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           "Could not find the matching host")

    except SOSError as e:
        common.format_err_msg_and_raise(
            "discover", "host", e.err_text, e.err_code)

    return


def task_parser(subcommand_parsers, common_parser):
    # show command parser
    task_parser = subcommand_parsers.add_parser(
        'tasks',
        description='ViPR host tasks  CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='check tasks of a host')

    mandatory_args = task_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-n', '-name',
                                metavar='<name>',
                                dest='name',
                                help='Name of Host',
                                required=True)
    '''mandatory_args.add_argument('-t', '-type',
                                choices=Host.HOST_TYPE_LIST,
                                dest='type',
                                help='Type of Host',
                                required=True)'''

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

    task_parser.set_defaults(func=host_list_tasks)


def host_list_tasks(args):
    obj = Host(args.ip, args.port)

    try:
        # if(not args.tenant):
        #    args.tenant = ""
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
        common.format_err_msg_and_raise("get tasks list", "host",
                                        e.err_text, e.err_code)


def compute_host_osinstall_parser(subcommand_parsers, common_parser):
    os_install_parser = subcommand_parsers.add_parser(
            'compute-host-os-install',
            description='Install operating system on the host',
            parents=[common_parser],
            conflict_handler='resolve',
            help='Install operating system on the host')
    mandatory_args = os_install_parser.add_argument_group(
                                        'mandatory arguments')
    mandatory_args.add_argument('-computeimage', '-ci',
                            help='name of computeimage',
                            dest='computeimage',
                            metavar='<computeimagename>',
                            required=True)
    mandatory_args.add_argument('-volume', '-vol',
                            help='name of volume',
                            dest='volume',
                            metavar='<volume>',
                            required=True)
    mandatory_args.add_argument('-project', '-pr',
                            help='name of project',
                            dest='project',
                            metavar='<project>',
                            required=True)
    os_install_parser.add_argument('-tenant', '-t',
                             help='Tenant for the host',
                             dest='tenant',
                             metavar='<tenant>',
                             default=None)
    mandatory_args.add_argument('-name', '-n',
                                help='name of a host',
                                dest='name',
                                metavar='<hostname>',
                                required=True)
    mandatory_args.add_argument('-newhostname', '-nhn',
                                help='name of a newhostname',
                                dest='newhostname',
                                metavar='<newhostname>')
    os_install_parser.add_argument('-hostip', '-ip',
                                help='hostip of a newhost',
                                dest='hostip',
                                metavar='<hostip>')
    os_install_parser.add_argument('-netmask', '-nm',
                                help='ipaddress of netmask',
                                dest='netmask',
                                metavar='<netmask>')
    os_install_parser.add_argument('-gateway', '-gw',
                                help='ipaddress of gateway',
                                dest='gateway',
                                metavar='<gateway>')
    os_install_parser.add_argument('-ntpserver', '-ns',
                                help='ipaddress of ntpserver',
                                dest='ntpserver',
                                metavar='<ntpserver>')
    os_install_parser.add_argument('-dnsservers', '-ds',
                                help='ipaddress of dnsservers',
                                dest='dnsservers',
                                metavar='<dnsservers>')
    os_install_parser.add_argument('-managementnetwork', '-mn',
                                help='managementnetwork',
                                dest='managementnetwork',
                                metavar='<managementnetwork>')
    os_install_parser.add_argument('-forceinstallation', '-ump',
                            help='forceinstallation',
                            metavar='<forceinstallation>',
                            dest='forceinstallation',
                            choices=['true', 'false'])
    os_install_parser.add_argument('-synchronous', '-sync',
                               dest='sync',
                               help='Execute in synchronous mode',
                               action='store_true')

    os_install_parser.set_defaults(func=compute_host_os_install)


def compute_host_os_install(args):
    hostObj = Host(args.ip, args.port)

    rootpasswd = common.get_password("host")
    hostObj.compute_host_os_install(args.computeimage, args.volume,
                            args.project, args.tenant, args.name,
                            args.newhostname,
                            args.hostip, args.netmask,
                            args.gateway, args.ntpserver,
                            args.dnsservers, args.managementnetwork,
                            args.forceinstallation, rootpasswd, args.sync)
    return


def compute_host_create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
            'provision-bare-metal-host',
            description='Creates hosts by taking compute elements from \
                    the virtual compute pool CLI usage',
            parents=[common_parser],
            conflict_handler='resolve',
            help='Provision bare metal hosts')

    mandatory_args = create_parser.add_argument_group('mandatory arguments')

    create_parser.add_argument('-tenant', '-t',
                               help='Tenant for the host',
                               dest='tenant',
                               metavar='<tenant>',
                               default=None)
    create_parser.add_argument('-cluster', '-c',
                               help='Name of the cluster for the host',
                               dest='cluster',
                               metavar='<cluster>')
    mandatory_args.add_argument('-computevpool', '-cvp',
                                help='name of computevpool',
                                dest='computevpool',
                                metavar='<computevpoolname>',
                                required=True)
    mandatory_args.add_argument('-hostnames', '-hn',
                                metavar='<hostnames>',
                                dest='hostnames',
                                help='list of hostnames seperated by space',
                                nargs='+',
                                required=True)
    mandatory_args.add_argument('-varray', '-va',
                                help='name of varray',
                                dest='varray',
                                metavar='<varrayname>',
                                required=True)
    create_parser.set_defaults(func=compute_host_create)


def compute_host_create(args):

    if(not args.tenant):
        tenant = ""
    else:
        tenant = args.tenant

    hostObj = Host(args.ip, args.port)
    try:
        hostObj.create_compute_hosts(args.tenant, args.varray,
        args.computevpool, args.hostnames, args.cluster)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "create", "host", e.err_text, e.err_code)


#
# Host Main parser routine
#
def host_parser(parent_subparser, common_parser):

    parser = parent_subparser.add_parser('host',
                                         description='ViPR host CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on host')
    subcommand_parsers = parser.add_subparsers(help='use one of sub-commands')

    # create command parser
    create_parser(subcommand_parsers, common_parser)

    # list command parser
    list_parser(subcommand_parsers, common_parser)

    # show parser
    show_parser(subcommand_parsers, common_parser)

    # delete parser
    delete_parser(subcommand_parsers, common_parser)

    # update parser
    update_parser(subcommand_parsers, common_parser)

    # List Initiators parser
    list_initiator_parser(subcommand_parsers, common_parser)

    # List ipinterfaces parser
    list_ipinterfaces_parser(subcommand_parsers, common_parser)

    # discover ipinterfaces parser
    discover_parser(subcommand_parsers, common_parser)

    # detach ipinterfaces parser
    detach_parser(subcommand_parsers, common_parser)

    # task parser
    task_parser(subcommand_parsers, common_parser)

    # os install parser
    compute_host_osinstall_parser(subcommand_parsers, common_parser)

    # provision-bare-metal-host parser
    compute_host_create_parser(subcommand_parsers, common_parser)

    #list unmanaged volumes parser
    list_umvolumes_parser(subcommand_parsers, common_parser)

    #list unmanaged export masks parser
    list_exportmasks_parser(subcommand_parsers, common_parser)
