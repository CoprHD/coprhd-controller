# Copyright (c)2016 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
import fileshare
import tag
import volume
import consistencygroup
import json
import time
from threading import Timer
from common import SOSError
from tenant import Tenant


class Schedulepolicy(object):
    # The class definition for operations on 'Schedulepolicy'.

    # Commonly used URIs for the 'Schedulepolicy' module
    URI_SNAPSHOT_SCHEDULE_POLICY_CREATE = '/tenants/{0}/schedule-policies'
    URI_SNAPSHOT_SCHEDULE_POLICY_SHOW = '/schedule-policies/{0}'
    URI_SNAPSHOT_SCHEDULE_POLICY_UPDATE = '/schedule-policies/{0}'
    
    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the ViPR instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def create(self, tenant, policyname, policytype, schedulefrequency, 
                                         schedulerepeat,scheduletime, scheddom, scheddow, expiretype,
                                         expirevalue):
        
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        tenant_uri = tenant_obj.tenant_query(tenant)
        
        schedule = dict()
        schedule = {
                    'schedule_frequency' : schedulefrequency,
                    'schedule_repeat' : schedulerepeat,
                    'schedule_time' : scheduletime
                    }
        
        if schedulefrequency == 'weeks':
            schedule['schedule_day_of_week'] = scheddow
        elif schedulefrequency == 'months':
            schedule['schedule_day_of_month'] = scheddom
            
        parms = {
                 'policy_type' : policytype,
                 'policy_name' : policyname}
        
        body = None
        parms['schedule'] = schedule
        
        snapshot_expire = dict()
        if expiretype or expirevalue :
            snapshot_expire = {
                           'expire_type' : expiretype,
                           'expire_value' : expirevalue}
            parms['snapshot_expire'] = snapshot_expire
        
        
        body = json.dumps(parms)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "POST",
            Schedulepolicy.URI_SNAPSHOT_SCHEDULE_POLICY_CREATE.format(
                tenant_uri),
            body)
        
        return
    
    def policy_list(self, tenantname):
        tenant_obj = Tenant(self.__ipAddr, self.__port)
        tenant_uri = tenant_obj.tenant_query(tenantname)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Schedulepolicy.URI_SNAPSHOT_SCHEDULE_POLICY_CREATE.format(
                tenant_uri),
            None)
        
        res = common.json_decode(s)
        return res['schedule_policy']
    
    def get_policy_from_name(self, policyname, tenantname):
        policylist = self.policy_list(tenantname)
        
        for policy in policylist:
            if (policy['name'] == policyname):
                return policy
            

        raise SOSError(
            SOSError.SOS_FAILURE_ERR,
            "policy with the name:" +
            policyname +
            " Not Found")

    def policy_show(self, policyname, tenantname):
        policy = self.get_policy_from_name(policyname, tenantname)
        policyid = policy['id']
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "GET",
            Schedulepolicy.URI_SNAPSHOT_SCHEDULE_POLICY_SHOW.format(
                policyid),
            None)
        
        res = common.json_decode(s)
        return res
    
    
    def policy_update(self, tenantname, policyname, policytype, schedulefrequency, 
                                         schedulerepeat,scheduletime, scheddom, scheddow, expiretype,
                                         expirevalue, newpolicyname):
        
        policy = self.policy_show(policyname, tenantname)
        policyid = policy['policy_id']
        
        parms = dict()    
        if newpolicyname:
            parms['policy_name'] = newpolicyname
        else :
            parms['policy_name'] = policy['policy_name']
        if policytype:
            parms['policy_type'] = policytype
        else :
            parms['policy_type'] = policy['policy_type']

        schedule = dict()
        if schedulefrequency:
            schedule['schedule_frequency'] = schedulefrequency
        else :
            schedule['schedule_frequency'] = policy['schedule_frequency']
        if schedulerepeat:
            schedule['schedule_repeat'] = schedulerepeat
        else :
            schedule['schedule_repeat'] = policy['schedule_repeat']
            
        if scheduletime:
            schedule['schedule_time'] = scheduletime
        else :
            schedule['schedule_time'] = policy['schedule_time'][:-3]

        if schedule['schedule_frequency'] == 'weeks':
            if scheddow :
                schedule['schedule_day_of_week'] = scheddow
            elif 'schedule_day_of_week' in policy:
                schedule['schedule_day_of_week'] = policy['schedule_day_of_week']
                
            
        elif schedule['schedule_frequency'] == 'months':
            if scheddom :
                schedule['schedule_day_of_month'] = scheddom
            elif 'schedule_day_of_month' in policy:
                schedule['schedule_day_of_month'] = policy['schedule_day_of_month']
 
        parms['schedule'] = schedule       
            
        snapshot_expire = dict()
        if expiretype:
            snapshot_expire['expire_type'] = expiretype
        elif ('snapshot_expire_type' in policy):
            snapshot_expire['expire_type'] = policy['snapshot_expire_type']
        if expirevalue:
            snapshot_expire['expire_value'] = expirevalue
        elif ('snapshot_expire_time' in policy):
            snapshot_expire['expire_value'] = policy['snapshot_expire_time']
        
        
        if ('expire_value' in snapshot_expire and 'expire_type' in snapshot_expire) :
            parms['snapshot_expire'] = snapshot_expire
        elif ('expire_value' in snapshot_expire or 'expire_type' in snapshot_expire):
            raise SOSError(
            SOSError.SOS_FAILURE_ERR,
            "Please provide both expire_value and expire_type")
            
        
        body = None

        body = json.dumps(parms)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "PUT",
            Schedulepolicy.URI_SNAPSHOT_SCHEDULE_POLICY_UPDATE.format(policyid),
            body)
        
        return
    
    
    def policy_delete(self, policyname, tenantname):
        policy = self.get_policy_from_name(policyname, tenantname)
        policyid = policy['id']
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port,
            "DELETE",
            Schedulepolicy.URI_SNAPSHOT_SCHEDULE_POLICY_SHOW.format(
                policyid),
            None)
        
        return


def create_parser(subcommand_parsers, common_parser):
    create_parser = subcommand_parsers.add_parser(
        'create',
        description='ViPR Schedule Policy Create CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Schedule Policy Create')
    mandatory_args = create_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-policyname', '-polname',
                                metavar='<policyname>',
                                dest='policyname',
                                help='Name of the policy',
                                required=True)
    mandatory_args.add_argument('-policytype', '-poltype',
                                metavar='<policytype>',
                                dest='policytype',
                                help='Type of the policy',
                                required=True)
    mandatory_args.add_argument('-schedulefrequency', '-schedfreq',
                                dest='schedulefrequency',
                                help='Schedule frequency of the policy',
                                choices=["days", "weeks", "months"],
                                required=True)
    mandatory_args.add_argument('-schedulerepeat', '-schedrep',
                                metavar='<schedulerepeat>',
                                dest='schedulerepeat',
                                type = int,
                                help='Schedule repeat for the policy',
                                required=True)
    mandatory_args.add_argument('-scheduletime', '-schedtim',
                                metavar='<scheduletime>',
                                dest='scheduletime',
                                help='Schedule time for the policy',
                                required=True)
    create_parser.add_argument('-scheduleDayOfWeek', '-scheddow',
                                metavar='<scheddow>',
                                dest='scheddow',
                                help='Scheduled day of week')
    create_parser.add_argument('-scheduleDayOfMonth', '-scheddom',
                                metavar='<scheddom>',
                                dest='scheddom',
                                help='Scheduled day of month ')
    create_parser.add_argument('-expiretype', '-exptype',
                                dest='expiretype',
                                choices=["hours", "days", "weeks", "months", "never"],
                                help='Snapshot Schedule expire type')
    create_parser.add_argument('-expirevalue', '-expval',
                                metavar='<expirevalue>',
                                dest='expirevalue',
                                type = int,
                                help='Snapshot Schedule expire value')
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    create_parser.set_defaults(func=create)


def create(args):
    obj = Schedulepolicy(args.ip, args.port)
    try:
        res = obj.create(args.tenant, args.policyname, args.policytype, args.schedulefrequency, 
                                         args.schedulerepeat, args.scheduletime, args.scheddom, args.scheddow, args.expiretype,
                                         args.expirevalue)
        return

    except SOSError as e:
        common.format_err_msg_and_raise("create", "schedule-policy",
                                        e.err_text, e.err_code)

def list_parser(subcommand_parsers, common_parser):
    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Schedule Policy list CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Schedule Policy list')
    mandatory_args = list_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    list_parser.set_defaults(func=policy_list)


def policy_list(args):
    obj = Schedulepolicy(args.ip, args.port)

    try:
        res = obj.policy_list(args.tenant)
        if res:
            from common import TableGenerator
            TableGenerator(res, ['name']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "schedule-policy",
                                        e.err_text, e.err_code)
        

def show_parser(subcommand_parsers, common_parser):
    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Schedule Policy show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Schedule Policy show')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    mandatory_args.add_argument('-policyname', '-polnm',
                               metavar='<policyname>',
                               dest='polname',
                               help='Name of policy',
                               required=True)
    show_parser.set_defaults(func=policy_show)


def policy_show(args):
    obj = Schedulepolicy(args.ip, args.port)

    try:
        res = obj.policy_show(args.polname, args.tenant)
        return common.format_json_object(res)

    except SOSError as e:
        common.format_err_msg_and_raise("show", "schedule-policy",
                                        e.err_text, e.err_code)
        
        
def update_parser(subcommand_parsers, common_parser):
    update_parser = subcommand_parsers.add_parser(
        'update',
        description='ViPR Schedule Policy Update CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Schedule Policy Update')
    mandatory_args = update_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-policyname', '-polname',
                                metavar='<policyname>',
                                dest='policyname',
                                help='Name of the policy',
                                required=True)
    update_parser.add_argument('-newpolicyname', '-newpolname',
                                metavar='<newpolicyname>',
                                dest='newpolicyname',
                                help='New name for the policy')
    update_parser.add_argument('-policytype', '-poltype',
                                metavar='<policytype>',
                                dest='policytype',
                                help='Type of the policy')
    update_parser.add_argument('-schedulefrequency', '-schedfreq',
                                dest='schedulefrequency',
                                help='Schedule frequency of the policy',
                                choices=["days", "weeks", "months"])
    update_parser.add_argument('-schedulerepeat', '-schedrep',
                                metavar='<schedulerepeat>',
                                dest='schedulerepeat',
                                type = int,
                                help='Schedule repeat for the policy')
    update_parser.add_argument('-scheduletime', '-schedtim',
                                metavar='<scheduletime>',
                                dest='scheduletime',
                                help='Schedule time for the policy')
    update_parser.add_argument('-scheduleDayOfWeek', '-scheddow',
                                metavar='<scheddow>',
                                dest='scheddow',
                                help='Scheduled day of week')
    update_parser.add_argument('-scheduleDayOfMonth', '-scheddom',
                                metavar='<scheddom>',
                                dest='scheddom',
                                help='Scheduled day of month ')
    update_parser.add_argument('-expiretype', '-exptype',
                                dest='expiretype',
                                choices=["hours", "days", "weeks", "months", "never"],
                                help='Snapshot Schedule expire type')
    update_parser.add_argument('-expirevalue', '-expval',
                                metavar='<expirevalue>',
                                dest='expirevalue',
                                type = int,
                                help='Snapshot Schedule expire value')
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    update_parser.set_defaults(func=policy_update)


def policy_update(args):
    obj = Schedulepolicy(args.ip, args.port)
    try:
        res = obj.policy_update(args.tenant, args.policyname, args.policytype, args.schedulefrequency, 
                                         args.schedulerepeat, args.scheduletime, args.scheddom, args.scheddow, args.expiretype,
                                         args.expirevalue, args.newpolicyname)
        return

    except SOSError as e:
        common.format_err_msg_and_raise("update", "schedule-policy",
                                        e.err_text, e.err_code)
        
        
def delete_parser(subcommand_parsers, common_parser):
    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Schedule Policy delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Schedule Policy delete')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-tenant', '-tn',
                               metavar='<tenantname>',
                               dest='tenant',
                               help='Name of tenant',
                               required=True)
    mandatory_args.add_argument('-policyname', '-polnm',
                               metavar='<policyname>',
                               dest='polname',
                               help='Name of policy',
                               required=True)
    delete_parser.set_defaults(func=policy_delete)


def policy_delete(args):
    obj = Schedulepolicy(args.ip, args.port)

    try:
        res = obj.policy_delete(args.polname, args.tenant)
        return

    except SOSError as e:
        common.format_err_msg_and_raise("delete", "schedule-policy",
                                        e.err_text, e.err_code)
        
        

#
# Schedulepolicy Main parser routine
#
def schedulepolicy_parser(parent_subparser, common_parser):
    # main project parser

    parser = parent_subparser.add_parser('schedule-policy',
                                         description='ViPR Schedule policy CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Operations on Schedule policy')
    subcommand_parsers = parser.add_subparsers(help='Use One Of Commands')
    
    #schedule policy create command parser
    create_parser(subcommand_parsers, common_parser)
    
    #schedule policy list command parser
    list_parser(subcommand_parsers, common_parser)
    
    #schedule policy show command parser
    show_parser(subcommand_parsers, common_parser)
    
    #schedule policy update command parser
    update_parser(subcommand_parsers, common_parser)
    
    #schedule policy delete command parser
    delete_parser(subcommand_parsers, common_parser)
    