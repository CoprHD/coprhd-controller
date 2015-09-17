# Copyright (c) 2013 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
import common
from common import SOSError


class ControlService(object):

    # URIs
    URI_CONTROL_CLUSTER_POWEROFF = '/control/cluster/poweroff'
    #URI_CONTROL_NODE_REBOOT = '/control/node/reboot?node_id={0}'
    #URI_CONTROL_SERVICE_RESTART = \
     #   '/control/service/restart?node_id={0}&name={1}'
    URI_CONTROL_NODE_REBOOT = '/control/node/reboot'
    URI_CONTROL_SERVICE_RESTART = '/control/service/restart'
    URI_TASK_DELETE = '/vdc/tasks/{0}/delete'
    
    
    
    
    
    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def rebootNode(self, nodeId , nodename):
        
        
        if (nodename is not None):
            uri = ControlService.URI_CONTROL_NODE_REBOOT + "?node_name=" + nodename 
        if (nodeId is not None):
            uri = ControlService.URI_CONTROL_SERVICE_RESTART + "?node_id=" + nodeId 
            
        

        #START - rebootNode
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            uri,
            None)
        if(not s):
            return None

        o = common.json_decode(s)
        print("response : " + o)
        return o
        #END - rebootNode

    def clusterPoweroff(self):
        #START - clusterPoweroff
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            ControlService.URI_CONTROL_CLUSTER_POWEROFF,
            None)
        if(not s):
            return None

        o = common.json_decode(s)
        return o
        #END - clusterPoweroff
        
    

    def restartService(self, nodeId, serviceName , nodename):
        
        print nodeId + " and " + serviceName 
        if (nodename is not None and serviceName is not None ):
            uri = ControlService.URI_CONTROL_SERVICE_RESTART + "?node_name=" + nodename + "&name=" + serviceName
        if (nodeId is not None and serviceName is not None):
            uri = ControlService.URI_CONTROL_SERVICE_RESTART + "?node_id=" + nodeId + "&name=" + serviceName
            
        
        
        #START - restartService
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            uri , None)
        if(not s):
            return None

        o = common.json_decode(s)
        return o
        #END - restartService

    def task_delete_by_uri(self,uri):
         '''
	 Deletes a task based on task id 
	 '''

         (s,h) = common.service_json_request(self.__ipAddr,self.__port,
	            "POST",ControlService.URI_TASK_DELETE.format(uri),None)
         return s		 

class Backup(object):

    URI_BACKUP_SET = "/backupset/backup?tag={0}"
    URI_BACKUP_SET_LIST = "/backupset/"
    URI_BACKUP_SET_DOWNLOAD = "/backupset/download?tag={0}"

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def create(self, name, force=False):

        request_uri = Backup.URI_BACKUP_SET.format(name)

        if(force is True):
            request_uri += '&force=true'

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            request_uri, None)
        if(not s):
            return None

        o = common.json_decode(s)
        return o

    def delete(self, name):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "DELETE",
            Backup.URI_BACKUP_SET.format(
            name), None)
        if(not s):
            return None

        o = common.json_decode(s)
        return o

    def list_backupsets(self):
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Backup.URI_BACKUP_SET_LIST, None)
        if(not s):
            return []
        o = common.json_decode(s)
        if (not o):
            return []
        if ('backupsets_info' in o):
            result = o['backupsets_info']
            if(result is None):
                return []
            elif isinstance(result, list):
                return result
            else:
                return [result]
        else:
            return []

    def download(self, name, filepath):

        if(filepath.endswith(".zip") is False):
            filepath += ".zip"

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Backup.URI_BACKUP_SET_DOWNLOAD.format(
            name), None,
                None, False, "application/octet-stream", filepath)
        if(not s):
            return None

        o = common.json_decode(s)
        return o

# START Parser definitions

'''
Parser for the restart-service command
'''


def restart_service_parser(subcommand_parsers, common_parser):
    # restart-service command parser
    restart_service_parser = subcommand_parsers.add_parser(
        'restart-service',
        description='ViPR restart-service CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Restarts a service')

    mandatory_args = restart_service_parser.add_argument_group(
        'mandatory arguments')
    restart_service_parser.add_argument(
        '-id', '-nodeid',
        help='Id of the node from which the service to be restarted',
        metavar='<nodeid>',
        dest='nodeid')
    mandatory_args.add_argument('-svc', '-servicename ',
                                dest='servicename',
                                metavar='<servicename>',
                                help='Name of the service to be restarted',
                                required=True)
    restart_service_parser.add_argument('-nodename', '-ndname ',
                                dest='nodename',
                                metavar='<node_name>',
                                help='Name of the node to be restarted')

    restart_service_parser.set_defaults(func=restart_service)


def restart_service(args):
    nodeId = args.nodeid
    serviceName = args.servicename
    nodename = args.nodename

    try:
        if(args.nodename is not None and args.nodeid is not None):
            print "Error: Enter either Node name or Node ID "
            return
        if(args.nodename is None and args.nodeid is None):
            print "Error : Enter either Node name or Node ID "
            return
            
        response = common.ask_continue(
            "restart service:" +
            serviceName )
        if(str(response) == "y"):
            contrlSvcObj = ControlService(args.ip, args.port)
            contrlSvcObj.restartService(nodeId, serviceName , nodename)
    except SOSError as e:
        common.format_err_msg_and_raise(
            "restart-service",
            serviceName ,
            e.err_text,
            e.err_code)


'''
Parser for the reboot node
'''


def reboot_node_parser(subcommand_parsers, common_parser):
    # create command parser
    reboot_node_parser = subcommand_parsers.add_parser(
        'reboot-node',
        description='ViPR reboot-node CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Reboots the node')

    mandatory_args = reboot_node_parser.add_argument_group(
        'mandatory arguments')
    reboot_node_parser.add_argument('-id', '-nodeid',
                                help='Id of the node to be rebooted',
                                metavar='<nodeid>',
                                dest='nodeid')
    
    reboot_node_parser.add_argument('-nodename', '-ndname',
                                help='name of the node to be rebooted',
                                metavar='<nodename>',
                                dest='nodename')

    reboot_node_parser.set_defaults(func=reboot_node)


def reboot_node(args):
    nodeId = args.nodeid
    nodename = args.nodename

    try:
        if(args.nodename is not None and args.nodeid is not None):
            print " Enter Either Nodename or Nodeid "
            return
        if(args.nodename is None and args.nodeid is None):
            print "Error : Enter either Node name or Node ID "
            return
        response = common.ask_continue("reboot node:" )
        if(str(response) == "y"):
            contrlSvcObj = ControlService(args.ip, args.port)
            contrlSvcObj.rebootNode(nodeId , nodename )
    except SOSError as e:
        common.format_err_msg_and_raise(
            "reboot-node",
            e.err_text,
            e.err_code)


'''
Parser for the cluster poweroff
'''


def cluster_poweroff_parser(subcommand_parsers, common_parser):
    # create command parser
    cluster_poweroff_parser = subcommand_parsers.add_parser(
        'cluster-poweroff',
        description='ViPR cluster-poweroff CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Power off the cluster')

    cluster_poweroff_parser.set_defaults(func=cluster_poweroff)


def cluster_poweroff(args):

    try:
        response = common.ask_continue("power-off the cluster")
        if(str(response) == "y"):
            contrlSvcObj = ControlService(args.ip, args.port)
            contrlSvcObj.clusterPoweroff()
    except SOSError as e:
        common.format_err_msg_and_raise(
            "cluster-poweroff",
            "",
            e.err_text,
            e.err_code)


def delete_tasks_parser(subcommand_parsers,common_parser):
    #delete task command parser
    delete_parser = subcommand_parsers.add_parser('delete-task',
                description='ViPR Delete Task CLI usage.',
		                                  parents=[common_parser],
						  conflict_handler='resolve',
						  help='Delete a task using task id')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-tid','-taskid',
                                metavar='<taskid>',
				dest='taskid',
				help='id of a delete task',
				required=True)
    delete_parser.set_defaults(func=task_delete)

def task_delete(args):

    try:
        obj = ControlService(args.ip,args.port)
	res = obj.task_delete_by_uri(args.taskid)
    except SOSError as e:
        common.format_err_msg_and_raise(
            'delete-task', 'system',
	    e.err_text, e.err_code)





'''
Parser function definitions for Backup CLI
'''


def create_backup_parser(subcommand_parsers, common_parser):
    create_backup_parser = subcommand_parsers.add_parser(
        'create-backup',
        description='ViPR create backup CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help="Creates a ViPR backup set.")
    mandatory_args = create_backup_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                help="Name of the backup. " +
                "It must not have '_' (underscore) character",
                metavar='<backup name>',
                dest='name',
                required=True)
    create_backup_parser.add_argument('-force',
                help='Create backup forcibly',
                action='store_true',
                dest='force')
    create_backup_parser.set_defaults(func=create_backup)


def create_backup(args):

    try:
        obj = Backup(args.ip, args.port)
        res = obj.create(args.name, args.force)
    except SOSError as e:
        common.format_err_msg_and_raise(
            'create', 'backup',
            e.err_text, e.err_code)


def delete_backup_parser(subcommand_parsers, common_parser):

    delete_backup_parser = subcommand_parsers.add_parser(
        'delete-backup',
        description='ViPR delete backup CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help="Deletes a ViPR backup set")
    mandatory_args = delete_backup_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                help='Name of the backup',
                metavar='<backup name>',
                dest='name',
                required=True)
    delete_backup_parser.set_defaults(func=delete_backup)


def delete_backup(args):

    try:
        obj = Backup(args.ip, args.port)
        res = obj.delete(args.name)
    except SOSError as e:
        common.format_err_msg_and_raise(
            'delete', 'backup',
            e.err_text, e.err_code)


def list_backup_parser(subcommand_parsers, common_parser):

    list_backup_parser = subcommand_parsers.add_parser(
        'list-backup',
        description='ViPR list backup CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help="List all ViPR backup set")
    list_backup_parser.add_argument('-v', '-verbose',
                                dest='verbose',
                                help='List backups with details',
                                action='store_true')
    list_backup_parser.set_defaults(func=list_backup)


def list_backup(args):

    try:
        obj = Backup(args.ip, args.port)
        res = obj.list_backupsets()
        if(len(res) > 0):
            if(args.verbose is True):
                return common.format_json_object(res)
            else:
                from datetime import datetime
                from common import TableGenerator
                for item in res:
                    value = datetime.fromtimestamp(float(item['create_time'])
                                                   / 1000)
                    item['creation_time'] = value.strftime('%Y-%m-%d %H:%M:%S')
                    item['size_in_mb'] = float(float(item['size'])
                                               / (1024 * 1024))

                TableGenerator(
                        res, ['name', 'size_in_mb',
                              'creation_time']).printTable()
    except SOSError as e:
        common.format_err_msg_and_raise(
            'list', 'backup',
            e.err_text, e.err_code)


def download_backup_parser(subcommand_parsers, common_parser):

    download_backup_parser = subcommand_parsers.add_parser(
        'download-backup',
        description='ViPR download backup CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help="Download a ViPR backup set")
    mandatory_args = download_backup_parser.add_argument_group(
        'mandatory arguments')
    mandatory_args.add_argument('-name', '-n',
                                help='Name of the backup',
                                metavar='<backup name>',
                                dest='name',
                                required=True)
    mandatory_args.add_argument('-filepath', '-fp',
                                help='Download file path',
                                metavar='<filepath>',
                                dest='filepath',
                                required=True)
    download_backup_parser.set_defaults(func=download_backup)


def download_backup(args):

    try:
        obj = Backup(args.ip, args.port)
        res = obj.download(args.name, args.filepath)
    except SOSError as e:
        common.format_err_msg_and_raise(
            'download', 'backup',
            e.err_text, e.err_code)
