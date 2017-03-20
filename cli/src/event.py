#!/usr/bin/python

# Copyright (c) 2016 EMC Corporation
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
from tenant import Tenant
from common import SOSError
from tenant import Tenant
from common import TableGenerator


class Event(object):

    '''
    The class definition for operations on 'Event'.
    '''
    URI_SERVICES_BASE = ''
    URI_EVENTS = URI_SERVICES_BASE + '/vdc/events?tenant={0}'
    URI_EVENT = URI_SERVICES_BASE + '/vdc/events/{0}'
    URI_EVENT_APPROVE = URI_EVENT + '/approve'
    URI_EVENT_DECLINE = URI_EVENT + '/decline'
    URI_EVENT_DETAILS = URI_EVENT + '/details'

    URI_RESOURCE_DEACTIVATE = '{0}/deactivate'

    BOOL_TYPE_LIST = ['true', 'false']

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance. These are
        needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

       
    def event_list(self, tenant):
        uri = Tenant(self.__ipAddr, self.__port).tenant_query(tenant)

        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "GET",
            Event.URI_EVENTS.format(uri), None)
        o = common.json_decode(s)
        return o['event']


    def event_show(self, uri, xml=False):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Event.URI_EVENT.format(uri),
                                             None, None, xml)

        if(not xml):
            o = common.json_decode(s)
            if('inactive' in o):
                if(o['inactive']):
                    return None
        else:
            return s
        return o


    def event_show_uri(self, uri):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Event.URI_EVENT.format(uri),
                                             None, None, False)
        o = common.json_decode(s)
        if(o['inactive'] is False):
            return o

        return None


    def event_delete(self, uri):
        formaturi = self.URI_RESOURCE_DEACTIVATE.format(
            Event.URI_EVENT.format(uri))
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", formaturi,
            None)
        return

    def event_approve(self, uri):
        formaturi = self.URI_EVENT_APPROVE.format(uri)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", formaturi,
            None)
        return

    def event_decline(self, uri):
        formaturi = self.URI_EVENT_DECLINE.format(uri)
        
        (s, h) = common.service_json_request(
            self.__ipAddr, self.__port, "POST", formaturi,
            None)
        return

    def event_details(self, uri):
        (s, h) = common.service_json_request(self.__ipAddr, self.__port, "GET",
                                             Event.URI_EVENT_DETAILS.format(uri),
                                             None, None, False)
        return common.json_decode(s)


def event_delete(args):
    obj = Event(args.ip, args.port)
    try:
        obj.event_delete(args.uri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise("delete", "event",
                                        e.err_text, e.err_code)

def event_approve(args):
    obj = Event(args.ip, args.port)
    try:
        obj.event_approve(args.uri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise("approve", "event",
                                        e.err_text, e.err_code)

def event_decline(args):
    obj = Event(args.ip, args.port)
    try:
        obj.event_decline(args.uri)
        return
    except SOSError as e:
        common.format_err_msg_and_raise("decline", "event",
                                        e.err_text, e.err_code)

def event_show(args):
    obj = Event(args.ip, args.port)
    try:
        res = obj.event_show(args.uri, args.xml)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("show", "event",
                                        e.err_text, e.err_code)

def event_details(args):
    obj = Event(args.ip, args.port)
    try:
        res = obj.event_details(args.uri)

        if(args.xml):
            return common.format_xml(res)

        return common.format_json_object(res)
    except SOSError as e:
        common.format_err_msg_and_raise("details", "event",
                                        e.err_text, e.err_code)
def event_list(args):
    obj = Event(args.ip, args.port)
    try:
        events = obj.event_list(args.tenant)
        output = []
        for event_uri in events:
            clobj = obj.event_show_uri(event_uri['id'])
            if(clobj):
		# Sometimes resource/name isn't filled in, and that trips up the table generator
		if (clobj['resource']['name'] == ""):
		    clobj['resource']['name'] = "No resource specified"
                clobj['resource_name'] = clobj['resource']['name']
                output.append(clobj)

        if(len(output) > 0):
	    from common import TableGenerator
            TableGenerator(output, ['module/id', 'event_status', 'resource_name', 'module/name', 'warning']).printTable()

    except SOSError as e:
        common.format_err_msg_and_raise("list", "event",
                                        e.err_text, e.err_code)

def show_parser(subcommand_parsers, common_parser):

    show_parser = subcommand_parsers.add_parser(
        'show',
        description='ViPR Event Show CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an Event')
    mandatory_args = show_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-uri', '-i',
                                metavar='<uri>',
                                dest='uri',
                                help='uri of an event',
                                required=True)
    show_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    show_parser.set_defaults(func=event_show)

def details_parser(subcommand_parsers, common_parser):

    details_parser = subcommand_parsers.add_parser(
        'details',
        description='ViPR Event Details CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Show an Event Details')
    mandatory_args = details_parser.add_argument_group('mandatory arguments')

    mandatory_args.add_argument('-uri', '-i',
                                metavar='<uri>',
                                dest='uri',
                                help='uri of an event',
                                required=True)
    details_parser.add_argument('-xml',
                             dest='xml',
                             action='store_true',
                             help='XML response')

    details_parser.set_defaults(func=event_details)

def list_parser(subcommand_parsers, common_parser):

    list_parser = subcommand_parsers.add_parser(
        'list',
        description='ViPR Event List CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='List of events')

    list_parser.add_argument('-tenant', '-tn',
                             help='Name of Tenant',
                             metavar='<tenant>',
                             dest='tenant',
                             default=None)

    list_parser.set_defaults(func=event_list)

def delete_parser(subcommand_parsers, common_parser):

    delete_parser = subcommand_parsers.add_parser(
        'delete',
        description='ViPR Event Delete CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Delete a Event')
    mandatory_args = delete_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-uri', '-i',
                                metavar='<uri>',
                                dest='uri',
                                help='uri of the event',
                                required=True)
    delete_parser.set_defaults(func=event_delete)


def approve_parser(subcommand_parsers, common_parser):

    approve_parser = subcommand_parsers.add_parser(
        'approve',
        description='ViPR Event Approve CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Approve an Event')
    mandatory_args = approve_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-uri', '-i',
                                metavar='<uri>',
                                dest='uri',
                                help='uri of the event',
                                required=True)
    approve_parser.set_defaults(func=event_approve)

def decline_parser(subcommand_parsers, common_parser):

    decline_parser = subcommand_parsers.add_parser(
        'decline',
        description='ViPR Event Decline CLI usage.',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Decline an Event')
    mandatory_args = decline_parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-uri', '-i',
                                metavar='<uri>',
                                dest='uri',
                                help='uri of the event',
                                required=True)
    decline_parser.set_defaults(func=event_decline)

def event_parser(parent_subparser, common_parser):
    parser = parent_subparser.add_parser(
        'event',
        description='ViPR Event CLI usage',
        parents=[common_parser],
        conflict_handler='resolve',
        help='Operations on Event')
    subcommand_parsers = parser.add_subparsers(
        help='Use one of sub commands(list, show, delete, approve, decline, details)')

    list_parser(subcommand_parsers, common_parser)

    show_parser(subcommand_parsers, common_parser)

    delete_parser(subcommand_parsers, common_parser)

    approve_parser(subcommand_parsers, common_parser)
    
    decline_parser(subcommand_parsers, common_parser)

    details_parser(subcommand_parsers, common_parser)


