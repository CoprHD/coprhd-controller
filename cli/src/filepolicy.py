#!/usr/bin/python
# -*- coding: utf-8 -*-

# Copyright (c) 2016-17 Dell EMC Technologies
# All Rights Reserved
#
# This software contains the intellectual property of Dell EMC Technologies
# or is licensed to Dell EMC Technologies from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of Dell EMC Technologies.

import common
import tag
import json
import socket
import commands
from project import Project
import fileshare
from common import SOSError
from threading import Timer
from virtualpool import VirtualPool
import schedulepolicy
import host


class FilePolicy(object):

    '''
    The class definition for operations on 'Filepolicy'.
    '''

    # Commonly used URIs for the 'Filepolicy' module

    URI_FILE_POLICIES = '/file/file-policies'
    URI_FILE_POLICY_SHOW = URI_FILE_POLICIES + '/{0}'
    URI_FILE_POLICY_DELETE = URI_FILE_POLICIES + '/{0}'
    URI_FILE_POLICY_UPDATE = URI_FILE_POLICIES + '/{0}'
    URI_FILE_POLICY_ASSIGN = URI_FILE_POLICIES + '/{0}/assign-policy'
    URI_FILE_POLICY_UNASSIGN = URI_FILE_POLICIES \
        + '/{0}/unassign-policy'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''

        self.__ipAddr = ipAddr
        self.__port = port

    # Filepolicy Query

    def filepolicy_query(self, name):

        policies = self.list_file_polices()

        for policy in policies:
            if policy['name'] == name:
		return policy

        raise SOSError(SOSError.NOT_FOUND_ERR, 'filepolicy ' + name
                       + ': not found')

    def list_file_polices(self):
        '''
        Makes REST API call and retrieves filepolicy 
        Parameters:
            
        Returns:
            List of FilePolicies UUIDs in JSON response payload
        '''

        uri = FilePolicy.URI_FILE_POLICIES

        (s, h) = common.service_json_request(self.__ipAddr,
                self.__port, 'GET', uri, None)
        o = common.json_decode(s)
        returnlst = []

        if 'file_policy' in o:
            return common.get_list(o, 'file_policy')
        return returnlst

    def filepolicy_show(self, label, xml=False):

        filepolicy = self.filepolicy_query(label)
	return self.filepolicy_show_by_uri(filepolicy['id'], xml)
        
    def filepolicy_show_by_uri(self, uri, xml=False):

        if xml:
            (s, h) = common.service_json_request(
                self.__ipAddr,
                self.__port,
                'GET',
                FilePolicy.URI_FILE_POLICY_SHOW.format(uri),
                None,
                None,
                xml,
                )
            return s

        (s, h) = common.service_json_request(self.__ipAddr,
                self.__port, 'GET',
                FilePolicy.URI_FILE_POLICY_SHOW.format(uri), None)

        o = common.json_decode(s)
        inactive = common.get_node_value(o, 'inactive')

        if inactive == True:
            return None
        return o

    def filepolicy_delete(self, label):
        '''
        Deletes a filepolicy based on policy name
        Parameters:
            name: name of filepolicy
        '''

	filepolicy = self.filepolicy_query(label)
	(s, h) = common.service_json_request(self.__ipAddr,
                self.__port, "DELETE",
                FilePolicy.URI_FILE_POLICY_DELETE.format(filepolicy['id']), None)
        return

    def filepolicy_create(
        self,
        name,
        type,
        tenants_access,
        description,
        priority,
        num_worker_threads,
        policyschedulefrequency,
        policyschedulerepeat,
        policyscheduletime,
        policyscheduleweek,
        policyschedulemonth,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue,
        applyat,
        ):
        '''
        Creates a filepolicy based on policy name
        parameters:
        type policy type 
        tenants_access tenants access flag,
        description policy description,
        priority priority of the policys,
        policyschedulefrequency sType of schedule frequency e.g days, week or months,
        policyschedulerepeat policy run on every,
        policyscheduletime Time when policy run,
        policyscheduleweek day of week when policy run,
        policyschedulemonth day of month when policy run,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue
        '''

        create_request = {}
        policy_schedule = {}
        snapshot_params = {}
        replication_params = {}
        snapshot_expire_params = {}

        create_request['policy_type'] = type
        create_request['policy_name'] = name
        create_request['policy_description'] = description
        create_request['is_access_to_tenants'] = tenants_access
        create_request['apply_at'] = applyat

        policy_schedule['schedule_frequency'] = policyschedulefrequency
        policy_schedule['schedule_repeat'] = policyschedulerepeat
        policy_schedule['schedule_time'] = policyscheduletime
        policy_schedule['schedule_day_of_week'] = policyscheduleweek
        policy_schedule['schedule_day_of_month'] = policyschedulemonth

        if type == 'file_replication':
            replication_params['replication_type'] = replicationtype
            replication_params['replication_copy_mode'] = 'ASYNC'
            replication_params['policy_schedule'] = policy_schedule
            create_request['priority'] = priority
            create_request['num_worker_threads'] = num_worker_threads
            create_request['replication_params'] = replication_params
        elif type == 'file_snapshot':
            snapshot_expire_params['expire_type'] = snapshotexpiretype
            snapshot_expire_params['expire_value'] = snapshotexpirevalue
            if (snapshotnamepattern is None):
                raise SOSError(SOSError.VALUE_ERR,"File policy create error:"+ "Snapshotshot name pattern should be provided")
            snapshot_params['snapshot_name_pattern'] = snapshotnamepattern
            snapshot_params['snapshot_expire_params'] = snapshot_expire_params
            snapshot_params['policy_schedule'] = policy_schedule
            create_request['snapshot_params'] = snapshot_params

        try:
            body = json.dumps(create_request)
            (s, h) = common.service_json_request(self.__ipAddr,
                    self.__port, 'POST', FilePolicy.URI_FILE_POLICIES,
                    body)
            if not s:
                return None
            o = common.json_decode(s)
	    return o
        except SOSError, e:
            errorMessage = str(e)
        common.format_err_msg_and_raise('create', 'filepolicy',
                errorMessage, e.err_code)

    def filepolicy_update(
        self,
        label,
        name,
        description,
        priority,
        num_worker_threads,
        policyschedulefrequency,
        policyschedulerepeat,
        policyscheduletime,
        policyscheduleweek,
        policyschedulemonth,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue,
        applyat,
        ):
        '''
        Creates a filepolicy based on policy name
        parameters:
        tenants_access tenants access flag,
        description policy description,
        priority priority of the policys,
        policyschedulefrequency sType of schedule frequency e.g days, week or months,
        policyschedulerepeat policy run on every,
        policyscheduletime Time when policy run,
        policyscheduleweek day of week when policy run,
        policyschedulemonth day of month when policy run,
        replicationcopymode,
        replicationtype,
        snapshotnamepattern,
        snapshotexpiretype,
        snapshotexpirevalue
        '''

        filepolicy = self.filepolicy_query(label)

        (s, h) = common.service_json_request(
                self.__ipAddr,
                self.__port,
                'GET',
                FilePolicy.URI_FILE_POLICY_SHOW.format(filepolicy['id']),
                None,
                None,
                False,
                )
        o = common.json_decode(s)
        pol_type = common.get_node_value(o,"type")
        update_request = {}
        if name is not None:
            update_request['policy_name'] = name
        if description is not None:
            update_request['policy_description'] = description
        if priority is not None:
            update_request['priority'] = priority
        if applyat is not None:
            update_request['apply_at'] = applyat

        policy_schedule = {}
        snapshot_params = {}
        replication_params = {}
        snapshot_expire_params = {}

        if policyschedulefrequency is not None:
            policy_schedule['schedule_frequency'] = policyschedulefrequency
        if policyschedulerepeat is not None:
            policy_schedule['schedule_repeat'] = policyschedulerepeat
        if policyscheduletime is not None:
            policy_schedule['schedule_time'] = policyscheduletime
        if policyscheduleweek is not None:
            policy_schedule['schedule_day_of_week'] = policyscheduleweek
        if policyschedulemonth is not None:
            policy_schedule['schedule_day_of_month'] = policyschedulemonth

        if pol_type == 'file_replication':
            if replicationtype is not None:
                replication_params['replication_type'] = replicationtype
            if policy_schedule is not None and (len(policy_schedule) >0):
                replication_params['policy_schedule'] = policy_schedule
            if priority is not None:
                update_request['priority'] = priority
            if num_worker_threads is not None:
                update_request['num_worker_threads'] = num_worker_threads
            if replication_params is not None and (len(replication_params) >0):
                replication_params['replication_copy_mode'] = 'ASYNC'
                update_request['replication_params'] = replication_params
        elif pol_type == 'file_snapshot':
            if snapshotexpiretype is not None:
                snapshot_expire_params['expire_type'] = snapshotexpiretype
            if snapshotexpirevalue is not None:
                snapshot_expire_params['expire_value'] = snapshotexpirevalue
            if snapshotnamepattern is not None:
                snapshot_params['snapshot_name_pattern'] = snapshotnamepattern
            if snapshot_expire_params is not None and (len(snapshot_expire_params) >0):
                snapshot_params['snapshot_expire_params'] = snapshot_expire_params
            if policy_schedule is not None and (len(policy_schedule) >0):
                snapshot_params['policy_schedule'] = policy_schedule
            if snapshot_params is not None and (len(snapshot_params) >0):
                update_request['snapshot_params'] = snapshot_params

        try:
            body = json.dumps(update_request)
            (s, h) = common.service_json_request(self.__ipAddr,
                    self.__port, 'PUT',
                    FilePolicy.URI_FILE_POLICY_UPDATE.format(filepolicy['id']),
                    body)
            if not s:
                return None
            o = common.json_decode(s)
            return o
        except SOSError, e:
            errorMessage = str(e)
        if common.is_uri(filepolicy['id']):
            errorMessage = str(e).replace(filepolicy['id'], label)
        common.format_err_msg_and_raise('update', 'filepolicy',
                errorMessage, e.err_code)

    def filepolicy_assign(
        self,
        name,
        assign_to_vpools,
        project_assign_vpool,
        assign_to_projects,
        source_varray,
        target_varrays,
        ):

        filepolicy = self.filepolicy_query(name)
        (s, h) = common.service_json_request(
                self.__ipAddr,
                self.__port,
                'GET',
                FilePolicy.URI_FILE_POLICY_SHOW.format(filepolicy['id']),
                None,
                None,
                False,
                )
	o = common.json_decode(s)
	appliedat = common.get_node_value(o,"applied_at")
	pol_type = common.get_node_value(o,"type")
	assign_request = {}

	if ( appliedat  == "vpool"):
		vpool_assign_param = {}
		assign_request_vpools = []
		if assign_to_vpools is None:
           		raise SOSError(SOSError.VALUE_ERR,"File policyassign error:"+ "Vpool(assign_to_vpools) value should be provided")
		elif( len(assign_to_vpools)>1):
            		vpool_names = assign_to_vpools.split(',')
            		vpool_obj = VirtualPool(self.__ipAddr, self.__port)
			for name in vpool_names:
                 		uri = vpool_obj.vpool_query(name, 'file')
                 		assign_request_vpools.append(uri)
        	elif( assign_to_vpools is not None ):
            		uri = vpool_obj.vpool_query(assign_to_vpools, 'file')
            		assign_request_vpools.append(uri)
        	vpool_assign_param['assign_to_vpools'] = assign_request_vpools
		assign_request['vpool_assign_param'] = vpool_assign_param
	elif ( appliedat == "project"):
		project_assign_param = {}
		assign_request_projects = []
		assign_request_project_vpools = []
		project_obj = Project(self.__ipAddr, self.__port)
		if assign_to_projects is None or project_assign_vpool is None:
                        raise SOSError(SOSError.VALUE_ERR,"File policyassign error:"+ "Vpool (project_assign_vpool) and project (assign_to_projects) value should be provided")
                
		if( len(assign_to_projects)>1):
            		project_names = assign_to_projects.split(',')
            		for name in project_names:
                 		uri = project_obj.project_query(name) 
		 		assign_request_projects.append(uri)
        	else:
            		uri = project_obj.project_query(assign_to_projects)
            		assign_request_projects.append(uri)
	
		vpool_obj = VirtualPool(self.__ipAddr, self.__port)
            	uri = vpool_obj.vpool_query(project_assign_vpool, 'file')
            	project_assign_param['vpool'] = uri
        	project_assign_param['assign_to_projects'] = assign_request_projects
		assign_request['project_assign_param'] = project_assign_param

	if (pol_type == "file_replication"):
		if (source_varray is not None and target_varrays is not None):
			file_replication_topologies = []
	        	file_replication_topology = {}
			assign_target_varrays = []
			from virtualarray import VirtualArray
                	varray_obj = VirtualArray(self.__ipAddr, self.__port)
	                src_varray_uri = varray_obj.varray_query(source_varray)
		        file_replication_topology['source_varray']= src_varray_uri
	
		        if( len(target_varrays)>1):
				trg_varrays= target_varrays.split(',')
				for varray in trg_varrays:
					uri =  varray_obj.varray_query(varray)
					assign_target_varrays.append(uri)
			else:
				uri = varray_obj.varray_query(target_varrays)
				assign_target_varrays.append(uri)
		
			file_replication_topology['target_varrays']= assign_target_varrays
			file_replication_topologies.append(file_replication_topology)
			assign_request['file_replication_topologies']= file_replication_topologies
		else:
			raise SOSError(SOSError.VALUE_ERR, "File policyassign error:"+ "Target and source virtual array should be provided")

		

        try:
            body = json.dumps(assign_request)
            (s, h) = common.service_json_request(self.__ipAddr,
                    self.__port, 'POST',
                    FilePolicy.URI_FILE_POLICY_ASSIGN.format(filepolicy['id']),
                    body)
            if not s:
                return None
            o = common.json_decode(s)
            return o
        except SOSError, e:
            errorMessage = str(e)
        if common.is_uri(filepolicy['id']):
            errorMessage = str(e).replace(filepolicy['id'], name)
        common.format_err_msg_and_raise('assign', 'filepolicy',
                errorMessage, e.err_code)

    def filepolicy_unassign(
        self,
        name,
        unassign_resource_type,
        unassign_from_vpools,
        unassign_from_projects,
        unassign_from_filesystem,
        tenant,
        project
        ):

        filepolicy = self.filepolicy_query(name)
        unassign_request = {}

        projects_uris = []
        vpools_uris = []
        
        if unassign_resource_type == 'vpools':
            if unassign_from_vpools is None :
                raise SOSError(SOSError.VALUE_ERR,"File policy unassign error:"+ "Vpools value should be provided")
            
            vpool_obj = VirtualPool(self.__ipAddr, self.__port)
            if( len(unassign_from_vpools) > 1 ):
                vpools = unassign_from_vpools.split(',')
                for vpool in vpools:
                    uri = vpool_obj.vpool_query(vpool, 'file')
                    vpools_uris.append(uri)
            else :
                uri = vpool_obj.vpool_query(unassign_from_vpools, 'file')
                vpools_uris.append(uri)
            unassign_request['unassign_from'] = vpools_uris
        elif unassign_resource_type == 'projects':
            if unassign_from_projects is None :
                raise SOSError(SOSError.VALUE_ERR,"File policy unassign error:"+ "Project value should be provided")
            
            project_obj = Project(self.__ipAddr, self.__port)
            if( len(unassign_from_projects) > 1):
                projects = unassign_from_projects.split(',')
                for project in projects:
                    uri = project_obj.project_query(project)
                    projects_uris.append(uri)
            else :
                uri = project_obj.project_query(unassign_from_projects)
                projects_uris.append(uri)
            unassign_request['unassign_from'] = projects_uris
        elif unassign_resource_type == 'filesystem':
	    filesystem_uris = []
            if unassign_from_filesystem is None or project is None :
                raise SOSError(SOSError.VALUE_ERR,"File policy unassign error:"+ "Filesystem and project value should be provided")
            
            fs_obj = fileshare.Fileshare(self.__ipAddr, self.__port)
            resourcepath = "/" + project + "/"
            if(tenant is not None):
                resourcepath = tenant + resourcepath
            if( len(unassign_from_filesystem) > 1):
                filesystems = unassign_from_filesystem.split(',')
                for filesystem in filesystems:
                    uri = fs_obj.fileshare_query(resourcepath + filesystem)
                    filesystem_uris.append(uri)
            else :
                uri = fs_obj.fileshare_query(resourcepath + filesystem)
                filesystem_uris.append(uri)
                
            unassign_request['unassign_from'] = filesystem_uris
            
	
        try:
            body = json.dumps(unassign_request)
            (s, h) = common.service_json_request(self.__ipAddr,
                    self.__port, 'POST',
                    FilePolicy.URI_FILE_POLICY_UNASSIGN.format(filepolicy['id']),
                    body)
            if not s:
                return None
            o = common.json_decode(s)
	    return o
        except SOSError, e:
            errorMessage = str(e)
        if common.is_uri(filepolicy['id']):
            errorMessage = str(e).replace(filepolicy['id'], name)
        common.format_err_msg_and_raise('unassign', 'filepolicy',
                errorMessage, e.err_code)


def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser('list',
            description='ViPR Filepolicy List CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='List filepolicies')
    mandatory_args = \
        list_parser.add_argument_group('mandatory arguments')

    list_parser.add_argument('-v', '-verbose',
                             dest='verbose',
                             help='List FilePolicy with details',
                             action='store_true')
    list_parser.add_argument('-l', '-long',
                             action='store_true',
                             help='List Filepolicy with details in table format',
                             dest='long')
    list_parser.set_defaults(func=filepolicy_list)


def filepolicy_list(args):
    obj = FilePolicy(args.ip, args.port)
    try:
	from common import TableGenerator
	filepolicies = obj.list_file_polices()
	records = []
	for filepolicy in filepolicies:
	    filepolicy_uri = filepolicy['id']
	    filepolicy_detail = obj.filepolicy_show_by_uri(filepolicy_uri)
	    if (filepolicy_detail) :
		records.append(filepolicy_detail)
	
	if(len(records) > 0):
	    if(args.verbose is True):
	        return common.format_json_object(records)
	    if(args.long is True):
	        TableGenerator(records, ['name', 'type', 'description','applied_at']).printTable()
	    else:
	        TableGenerator(records, ['name']).printTable()
	else:
            return

    except SOSError as e:
        raise e

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser('show',
            description='ViPR Filepolicy Show CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='show filepolicies')

    mandatory_args = \
        show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-n',
        '-name',
        metavar='<name>',
        dest='name',
        help='Name of Filepolicy',
        required=True,
        )

    show_parser.add_argument('-xml', dest='xml', action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=filepolicy_show)


def filepolicy_show(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        res = obj.filepolicy_show(args.name, args.xml)
        if args.xml:
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError, e:
        common.format_err_msg_and_raise('show', 'filepolicy',
                e.err_text, e.err_code)


def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser('create',
            description='ViPR FilePolicy Create CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='Create a filepolicy')
    mandatory_args = \
        create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name',
        '-n',
        metavar='<policy_name>',
        dest='name',
        help='Name of the policy',
        required=True,
        )
    mandatory_args.add_argument(
        '-type',
        '-t',
        metavar='<policy_type>',
        dest='policy_type',
        help='Type of the policy, valid values are : file_snapshot, file_replication, file_quota'
            ,
        choices=['file_snapshot', 'file_replication', 'file_quota'],
        required=True,
        )
    mandatory_args.add_argument(
        '-tenants_access',
        '-ta',
        metavar='<is_access_to_tenants>',
        dest='tenants_access',
        help='Tenants access',
        required=False,
        )
    create_parser.add_argument('-apply_at','-aplat',
                               metavar='<apply_at>',
                               dest='apply_at',
                               help='Level at which policy has to applied. Valid values are vpool, project, file_system. Default: vpool',
                               default ='vpool',)
    create_parser.add_argument('-description', '-dc',
                               metavar='<policy_description>',
                               dest='description',
                               help='Policy Description')
    create_parser.add_argument('-priority', '-pr', metavar='<priority>'
                               , dest='priority',
                               help='Priority of the policy. Valid values are: High, Normal',
                               choices=['High', 'Normal'])
    create_parser.add_argument('-num_worker_threads','-wt',
                               metavar='<num_worker_threads>',
                               dest='num_worker_threads',
                               help = 'Number of worker threads range:3-10, Default: 3',
                               choices = xrange(3,10), default = 3,)
    create_parser.add_argument('-policyscheduleweek', '-plscwk',
                               metavar='<policy_schedule_week>',
                               dest='policy_schedule_week',
                               help='Day of week when policy run')
    create_parser.add_argument('-policyschedulemonth', '-plscmn',
                               metavar='<policy_schedule_month>',
                               dest='policy_schedule_month',
                               help='Day of month when policy run')
    create_parser.add_argument('-snapshotnamepattern', '-snpnmptrn',
                               metavar='<snapshot_name_pattern>',
                               dest='snapshot_name_pattern',
                               help='Snapshot pattern ')
    create_parser.add_argument('-snapshotexpiretype','-snpexptp',
                               metavar='<snapshot_expire_type>',
                               dest='snapshot_expire_type',
                               help='Snapshot expire type e.g hours, days, weeks, months or never. Default: days',
                               choices=['hours', 'days', 'weeks', 'months', 'never'],
                               default ='days')
    create_parser.add_argument('-snapshotexpirevalue', '-snpexpvl',
                               metavar='<snapshot_expire_value>',
                               dest='snapshot_expire_value',
                               help='Snapshot expire after this value. Default: 2',
                               default = 2)
    create_parser.add_argument('-policyschedulefrequency','-plscfr',
                               metavar='<policy_schedule_frequency>',
                               dest='policy_sched_frequnecy',
                               help='Type of schedule frequency e.g minutes, hours, days, weeks or months. Default: days',
                               default = 'days',)
    create_parser.add_argument('-policyschedulerepeat','-plscrp',
        		               metavar='<policy_schedule_repeat>',
        		               dest='policy_schedule_repeat',
                		       help='Policy run on every. Default: 1',
                		       default = 1,)
    create_parser.add_argument('-policyscheduletime','-plsctm',
        		               metavar='<policy_schedule_time>',
        		               dest='policy_schedule_time',
                		       help='Time when policy run. Default: 00:00',
               			       default='00:00',)
    create_parser.add_argument('-replicationtype','-reptype',
        		               metavar='<replication_type>',
        		               dest='replication_type',
                		       help='File Replication type Valid values are: LOCAL, REMOTE. Default: REMOTE',
                		       choices=['LOCAL', 'REMOTE'],
                		       default = 'REMOTE',)
    create_parser.set_defaults(func=filepolicy_create)


def filepolicy_create(args):
    obj = FilePolicy(args.ip, args.port)
    SYNC = 'SYNC'
    ASYNC = 'ASYNC'
    if args:
        try:
            obj.filepolicy_create(
                args.name,
                args.policy_type,
                args.tenants_access,
                args.description,
                args.priority,
                args.num_worker_threads,
                args.policy_sched_frequnecy,
                args.policy_schedule_repeat,
                args.policy_schedule_time,
                args.policy_schedule_week,
                args.policy_schedule_month,
                args.replication_type,
                args.snapshot_name_pattern,
                args.snapshot_expire_type,
                args.snapshot_expire_value,
                args.apply_at,
                )
        except SOSError, e:

            common.format_err_msg_and_raise('create', 'filepolicy',
                    e.err_text, e.err_code)


def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('update',
            description='ViPR FilePolicy Update CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='Update a filepolicy')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-existingpolname',
        '-epn',
        metavar='<existing_policy_name>',
        dest='label',
        help='Name of the existing policy to be changed',
        required=True,
        )
    update_parser.add_argument('-name','-n',
                               metavar='<policy_name>',
                               dest='name',
                               help='Name of the policy')
    update_parser.add_argument('-apply_at','-aplat',
                               metavar='<apply_at>',
                               dest='apply_at',
                               help='Level at which policy has to applied. Valid values are vpool, project, file_system')
    update_parser.add_argument('-description', '-dc',
                               metavar='<policy_description>',
                               dest='description',
                               help='Policy Description')
    update_parser.add_argument('-priority', '-pr', metavar='<priority>'
                               , dest='priority',
                               help='Priority of the policy. Valid value: Normal, High',
                               choices = ['Normal','High'])
    update_parser.add_argument('-num_worker_threads','-wt',
                               metavar='<num_worker_threads>',
                               dest='num_worker_threads',
                               help = 'Number of worker threads',
                               choices = xrange(3,10))
    update_parser.add_argument('-policyscheduleweek', '-plscwk',
                               metavar='<policy_schedule_week>',
                               dest='policy_schedule_week',
                               help='Day of week when policy run')
    update_parser.add_argument('-policyschedulemonth', '-plscmn',
                               metavar='<policy_schedule_month>',
                               dest='policy_schedule_month',
                               help='Day of month when policy run')
    update_parser.add_argument('-snapshotnamepattern', '-snpnmptrn',
                               metavar='<snapshot_name_pattern>',
                               dest='snapshot_name_pattern',
                               help='Snapshot pattern ')
    update_parser.add_argument('-snapshotexpiretype','-snpexptp',
                               metavar='<snapshot_expire_type>',
                               dest='snapshot_expire_type',
                               help='Snapshot expire type e.g hours, days, weeks, months or never',
                               choices=['hours', 'days', 'weeks', 'months', 'never'])
    update_parser.add_argument('-snapshotexpirevalue', '-snpexpvl',
                               metavar='<snapshot_expire_value>',
                               dest='snapshot_expire_value',
                               help='Snapshot expire after this value')
    update_parser.add_argument('-policyschedulefrequency','-plscfr',
                               metavar='<policy_schedule_frequency>',
                               dest='policy_sched_frequnecy',
                               help='Type of schedule frequency e.g minutes, hours, days, weeks or months')
    update_parser.add_argument('-policyschedulerepeat','-plscrp',
                               metavar='<policy_schedule_repeat>',
                               dest='policy_schedule_repeat',
                               help='Policy run on every')
    update_parser.add_argument('-policyscheduletime','-plsctm',
                               metavar='<policy_schedule_time>',
                               dest='policy_schedule_time',
                               help='Time when policy run')
    update_parser.add_argument('-replicationtype','-reptype',
                               metavar='<replication_type>',
                               dest='replication_type',
                               help='File Replication type Valid values are: LOCAL, REMOTE',
                               choices=['LOCAL', 'REMOTE'])
    update_parser.set_defaults(func=filepolicy_update)


def filepolicy_update(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        obj.filepolicy_update(
                args.label,
                args.name,
                args.description,
                args.priority,
                args.num_worker_threads,
                args.policy_sched_frequnecy,
                args.policy_schedule_repeat,
                args.policy_schedule_time,
                args.policy_schedule_week,
                args.policy_schedule_month,
                args.replication_type,
                args.snapshot_name_pattern,
                args.snapshot_expire_type,
                args.snapshot_expire_value,
                args.apply_at,
            )
    except SOSError, e:

        common.format_err_msg_and_raise('update', 'filepolicy',
                e.err_text, e.err_code)


# FilePolicy Delete routines

def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser('delete',
            description='ViPR Filesystem Delete CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='Delete a filepolicy')
    mandatory_args = \
        delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name',
        '-n',
        metavar='<policy-name>',
        dest='name',
        help='Name of FilePolicy',
        required=True,
        )
    delete_parser.set_defaults(func=filepolicy_delete)


def filepolicy_delete(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        if not args.name:
            args.name = ''
        obj.filepolicy_delete(args.name)
    except SOSError, e:

        if e.err_code == SOSError.NOT_FOUND_ERR:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           'FilePolicy delete failed: ' + e.err_text)
        else:
            raise e


# FilePolicy assign

def assign_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('assign',
            description='ViPR FilePolicy assign CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='Assign FilePolicy to vpool, project')
    mandatory_args = \
        update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name',
        '-n',
        metavar='<policy_name>',
        dest='name',
        help='Name of the policy',
        required=True,
        )
    update_parser.add_argument('-assigntovpools', '-asignvpls',
                		       metavar='<assign_to_vpools>',
               			       dest='assign_to_vpools',
                		       help='assign to vpools. Required for assigning file policies to vpool')
    update_parser.add_argument('-assigntoprojects', '-asignprjs',
                               metavar='<assign_to_projects>',
                               dest='assign_to_projects',
                               help='Assign to projects. Required for assigning file policies to project' )
    update_parser.add_argument('-assigntoprojectsvpool',
                               '-asignprjvpool',
                               metavar='<project_assign_vpool>',
                               dest='project_assign_vpool',
                               help='vpool of to-be asssigned projects. Required for assigning file policies to project ')
    update_parser.add_argument('-sourcevarray', '-srcvarray',
                               metavar='<source_varray>',
                               dest='source_varray',
                               help='source varray for file replication')
    update_parser.add_argument('-targetvarrays', '-trgvarrays',
                               metavar='<target_varrays>',
                               dest='target_varrays',
                               help='target varrays for file replication')
    update_parser.set_defaults(func=filepolicy_assign)


def filepolicy_assign(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        if not args.name:
            args.name = ''
        obj.filepolicy_assign(
            args.name,
            args.assign_to_vpools,
            args.project_assign_vpool,
            args.assign_to_projects,
            args.source_varray,
            args.target_varrays,
            )
    except SOSError, e:
        if e.err_code == SOSError.NOT_FOUND_ERR:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           'FilePolicy assign failed: ' + e.err_text)
        else:
            raise e


# FilePolicy unassign

def unassign_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser('unassign',
            description='ViPR FilePolicy unassign CLI usage.',
            parents=[common_parser], conflict_handler='resolve',
            help='Unassign FilePolicy from vpool, project, filesystem')
    mandatory_args = \
        update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument(
        '-name',
        '-n',
        metavar='<policy_name>',
        dest='name',
        help='Name of the policy',
        required=True,
        )
    mandatory_args.add_argument(
        '-unassignresourcetype',
        '-unasngrestp',
        metavar='<unassign_resource_type>',
        dest='unassign_resource_type',
        help='Resource type to be unassigned from. type values : vpools or projects or filesystem',
        choices=['vpools', 'projects', 'filesystem'],
        required=True,
        )
    update_parser.add_argument('-unassignvpools', '-unasignvpls',
                               metavar='<unassign_from_vpools>',
                               dest='unassign_from_vpools',
                               help='unassign from vpools')
    update_parser.add_argument('-unassignfromprojects', '-unasignprjs',
                               metavar='<unassign_from_projects>',
                               dest='unassign_from_projects',
                               help='unassign from projects')
    update_parser.add_argument('-unassignfromfs', '-unasignfs',
                               metavar='<unassign_from_filesystem>',
                               dest='unassign_from_filesystem',
                               help='unassign from filesystem')
    update_parser.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant')
    update_parser.add_argument('-project', '-pr',
                               metavar='<projectname>',
                               dest='project',
                               help='Name of Project')

    update_parser.set_defaults(func=filepolicy_unassign)


def filepolicy_unassign(args):
    obj = FilePolicy(args.ip, args.port)
    try:
        if not args.name:
            args.name = ''
        obj.filepolicy_unassign(args.name, args.unassign_resource_type,
                                args.unassign_from_vpools,
                                args.unassign_from_projects, args.unassign_from_filesystem, args.tenant, args.project)
    except SOSError, e:
        if e.err_code == SOSError.NOT_FOUND_ERR:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           'FilePolicy assign failed: ' + e.err_text)
        else:
            raise e


#
# FilePolicy Main parser routine
#

def filepolicy_parser(parent_subparser, common_parser):

    # main project parser

    parser = parent_subparser.add_parser('filepolicy',
            description='ViPR filepolicy CLI usage',
            parents=[common_parser], conflict_handler='resolve',
            help='Operations on filepolicy')
    subcommand_parsers = \
        parser.add_subparsers(help='Use one of subcommands')

     # list command parser

    list_parser(subcommand_parsers, common_parser)

    # show command parser

    show_parser(subcommand_parsers, common_parser)

    # create command parser

    create_parser(subcommand_parsers, common_parser)

    # update command parser

    update_parser(subcommand_parsers, common_parser)

    # delete command parser

    delete_parser(subcommand_parsers, common_parser)

    # policy assign command parser

    assign_parser(subcommand_parsers, common_parser)

    # policy unassign command parser

    unassign_parser(subcommand_parsers, common_parser)
