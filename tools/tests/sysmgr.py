#!/usr/bin/python
#
# Copyright (c) 2015 EMC Corporation
# All Rights Reserved
#

import argparse
import sys
import os
import urllib
import re
import time
import datetime
import bourne as bourne_main

if bourne_main.PORT == bourne_main.LB_API_PORT:
    DEFAULT_SYSMGR_PORT = bourne_main.LB_API_PORT
else:
    DEFAULT_SYSMGR_PORT = 9993

SYSADMIN='root'
SYSADMIN_PASSWORD='ChangeMe'
verbose = True

# URIs
URI_UPGRADES_CLUSTER_INFO = "/upgrade/cluster-state/"
URI_UPGRADES_TARGET = "/upgrade/target-version/"
URI_UPGRADES_WAKEUP = "/upgrade/internal/wakeup/"
URI_UPGRADES_INSTALL = "/upgrade/image/install"
URI_UPGRADES_REMOVE = "/upgrade/image/remove"
URI_LOGS = "/logs"
URI_LOG_LEVELS = URI_LOGS + "/log-levels"
URI_SEND_ALERT = "/callhome/alert/"
URI_SEND_HEARTBEAT = "/callhome/heartbeat/"
URI_SEND_REGISTRATION = "/callhome/registration/"
URI_GET_ESRSCONFIG = "/callhome/esrs-device/"
URI_GET_LICENSE = "/license/"
URI_CONFIGURE_CONNECTEMC_SMTP = "/config/connectemc/email/"
URI_CONFIGURE_CONNECTEMC_FTPS = "/config/connectemc/ftps/"
URI_PROPS = "/config/properties/?"
URI_PROPS_METADATA = "/config/properties/metadata"
URI_RESET_PROPS = "/config/properties/reset/"
URI_SELF_TEST = "/self-test/"
URI_MONITOR_STATS = "/monitor/stats"
URI_MONITOR_HEALTH = "/monitor/health"
URI_MONITOR_DIAGNOSTICS = "/monitor/diagnostics"
URI_MONITOR_STORAGE="/monitor/storage"
URI_GET_CLI = "/cli"
URI_CONTROL_RESTART_SERIVCE = "/control/service/restart"
URI_CONTROL_REBOOT_NODE = "/control/node/reboot"
URI_CONTROL_POWEROFF_CLUSTER = "/control/cluster/poweroff"
URI_GET_ALERT_TASKS="/callhome/alert/{0}/tasks/"
URI_GET_ALERT_TASK="/callhome/alert/{0}/tasks/{1}"

def set_verbose(flag):
    global verbose
    verbose = flag
 
def wait_for_stable_state(args):
    timeCounter = 0
    print "Waiting for cluster state to become STABLE"
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    while True:
	try:
            curState =  bourne.api_check_success('GET', URI_UPGRADES_CLUSTER_INFO)
            if (curState['cluster_state'] == 'STABLE'):
                print "Cluster state is STABLE"
                return
        except:
	    print "Exception while checking for api success"
	    pass
	finally:
	    time.sleep(args.sleep)
            timeCounter += args.sleep
	    print  "Retrying... time elapsed: [" + str(timeCounter) + "s]"
            if (timeCounter >= args.length):
	        print("giving up wait_for_stable_state. counter %s args.length %s" % (timeCounter, args.length))
                raise Exception("Timeout occurred while waiting for cluster state to become STABLE. Please try again or check syssvc.log on your appliance for more information.")

def get_cluster_state(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_UPGRADES_CLUSTER_INFO + "?force=" + args.force, accept=args.accept)
    if args.wait_for_stable == 1 and resp['cluster_state'] != 'STABLE':
        wait_for_stable_state(args)
        # calling this again with force & accept params
        resp = bourne.api_check_success('GET', URI_UPGRADES_CLUSTER_INFO + "?force=" + args.force, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_target_version(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_UPGRADES_TARGET,accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def set_target(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('PUT', URI_UPGRADES_TARGET + "?version=" + args.version + "&force=" + args.force, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def wakeup(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_UPGRADES_WAKEUP)
    
def install(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('POST', URI_UPGRADES_INSTALL + "?version=" + args.version + "&force=" + args.force, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp
    
def remove(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('POST', URI_UPGRADES_REMOVE + "?version=" + args.version + "&force=" + args.force, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp
    
def prepare_body(args):
    params = { 'user_str' : args.message,
              'contact' : args.contact
             }
    return params

def prepare_connectemc_smtp_body(args):
    params = {'bsafe_encryption_ind' : 'no',
              'email_server' : args.smtp_server,
              'primary_email_address' : args.primary_email,
              'email_sender' : args.sender_email
             }
    return params

def prepare_connectemc_ftps_body(args):
    params = {'bsafe_encryption_ind' : 'no',
              'host_name' : args.hostname,
              'email_sender' : args.sender_email
             }
    return params

def prepare_license_body(args):
    text = ''
    if args.license_file:
        try:
            with open(args.license_file, 'r') as content_file:
                text = content_file.read()
            text = text.rstrip('\n')
        except:
            raise Exception('Failed to open file: '+args.license_file)
    else:
        text = args.license_text
    params = {"license_text" : text}
    return params

def prepare_get_log_lvl_params(args):
    params = '' 
    if ( args.log != '' ):
        for log in args.log.split(','):
            params += '&' if ('?' in params) else '?'
            params += "log_name=" + log
    if ( args.node != '' ):
        for node in args.node.split(','): 
            params += '&' if ('?' in params) else '?'
            params += "node_id=" + args.node
    return params

def prepare_params(args):
    params = prepare_get_log_lvl_params(args)
    if ( args.severity != '' ):
        params += '&' if ('?' in params) else '?'
        params += "severity=" + args.severity
    if ( args.start != '' ):
        params += '&' if ('?' in params) else '?'
        params += "start=" + args.start
    if ( args.end != '' ):
        params += '&' if ('?' in params) else '?'
        params += "end=" + args.end
    if ( args.regular != '' ):
        params += '&' if ('?' in params) else '?'
        params += "msg_regex=" + urllib.quote_plus(args.regular.encode("utf8"))
    if ( args.maxcount != ''):
        params += '&' if ('?' in params) else '?'
        params += "maxcount=" + args.maxcount
    if ( args.dryrun == True):
        params += '&' if ('?' in params) else '?'
        params += "dryrun=" + str(args.dryrun).lower()
    return params

def prepare_alert_params(args):
    params = prepare_get_log_lvl_params(args)
    if ( args.severity != '' ):
        params += '&' if ('?' in params) else '?'
        params += "severity=" + args.severity
    if ( args.start != '' ):
        params += '&' if ('?' in params) else '?'
        params += "start=" + args.start
    if ( args.end != '' ):
        params += '&' if ('?' in params) else '?'
        params += "end=" + args.end
    if ( args.regular != '' ):
        params += '&' if ('?' in params) else '?'
        params += "msg_regex=" + urllib.quote_plus(args.regular.encode("utf8"))
    if ( args.maxcount != ''):
        params += '&' if ('?' in params) else '?'
        params += "maxcount=" + args.maxcount
    if ( args.source != ''):
        params += '&' if ('?' in params) else '?'
        params += "source=" + args.source
    if ( args.eventid != ''):
        params += '&' if ('?' in params) else '?'
        params += "event_id=" + args.eventid
    if ( args.forceAttachLogs == True):
        params += '&' if ('?' in params) else '?'
        params += "forceAttachLogs=" + str(args.forceAttachLogs).lower()        
    return params

def prepare_set_log_level_body(args):
    params = {'severity' : int(args.severity)}
    if ( args.log != '' ):
        params['log_name'] = args.log.split(',')
    if ( args.node != '' ):
        params['node_id'] = args.node.split(',')
    if ( args.expir_in_min != '' ):
        params['expir_in_min'] = int(args.expir_in_min)
    if ( args.scope != '' ):
        params['scope'] = args.scope

    if verbose:
        print "set log level param in JSON:"
        print params
    return params

def prepare_set_log_level_body_xml(args):
    params = "<log_level_set><severity>"+args.severity+"</severity>"
    if ( args.log != '' ):
        for log_name in args.log.split(','):
            params += "<log_name>"+log_name+"</log_name>"
    if ( args.node != '' ):
        for node_id in args.node.split(','):
            params += "<node_id>"+node_id+"</node_id>"
    if ( args.expir_in_min != '' ):
        params += "<expir_in_min>"+args.expir_in_min+"</expir_in_min>"
    if ( args.scope != '' ):
        params += "<scope>"+args.scope+"</scope>"
    params += "</log_level_set>"

    if verbose:
        print "set log level param in XML:"
        print params
    return params

def get_logs(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    type = args.accept
    if type == None:
        args.accept='application/json'
    resp = bourne.api_check_success('GET-stream', URI_LOGS + prepare_params(args), accept=args.accept)
    if resp:
        for chunk in resp.iter_content(chunk_size=1024):
            sys.stdout.write(chunk)
        sys.stdout.write("\n")
    return
        
    if type == 'text/plain':
        print_logs_text(resp, args.dryrun, type)
    else:
        pretty_print(resp, args.accept)
    return resp

# Method to print logs in text format
def print_logs_text(response, dryrun, accept):
    if(dryrun == True):
        return
    if response:
        for msg in response:
            # general logs
            if msg['class']:
                utcTime=datetime.datetime.utcfromtimestamp(msg['time_ms']/1000.0).strftime('%Y-%m-%d %H:%M:%S,%f')[:-3]
                print "%s %s %s [%s] %s %s (line %s) %s" \
                  % (utcTime,msg['node'],msg['service'],msg['thread'],msg['severity'],msg['class'],msg['line'],msg['message'])
            # system logs
            else:
                print "%s,000 %s %s %s %s" \
                  % (msg['time'],msg['node'],msg['service'],msg['severity'],msg['message'])
 

def write_to_file(filename, mode, content):
    try:
        with open(filename, mode) as f:
            f.write(content)
        print 'Successfully write to file: ' + filename
    except Exception, e:
        raise Exception('Failed to write to file: ' + filename + '. Cause: ' + str(e))

def get_download_filename(content_disposition):
    content_disposition = content_disposition.replace(" ", "")
    m = re.match("(.*)filename=(.+)", content_disposition)
    if m:
        filename = m.group(2)
        return filename
    else:
        return ""

def get_log_levels(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_LOG_LEVELS + prepare_get_log_lvl_params(args), accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def set_log_levels(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    params = None
    if args.type == 'application/json':
        params = prepare_set_log_level_body(args)
    elif args.type == 'application/xml':
        params = prepare_set_log_level_body_xml(args)
    else:
        raise Exception('invalid content type, must be application/json or application/xml')
    resp = bourne.api_check_success('POST', URI_LOG_LEVELS, parms=params, content_type=args.type, accept=args.accept)   
    pretty_print(resp, args.accept)
    return resp

def pretty_print(response, accept='application/json'):
    if response and verbose:
        if accept == 'application/json':
            bourne.pretty_print_json(response)
        elif accept == 'application/xml':
            bourne.pretty_print_xml(response)
        elif accept == 'text/plain':
            print response.text
        else:
            print response

def send_alert(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    args.accept='application/json'
    resp = bourne.api_check_success('POST', URI_SEND_ALERT + prepare_alert_params(args), prepare_body(args), accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_tasks(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    uri = URI_GET_ALERT_TASKS.format(args.resource_id)
    resp = bourne.api_check_success('GET', uri, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_task(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    uri = URI_GET_ALERT_TASK.format(args.resource_id, args.op_id)
    resp = bourne.api_check_success('GET', uri, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp
	 
def send_heartbeat(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_SEND_HEARTBEAT)
    
def send_registration(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_SEND_REGISTRATION)
	
	
def get_esrsconfig(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_GET_ESRSCONFIG, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp
	
def get_license(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_GET_LICENSE, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def add_license(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp=bourne.api_check_success('POST',URI_GET_LICENSE, prepare_license_body(args), accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def configure_connectemc_smtp(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp=bourne.api_check_success('POST',URI_CONFIGURE_CONNECTEMC_SMTP, prepare_connectemc_smtp_body(args), accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def configure_connectemc_ftps(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp=bourne.api_check_success('POST',URI_CONFIGURE_CONNECTEMC_FTPS, prepare_connectemc_ftps_body(args), accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_props(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_PROPS, accept=args.accept, qparms=args.category)
    pretty_print(resp, args.accept)
    return resp

def set_props(args):
    if args.properties_file:
        try:
            f = open(args.properties_file, 'r')
            props = ''
            for line in f:
                props += line + ';'
        except:
            raise Exception('Failed to open file: '+args.properties_file)
    elif args.properties:
        props = args.properties
    else:
        raise Exception('Please provide property file or properties directly.')
    # Changed the property value separator from comma to semi-colon
    params = get_properties_body(props.split(';'))
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('PUT', URI_PROPS, params, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_properties_body(props):
    params = dict()
    properties = dict()
    params['properties'] = properties
    properties['entry'] = []
    for p in props:
        m = re.match("(.+?)=(.*)\n?", p)
        if m:
            key, value = m.groups()
            entry = dict()
            entry['key'] = key
            entry['value'] = value
            properties['entry'].append(entry)
    return params        

def get_props_metadata(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_PROPS_METADATA,accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def reset_props(args):
    bodystring = ''
    if args.properties_file:
        try:
            f = open(args.properties_file, 'r')
            keys = ''
            for line in f:
                keys += line + ','
            bodystring = get_reset_properties_body(keys.split(','))
        except:
            raise Exception('Failed to open file: '+args.properties_file)
    elif args.properties:
        keys = args.properties
        bodystring = get_reset_properties_body(keys.split(','))
    else:
        bodystring = '{}'
    print "Request: " + str(bodystring)
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('POST', URI_RESET_PROPS + "?removeObsolete=" + args.force, bodystring, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_reset_properties_body(keys):
    params = dict()
    params['property'] = []
    for k in keys:
        m = re.match("(.+)\n?", k)
        if m:
            key = m.groups()[0]
            params['property'].append(key)
    return params

def get_stats(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    uri = URI_MONITOR_STATS + "?node_id="+args.node_id if args.node_id else URI_MONITOR_STATS
    resp = bourne.api_check_success('GET', uri, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_health(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    uri = URI_MONITOR_HEALTH + "?node_id="+args.node_id if args.node_id else URI_MONITOR_HEALTH
    resp = bourne.api_check_success('GET', uri, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_diagnostics(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    uri = URI_MONITOR_DIAGNOSTICS+"?verbose="+args.verbose
    uri = uri + "&node_id="+args.node_id if args.node_id else uri
    resp = bourne.api_check_success('GET', uri, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_storage(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_MONITOR_STORAGE, accept=args.accept)
    pretty_print(resp, args.accept)
    return resp

def get_alerts(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    args.log = "systemevents"
    type = args.accept
    if type == 'native' or type == 'padded':
        args.accept='application/json'
    resp = bourne.api_check_success('GET', URI_LOGS + prepare_params(args), accept=args.accept)
    if type == 'native' or type == 'padded':
        print_logs_text(resp, type)
    else:
        pretty_print(resp, args.accept)
    return resp

def get_cli_tar(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    resp = bourne.api_check_success('GET', URI_GET_CLI, accept='application/octet-stream')
    expected_cli_name = 'ViPR-cli.tar.gz'
    if resp:
        filename = get_download_filename(resp.headers['content-disposition'])
        if (filename != expected_cli_name):
            raise Exception("Received cli tar's name '" + filename + "' is not '" + expected_cli_name + "'")
    else:
        raise Exception("Error getting cli tar")

    write_to_file(expected_cli_name, 'wb', resp.content)

def restart_service(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_CONTROL_RESTART_SERIVCE+"?node_id="+args.node_id+"&name="+args.name, accept=args.accept)

def reboot_node(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_CONTROL_REBOOT_NODE+"?node_id="+args.node_id, accept=args.accept)

def poweroff_cluster(args):
    bourne.connect(args.ip, DEFAULT_SYSMGR_PORT)
    bourne.api_check_success('POST', URI_CONTROL_POWEROFF_CLUSTER)
    
def disable_update_check(args):
    args.properties_file=None
    args.properties = 'system_update_repo'
    reset_props(args)
    
#----------------------------------------------------------------------
# command-line parsing
#----------------------------------------------------------------------

try:
    bourne_ip = os.environ['BOURNE_IPADDR']
except:
    bourne_ip = 'localhost'

try:
    bourne = bourne_main.Bourne()
except:
    raise

if __name__=="__main__":
    
    def login(args):
        bourne.connect(args.ip)
        bourne.login(user = args.user, password = args.password)
        print "Logged in as user: " + args.user

    def logout(args):
        bourne.connect(args.ip)
        bourne.security_logout()
        print "Logged out."

    main_parser = argparse.ArgumentParser(
                  add_help = False, 
                  epilog = 'Ex: sysmgr.py login --ip 10.247.100.15'
                  )
    main_parser.add_argument('--ip', metavar = 'ipaddr', help = 'IP address of a ViPR system', default=bourne_ip)
    
    common_parser = argparse.ArgumentParser()
    common_parser.add_argument('--ip', metavar = 'ipaddr', help = 'IP address of a ViPR system', default=bourne_ip)

    login_parser = argparse.ArgumentParser(parents = [common_parser], description = 'Login cli usage', conflict_handler='resolve')
    login_parser.add_argument('--user',    metavar = 'user',    help = 'Login username', default=SYSADMIN)
    login_parser.add_argument('--password',    metavar = 'password',    help = 'Login password', default=SYSADMIN_PASSWORD)

    logout_parser = argparse.ArgumentParser(parents = [common_parser], description = 'Logout cli usage', conflict_handler='resolve')

    get_parser = argparse.ArgumentParser(parents = [common_parser], conflict_handler='resolve')
    get_parser.add_argument('--accept',    metavar = 'accept_type',    help = 'Response accept type: application/json,application/xml', default='application/json')

    get_log_levels_parser = argparse.ArgumentParser(parents = [get_parser], description = 'Get log levels usage', conflict_handler='resolve')
    get_log_levels_parser.add_argument('--log',    metavar = 'log_name',    help = 'A comma-separated list of log file names', default='')
    get_log_levels_parser.add_argument('--node',    metavar = 'node_id',    help = 'A comma-separated list of Node ids', default='')

    set_log_levels_parser = argparse.ArgumentParser(parents = [get_log_levels_parser], 
                                                    description = 'Set log levels usage', conflict_handler='resolve')
    set_log_levels_parser.add_argument('severity',    metavar = 'severity',    help = 'Log severity to be set')
    set_log_levels_parser.add_argument('--expir_in_min',    metavar = 'expir_in_min',    help = 'Log level expiration time in minutes', default='')
    set_log_levels_parser.add_argument('--scope',  metavar = 'scope',  help = 'Log level scope', default='0');
    set_log_levels_parser.add_argument('--type',    metavar = 'content_type',    help = 'Payload content type: application/json,application/xml', default='application/json')
    
    logfile_parser = argparse.ArgumentParser(parents = [common_parser], description = 'Logs cli usage', conflict_handler='resolve')
    logfile_parser.add_argument('--accept',    metavar = 'accept_type',    help = 'Response accept type: application/json,application/xml,text/plain', default='application/json')
    logfile_parser.add_argument('--severity',    metavar = 'severity',    help = 'Log severity', default='7')
    logfile_parser.add_argument('--start',    metavar = 'start',    help = 'Log message start date/time', default='')
    logfile_parser.add_argument('--end',    metavar = 'end',    help = 'Log message end date/time', default='')
    logfile_parser.add_argument('--node',    metavar = 'node_id',    help = 'Node id', default='')
    logfile_parser.add_argument('--regular',    metavar = 'msg_regex',    help = 'Log message Regex', default='')
    logfile_parser.add_argument('--maxcount',    metavar = 'maxcount',    help = 'Maximum number of log messages to retrieve', default='')
	
    logs_parser = argparse.ArgumentParser(parents = [logfile_parser], conflict_handler='resolve')
    logs_parser.add_argument('--log',    metavar = 'log_name',    help = 'Log file name', default='')
    logs_dryrun_parser = argparse.ArgumentParser(parents = [logs_parser], conflict_handler='resolve')    
    logs_dryrun_parser.add_argument('--dryrun', help = 'dry run', action='store_true', default=False)

    alert_parser = argparse.ArgumentParser(parents = [logs_parser],description='Send alert cli usage', conflict_handler='resolve')
    alert_parser.add_argument('--source',  metavar = 'source', 
                              help = 'Source of this alert. Allowed values: OBJECT, CONTROLLER', default='')
    alert_parser.add_argument('--eventid',  metavar = 'eventid', help = 'Event id', default='')
    alert_parser.add_argument('--message',  metavar = 'message', help = 'User message')
    alert_parser.add_argument('--contact',    metavar = 'contact',    help = 'User contact', default='')
    alert_parser.add_argument('--forceAttachLogs', help = 'force attaching logs when its size exceeds the max', action='store_true', default=False)

    connectemc_smtp_parser = argparse.ArgumentParser(parents = [get_parser],description='Configure connectemc for SMTP', conflict_handler='resolve')
    connectemc_smtp_parser.add_argument('--primary_email', metavar='primary_email',  help = 'Primary email address')
    connectemc_smtp_parser.add_argument('--smtp_server',  metavar='smtp_server', help = 'SMTP server')
    connectemc_smtp_parser.add_argument('--sender_email',  metavar='sender_email', help = 'From email address')
 
    connectemc_ftps_parser = argparse.ArgumentParser(parents = [get_parser],description='Configure connectemc for FTPS', conflict_handler='resolve')
    connectemc_ftps_parser.add_argument('--hostname',  metavar = 'hostname', help = 'FTPS server')
    connectemc_ftps_parser.add_argument('--sender_email',  metavar='sender_email', help = 'From email address')

    force_parser = argparse.ArgumentParser(add_help=False, conflict_handler='resolve')
    force_parser.add_argument('--force',    metavar = 'force',    help = 'Force', default='0')

    selftest_parser = argparse.ArgumentParser(parents = [get_parser], description='Run self test cli usage', conflict_handler='resolve')
    selftest_parser.add_argument('--params',    metavar = 'params',   help = 'Self test parameter file', default='')
    selftest_parser.add_argument('--depth',    metavar = 'depth',   help = 'Depth of tests', default='0.1')
    selftest_parser.add_argument('--breadth',    metavar = 'breadth',   help = 'Random percentage of cos to test', default='1')
    
    property_parser = argparse.ArgumentParser(parents = [get_parser, force_parser],conflict_handler='resolve', epilog='Ex: sysmgr.py set_props --properties key1=value1,key2=value2')
    property_parser.add_argument('--properties_file',    metavar = 'properties_file',   help = 'Properties file(with new key=value pairs) name')
    property_parser.add_argument('--properties',    metavar = 'properties',   help = 'Properties to set (comma separated key=value pairs)')
    
    reset_property_parser = argparse.ArgumentParser(parents = [get_parser, force_parser],conflict_handler='resolve', epilog='Ex: sysmgr.py reset_props --properties key1,key2')
    reset_property_parser.add_argument('--properties_file',    metavar = 'properties_file',   help = 'Properties file(with list of keys to reset) name')
    reset_property_parser.add_argument('--properties',    metavar = 'properties',   help = 'Properties to reset (comma separated list of keys)')

    upgrade_parser = argparse.ArgumentParser(parents = [get_parser, force_parser], conflict_handler='resolve')
    upgrade_parser.add_argument('--version', metavar = 'version', help = 'Version string', required=True)
    
    cluster_parser = argparse.ArgumentParser(parents = [get_parser, force_parser],description='Get cluster state cli usage', conflict_handler='resolve')
    cluster_parser.add_argument('-w', '--wait_for_stable', metavar = 'wait', help = 'If 1, will wait for cluster to become stable', type=int, default=0)
    cluster_parser.add_argument('--sleep', metavar = 'sleep', help = 'Number of seconds to sleep between retries',  type=int, default=10)
    cluster_parser.add_argument('--length', metavar = 'length', help = 'Maximum number of seconds to wait for the cluster to become stable',  type=int, default=600)

    addlicense_parser = argparse.ArgumentParser(parents = [get_parser],conflict_handler='resolve')
    addlicense_parser.add_argument('--license_file', metavar = 'filename', help = 'File that contains license text', required=True)

    restart_service_parser = argparse.ArgumentParser(parents = [get_parser], description='Restart service', conflict_handler='resolve')
    restart_service_parser.add_argument('--node_id',    metavar = 'node_id',    help = 'Node id', default='')
    restart_service_parser.add_argument('--name',    metavar = 'service_name',    help = 'Service name', default='')

    reboot_node_parser = argparse.ArgumentParser(parents = [get_parser], description='Reboot node', conflict_handler='resolve')
    reboot_node_parser.add_argument('--node_id',    metavar = 'node_id',    help = 'Node id', default='')
    
    get_stats_parser = argparse.ArgumentParser(parents = [get_parser], description='Reboot node', conflict_handler='resolve')
    get_stats_parser.add_argument('--node_id',    metavar = 'node_id',    help = 'List of comma separated node ids', default='')

    diagnostics_parser = argparse.ArgumentParser(parents = [get_stats_parser], conflict_handler='resolve')
    diagnostics_parser.add_argument('-v','--verbose', metavar = 'verbose', help = 'If 1, verbose option is set', default='0')
    

    tasks_parser = argparse.ArgumentParser(parents = [get_parser], conflict_handler='resolve')
    tasks_parser.add_argument('-r','--resource_id', metavar = 'resourceid', help = 'Resource id', required=True)

    task_parser = argparse.ArgumentParser(parents = [tasks_parser], conflict_handler='resolve')
    task_parser.add_argument('-o','--op_id', metavar = 'operationid', help = 'Operation id', required=True)

    commands = (
         ( "login"                     , ( login_parser,           login,                     )),
         ( "logout"                    , ( logout_parser,          logout,                    )),
         ( "get_cluster_state"         , ( cluster_parser,         get_cluster_state          )),
         ( "get_target_version"        , ( get_parser,             get_target_version         )),
         ( "set_target"                , ( upgrade_parser,         set_target                 )),
         ( "install_image"             , ( upgrade_parser,         install                    )),
         ( "remove_image"              , ( upgrade_parser,         remove,                    )),
         ( "wakeup"                    , ( common_parser,          wakeup,                    )),
         ( "get_logs"                  , ( logs_dryrun_parser,     get_logs,                  )),
         ( "get_log_levels"            , ( get_log_levels_parser,  get_log_levels,            )),
         ( "set_log_levels"            , ( set_log_levels_parser,  set_log_levels,            )),
         ( "send_alert"                , ( alert_parser,           send_alert,                )),
         ( "send_heartbeat"            , ( common_parser,          send_heartbeat,            )),
         ( "send_registration"         , ( common_parser,          send_registration,         )),
         ( "get_esrsconfig"            , ( get_parser,             get_esrsconfig,            )),
         ( "get_license"               , ( get_parser,             get_license,               )),
         ( "add_license"               , ( addlicense_parser,      add_license,               )),
         ( "configure_connectemc_ftps" , ( connectemc_ftps_parser, configure_connectemc_ftps, )),
         ( "configure_connectemc_smtp" , ( connectemc_smtp_parser, configure_connectemc_smtp, )),
         ( "get_props"                 , ( get_parser,             get_props,                 )),
         ( "set_props"                 , ( property_parser,        set_props,                 )),
         ( "get_props_metadata"        , ( get_parser,             get_props_metadata         )),
         ( "reset_props"               , ( reset_property_parser,  reset_props                )),
         ( "get_stats"                 , ( get_stats_parser,       get_stats                  )),
         ( "get_health"                , ( get_stats_parser,       get_health                 )),
         ( "get_diagnostics"           , ( diagnostics_parser,     get_diagnostics            )),
         ( "get_alerts"                , ( logfile_parser,         get_alerts                 )),
         ( "get_cli_tar"               , ( common_parser,          get_cli_tar                )),
         ( "restart_service"           , ( restart_service_parser, restart_service,           )),
         ( "reboot_node"               , ( reboot_node_parser,     reboot_node,               )),
         ( "poweroff_cluster"          , ( common_parser,          poweroff_cluster,          )),
         ( "get_tasks"                 , ( tasks_parser,           get_tasks                  )),
         ( "get_task"                  , ( task_parser,            get_task                   )),
         ( "get_storage"               , ( get_parser,             get_storage                )),
         ( "disable_update_check"      , ( get_parser,             disable_update_check      ))    
    )
    main_parser.add_argument('cmd', help = ','.join(map(lambda x: "  %s" % (x[0],), commands,)))
    usage = main_parser.format_usage()
    # Adding 'cmd' after file name and removing 'cmd' at the end
    # first 7 chars of usage is 'usage:' - removing it as it will be added again.
    main_parser.usage = usage[7:usage.find('.py')+3] + ' cmd' + usage[usage.find('.py')+3:usage.rfind(']')+1]
    
    try:
        if (len(sys.argv) > 1):
            command = sys.argv[1]
            if command not in dict(commands):
                raise Exception("Command not found: "+ command)
            ( parser, method ) = dict(commands)[command]

            # Adding command to parser usage after file name (sysmgr.py).
            usage = parser.format_usage()
            parser.usage = usage[7:usage.find('.py')+3] + ' ' + command + usage[usage.find('.py')+3:]
            method(parser.parse_args(' '.join(sys.argv[2:]).split()))
        else:
            main_parser.print_help()
            sys.exit(2)
    except Exception, e:
        print >>sys.stderr, e 
        sys.exit(1)
