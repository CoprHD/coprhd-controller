#!/usr/bin/python

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.

import common
from common import SOSError


class Meter(object):

    '''
    The class definition for operations on 'Metering'.
    '''
    # Commonly used URIs for the 'Metering' module
    URI_METER = '/metering/stats.{0}/?time_bucket={1}'

    def __init__(self, ipAddr, port):
        '''
        Constructor: takes IP address and port of the SOS instance.
        These are needed to make http requests for REST API
        '''
        self.__ipAddr = ipAddr
        self.__port = port

    def get_stats(self, output_format, time_frame):
        '''
        Makes REST API call to get the statistics of resource usage
        during the given time frame
        Parameters:
            output_format: xml or json
            time_frame: string in yyyy-mm-ddThh:mm
        Returns:
            Resource usage details in response payload
        '''

        URI = Meter.URI_METER.format(output_format, time_frame)

        (s, h) = common.service_json_request(self.__ipAddr, self.__port,
                                             "GET", URI, None)
        if(output_format == "json"):
            o = common.json_decode(s)
            return o

        return s


def meter_get_status(args):
    obj = Meter(args.ip, args.port)
    try:
        time_frame = common.get_formatted_time_string(args.year,
                                                      args.month, args.day,
                                                      args.hour, args.minute)
        res = obj.get_stats(args.format, time_frame)

        if (args.format == "json"):
            return common.format_json_object(res)
        return res

    except ValueError as e:
        raise SOSError(SOSError.CMD_LINE_ERR, "error: " + str(e))
    except SOSError as e:
        if (e.err_code == SOSError.SOS_FAILURE_ERR):
            raise SOSError(SOSError.SOS_FAILURE_ERR,
                           "Unable to get requested usage statistics")
        else:
            raise e


def meter_parser(parent_subparser, common_parser):
    # main metering parser

    parser = parent_subparser.add_parser('meter',
                                         description='SOS metering CLI usage',
                                         parents=[common_parser],
                                         conflict_handler='resolve',
                                         help='Get metering statistics for' +
                                         ' the given time bucket')

    mandatory_args = parser.add_argument_group('mandatory arguments')
    mandatory_args.add_argument('-year', '-y',
                                help='Year', dest='year',
                                metavar='<year>', required=True)
    mandatory_args.add_argument('-month', '-mon',
                                metavar='<month>', dest='month',
                                help='month of the year {1 - 12}',
                                required=True)
    mandatory_args.add_argument('-day', '-d',
                                metavar='<day>', dest='day',
                                help='day of the month {01 - 31}',
                                required=True)
    mandatory_args.add_argument('-hour', '-hr',
                                metavar='<hour>', dest='hour',
                                help='hour of the day {00 - 23}',
                                required=True)
    parser.add_argument('-format', '-f',
                        metavar='<format>', dest='format',
                        help='response format: xml or json (default:json)',
                        choices=['xml', 'json'],
                        default="json")
    parser.add_argument('-minute', '-min',
                        metavar='<minute>', dest='minute',
                        help='minute of the hour {00 - 59}')

    parser.set_defaults(func=meter_get_status)
