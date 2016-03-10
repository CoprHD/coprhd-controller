#!/usr/bin/python

# Copyright (c) 2012 EMC Corporation
# All Rights Reserved
#
# This software contains the intellectual property of EMC Corporation
# or is licensed to EMC Corporation from third parties.  Use of this
# software and the intellectual property contained therein is expressly
# limited to the terms and conditions of the License Agreement under which
# it is provided by or on behalf of EMC.
'''
Contains some commonly used utility methods
'''
import os
import stat
import json
import re
import datetime
import sys
import socket
import base64
import requests
from requests.exceptions import SSLError
from requests.exceptions import ConnectionError
from requests.exceptions import TooManyRedirects
from requests.exceptions import Timeout
import cookielib
import xml.dom.minidom
import getpass
from xml.etree import ElementTree
from threading import Timer

from urihelper import singletonURIHelperInstance

PROD_NAME = 'storageos'
TENANT_PROVIDER = 'urn:vipr:TenantOrg:provider:'

SWIFT_AUTH_TOKEN = 'X-Auth-Token'

TIMEOUT_SEC = 20  # 20 SECONDS
OBJCTRL_INSECURE_PORT = '9010'
OBJCTRL_PORT = '4443'
IS_TASK_TIMEOUT = False


def _decode_list(data):
    rv = []
    for item in data:
        if isinstance(item, unicode):
            item = item.encode('utf-8')
        elif isinstance(item, list):
            item = _decode_list(item)
        elif isinstance(item, dict):
            item = _decode_dict(item)
        rv.append(item)
    return rv


def _decode_dict(data):
    rv = {}
    for key, value in data.iteritems():
        if isinstance(key, unicode):
            key = key.encode('utf-8')
        if isinstance(value, unicode):
            value = value.encode('utf-8')
        elif isinstance(value, list):
            value = _decode_list(value)
        elif isinstance(value, dict):
            value = _decode_dict(value)
        rv[key] = value
    return rv


def format_xml(obj):
    xml_out = xml.dom.minidom.parseString(obj)
    return xml_out.toprettyxml()


def json_decode(rsp):
    '''
    Used to decode the JSON encoded response
    '''
    o = ""
    try:
        o = json.loads(rsp, object_hook=_decode_dict)
    except ValueError:
        raise SOSError(SOSError.VALUE_ERR,
                       "Failed to recognize JSON payload:\n[" + rsp + "]")
    return o


def json_encode(name, value):
    '''
    Used to encode any attribute in JSON format
    '''

    body = json.dumps({name: value})
    return body


def xml_decode(rsp):
    if not rsp:
        return ''
    try:
        o = ElementTree.fromstring(str(rsp))
        #o = tree.getroot()
    except:
        print 'Unexpected exception: ', sys.exc_info()[0]
        raise
    return o


def service_json_request(ip_addr, port, http_method, uri, body, token=None,
                         xml=False, contenttype='application/json',
                         filename=None, customheaders=None):
    '''
    Used to make an HTTP request and get the response.
    The message body is encoded in JSON format.
    Parameters:
        ip_addr: IP address or host name of the server
        port: port number of the server on which it
            is listening to HTTP requests
        http_method: one of GET, POST, PUT, DELETE
        uri: the request URI
        body: the request payload
    Returns:
        a tuple of two elements: (response body, response headers)
    Throws: SOSError in case of HTTP errors with err_code 3
    '''
    global COOKIE

    SEC_AUTHTOKEN_HEADER = 'X-SDS-AUTH-TOKEN'

    if (xml):
        headers = {'Content-Type': contenttype,
                   'ACCEPT': 'application/xml, application/octet-stream',
                   'X-EMC-REST-CLIENT': 'TRUE'}
    else:
        headers = {'Content-Type': contenttype,
                   'ACCEPT': 'application/json, application/octet-stream',
                   'X-EMC-REST-CLIENT': 'TRUE'}

    if(customheaders):
        headers.update(customheaders)

    if (token):
        if ('?' in uri):
            uri += '&requestToken=' + token
        else:
            uri += '?requestToken=' + token

    try:

        cookiefile = COOKIE
        form_cookiefile = None
        if (cookiefile is None):
            install_dir = getenv('VIPR_CLI_INSTALL_DIR')
            if (install_dir is None):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               "VIPR_CLI_INSTALL_DIR is not set." +
                               " Please execute viprcli.profile\n")
            if sys.platform.startswith('linux'):
                parentshellpid = os.getppid()
                if (parentshellpid is not None):
                    form_cookiefile = install_dir + '/cookie/' + \
                        str(parentshellpid)
                else:
                    form_cookiefile = install_dir + '/cookie/cookiefile'
            elif sys.platform.startswith('win'):
                form_cookiefile = install_dir + '\\cookie\\cookiefile'
            else:
                form_cookiefile = install_dir + '/cookie/cookiefile'
        if (form_cookiefile):
            cookiefile = form_cookiefile
            if (not os.path.exists(cookiefile)):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               cookiefile + " : Cookie not found :" +
                               " Please authenticate again")
            fd = open(cookiefile, 'r')
            if (fd):
                fd_content = fd.readline().rstrip()
                if(fd_content):
                    cookiefile = fd_content
                else:
                    raise SOSError(SOSError.NOT_FOUND_ERR,
                                   cookiefile + " : Failed to retrive" +
                                   " the cookie file")
            else:
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               cookiefile + " : read failure\n")
        #cli support for api version
        protocol = "https://"
        if(str(port) == '8080'):
            protocol = "http://"        
        url = protocol + ip_addr + ":" + str(port) + uri

        cookiejar = cookielib.LWPCookieJar()
        if (cookiefile):
            if (not os.path.exists(cookiefile)):
                raise SOSError(SOSError.NOT_FOUND_ERR, cookiefile + " : " +
                               "Cookie not found : Please authenticate again")
            if (not os.path.isfile(cookiefile)):
                raise SOSError(SOSError.NOT_FOUND_ERR,
                               cookiefile + " : Not a cookie file")
            # cookiejar.load(cookiefile, ignore_discard=True,
            # ignore_expires=True)
            tokenfile = open(cookiefile)
            token = tokenfile.read()
            tokenfile.close()
        else:
            raise SOSError(SOSError.NOT_FOUND_ERR,
                           cookiefile + " : Cookie file not found")

        headers[SEC_AUTHTOKEN_HEADER] = token


        if (http_method == 'GET'):
            '''when the GET request is specified with a filename, we write
               the contents of the GET request to the filename. This option
               generally is used when the contents to be returned are large.
               So, rather than getting all the data at once we Use
               stream=True for the purpose of streaming. Stream = True
               means we can stream data'''
            if(filename):
                if(requests.__version__ == "2.3.0"):
                    response = requests.get(url, stream=True, headers=headers,
                                        verify=False, cookies=cookiejar)
                else:
                    response = requests.get(url, prefetch=False, headers=headers,
                                        verify=False, cookies=cookiejar)
                    

            else:
                response = requests.get(url, headers=headers, verify=False,
                                        cookies=cookiejar)

            if(filename):
                try:
                    with open(filename, 'wb') as fp:
                        while(True):
                            chunk = response.raw.read(100)

                            if not chunk:
                                break
                            fp.write(chunk)
                except IOError as e:
                    raise SOSError(e.errno, e.strerror)

        elif (http_method == 'POST'):
            if(filename):
                with open(filename, "rb") as f:
                    response = requests.post(url, data=f, headers=headers,
                                             verify=False, cookies=cookiejar)
            else:
                response = requests.post(url, data=body, headers=headers,
                                         verify=False, cookies=cookiejar)
        elif (http_method == 'PUT'):
            response = requests.put(url, data=body, headers=headers,
                                    verify=False, cookies=cookiejar)
        elif (http_method == 'DELETE'):

            response = requests.delete(url, headers=headers, verify=False,
                                       cookies=cookiejar)
        else:
            raise SOSError(SOSError.HTTP_ERR,
                           "Unknown/Unsupported HTTP method: " + http_method)

        if((response.status_code == requests.codes['ok']) or
           (response.status_code == 202)):
                return (response.text, response.headers)
        else:
            error_msg = None
            if(response.status_code == 500):
                responseText = json_decode(response.text)
                errorDetails = ""
                if('details' in responseText):
                    errorDetails = responseText['details']
                error_msg = "ViPR internal server error. Error details: " + \
                    errorDetails
            elif(response.status_code == 401):
                error_msg = "Access forbidden: Authentication required"
            elif(response.status_code == 403):
                error_msg = ""
                errorDetails = ""
                errorDescription = ""

                responseText = json_decode(response.text)

                if('details' in responseText):
                    errorDetails = responseText['details']
                    error_msg = error_msg + "Error details: " + errorDetails
                elif('description' in responseText):
                    errorDescription = responseText['description']
                    error_msg = error_msg + "Error description: " + \
                        errorDescription
                else:
                    error_msg = "Access forbidden: You don't have" + \
                        " sufficient privileges to perform this operation"

            elif(response.status_code == 404):
                error_msg = "Requested resource not found"
            elif(response.status_code == 405):
                error_msg = str(response.text)
            elif response.status_code == 503:
                error_msg = ""
                errorDetails = ""
                errorDescription = ""

                responseText = json_decode(response.text)

                if 'code' in responseText:
                    errorCode = responseText['code']
                    error_msg = error_msg + "Error " + str(errorCode)

                if 'details' in responseText:
                    errorDetails = responseText['details']
                    error_msg = error_msg + ": " + errorDetails
                elif 'description' in responseText:
                    errorDescription = responseText['description']
                    error_msg = error_msg + ": " + errorDescription
                else:
                    error_msg = "Service temporarily unavailable: The server" + \
                                " is temporarily unable to service your request"
            else:
                error_msg = response.text
                if isinstance(error_msg, unicode):
                    error_msg = error_msg.encode('utf-8')
            raise SOSError(SOSError.HTTP_ERR, "HTTP code: " +
                           str(response.status_code) +
                           ", " + response.reason + " [" + error_msg + "]")

    except (SOSError, socket.error, SSLError,
            ConnectionError, TooManyRedirects, Timeout) as e:
        raise SOSError(SOSError.HTTP_ERR, str(e))
    # TODO : Either following exception should have proper message or IOError
    # should just be combined with the above statement
    except IOError as e:
        raise SOSError(SOSError.HTTP_ERR, str(e))


def is_uri(name):
    '''
    Checks whether the name is a UUID or not
    Returns:
        True if name is UUID, False otherwise
    '''
    try:
        (urn, prod, trailer) = name.split(':', 2)
        return (urn == 'urn' and prod == PROD_NAME)
    except:
        return False

def get_viprcli_version():
    try:
        filename = os.path.abspath(os.path.dirname(__file__))        
        filename = os.path.join(filename, "ver.txt")
        verfile = open(filename, 'r')
        line = verfile.readline().strip("\r\n")
        verfile.close()
        return line
    except IOError as e:
        raise SOSError(SOSError.NOT_FOUND_ERR, str(e))


def format_json_object(obj):
    '''
    Formats JSON object to make it readable by proper indentation
    Parameters:
        obj - JSON object
    Returns:
        a string of  formatted JSON object
    '''
    return json.dumps(obj, sort_keys=True, indent=3)


def pyc_cleanup(directory, path):
    '''
    Cleans up .pyc files in a folder
    '''
    for filename in directory:
        if filename[-3:] == 'pyc':
            os.remove(path + os.sep + filename)
        elif os.path.isdir(path + os.sep + filename):
            pyc_cleanup(os.listdir(path + os.sep + filename),
                        path + os.sep + filename)


def get_parent_child_from_xpath(name):
    '''
    Returns the parent and child elements from XPath
    '''
    if('/' in name):
        (pname, label) = name.rsplit('/', 1)
    else:
        pname = None
        label = name
    return (pname, label)


def get_object_id(obj):
    '''
    Returns value of 'id' field in the given JSON object
    '''
    if (not obj):
        return {}
    elif (isinstance(obj['id'], str)):
        return [obj['id']]
    else:
        return obj['id']


def validate_port_number(string):
    '''
    Checks whether the given string is a valid port number
    '''
    try:
        value = int(string)
    except ValueError as e:
        return False

    if(value >= 0 and value <= 65535):
        return True
    return False


def get_formatted_time_string(year, month, day, hour, minute):
    '''
    Validates the input parameters: year, month, day, hour and minute.
    Returns time stamp in yyyy-MM-dd'T'HH:mm if parameters are valid;
    None otherwise
    The parameter: minute is optional. If this is passed as None, then
    the time stamp returned will be of the form: yyyy-MM-dd'T'HH

    Throws:
        ValueError in case of invalid input
    '''
    result = None
    if minute:
        d = datetime.datetime(int(year), int(month), int(day), int(hour),
                              int(minute))
        result = d.strftime("%Y-%m-%dT%H:%M")
    else:
        d = datetime.datetime(int(year), int(month), int(day), int(hour))
        result = d.strftime("%Y-%m-%dT%H")

    return result


def to_bytes(in_str):
    """
    Converts a size to bytes
    Parameters:
        in_str - a number suffixed with a unit: {number}{unit}
                units supported:
                K, KB, k or kb - kilobytes
                M, MB, m or mb - megabytes
                G, GB, g or gb - gigabytes
                T, TB, t or tb - terabytes
    Returns:
        number of bytes
        None; if input is incorrect

    """
    match = re.search('^([0-9]+)([a-zA-Z]{0,2})$', in_str)

    if not match:
        return None

    unit = match.group(2).upper()
    value = match.group(1)

    size_count = long(value)
    if (unit in ['K', 'KB']):
        multiplier = long(1024)
    elif (unit in ['M', 'MB']):
        multiplier = long(1024 * 1024)
    elif (unit in ['G', 'GB']):
        multiplier = long(1024 * 1024 * 1024)
    elif (unit in ['T', 'TB']):
        multiplier = long(1024 * 1024 * 1024 * 1024)
    elif (unit == ""):
        return size_count
    else:
        return None

    size_in_bytes = long(size_count * multiplier)
    return size_in_bytes


def get_list(json_object, parent_node_name, child_node_name=None):
    '''
    Returns a list of values from child_node_name
    If child_node is not given, then it will retrieve list from parent node
    '''
    if(not json_object):
            return []

    return_list = []
    if isinstance(json_object[parent_node_name], list):
        for detail in json_object[parent_node_name]:
            if(child_node_name):
                return_list.append(detail[child_node_name])
            else:
                return_list.append(detail)
    else:
        if(child_node_name):
            return_list.append(json_object[parent_node_name][child_node_name])
        else:
            return_list.append(json_object[parent_node_name])

    return return_list


def get_node_value(json_object, parent_node_name, child_node_name=None):
    '''
    Returns value of given child_node. If child_node is not given, then value
    of parent node is returned.
    returns None: If json_object or parent_node is not given,
                  If child_node is not found under parent_node
    '''
    if(not json_object):
        return None

    if(not parent_node_name):
        return None

    detail = json_object[parent_node_name]
    if(not child_node_name):
        return detail

    return_value = None

    if(child_node_name in detail):
        return_value = detail[child_node_name]
    else:
        return_value = None

    return return_value


def to_stringmap_list(stringmap):
    entry = list()
    for mapentry in stringmap:
        (name, value) = mapentry.split('=', 1)
        entry.append({'name': name,
                      'value': value})
    return entry


def list_by_hrefs(ipAddr, port, hrefs):
    '''
    This is the function will take output of list method of idividual object
    that contains list of href link.
    Extract the href and get the object details and append to list
    then return the list contain object details
    '''
    output = []
    for link in hrefs:
        href = link['link']
        hrefuri = href['href']
        # we need keep except to over exception from appliance,
        # later we can take off
        try:
            (s, h) = service_json_request(ipAddr, port,
                                          "GET",
                                          hrefuri, None, None)
            o = json_decode(s)
            if(o['inactive'] is False):
                output.append(o)
        except:
            pass
    return output


def show_by_href(ipAddr, port, href):
    '''
    This function will get the href of object and display the details
    of the same
    '''
    link = href['link']
    hrefuri = link['href']
    # we need keep except to over exception from appliance,
    # later we can take off
    try:
        (s, h) = service_json_request(ipAddr, port, "GET",
                                      hrefuri, None, None)
        o = json_decode(s)
        if(o['inactive']):
            return None
        return o
    except:
        pass
    return None


def create_file(file_path):
    '''
    Create a file in the specified path.
    If the file_path is not an absolute pathname, create the file from the
    current working directory.
    raise exception : Incase of any failures.
    returns True: Incase of successful creation of file
    '''
    fd = None
    try:
        if (file_path):
            if (os.path.exists(file_path)):
                if (os.path.isfile(file_path)):
                    return True
                else:
                    raise SOSError(SOSError.NOT_FOUND_ERR,
                                   file_path + ": Not a regular file")
            else:
                dir = os.path.dirname(file_path)
                if (dir and not os.path.exists(dir)):
                    os.makedirs(dir)
            fd = os.open(file_path, os.O_RDWR | os.O_CREAT,
                         stat.S_IREAD | stat.S_IWRITE |
                         stat.S_IRGRP | stat.S_IROTH)

    except OSError as e:
        raise e
    except IOError as e:
        raise e
    finally:
        if(fd):
            os.close(fd)
    return True


def getenv(envvarname, envdefaultvalue=None):
    sosenv = None
    configfile = None
    soscli_abs_path = None

    try:
        if (envvarname is None):
            return None
        sosenv = os.getenv(envvarname)
        if (sosenv is None):
            soscli_dir_path = \
            os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            if sys.platform.startswith('win'):
                filename = 'viprcli.profile.bat'
            else:
                filename = 'viprcli.profile'
            # read the value from viprcli.profile
            soscli_abs_path = os.path.join(soscli_dir_path, filename)
            # In new installation, viprcli.profile file is in one level above.
            # still keeping the above check for backward compatibility.
            if not os.path.isfile(soscli_abs_path):
                soscli_dir_path = \
                os.path.dirname(os.path.dirname(soscli_dir_path))
                soscli_abs_path = os.path.join(soscli_dir_path, filename)
        
            if not os.path.isfile(soscli_abs_path):
                soscli_dir_path = os.path.dirname(os.path.abspath(sys.argv[0]))
                  
            configfile = open(soscli_abs_path, "r")
            if (configfile):
                line = configfile.readline()
                while line:
                    if (line[0] == '#'):
                        line = configfile.readline()
                        continue
                    if sys.platform.startswith('win'):
                        exportenvname = 'set ' + envvarname
                    else:
                        exportenvname = 'export ' + envvarname
                    if (line.startswith(envvarname) or
                            line.startswith(exportenvname)):
                        sosenvval = None
                        (word, sosenvval) = line.rsplit('=', 1)
                        if sys.platform.startswith('win'):
                            exportenvvarname = 'set[ \t]+' + envvarname
                        else:
                            exportenvvarname = 'export[ \t]+' + envvarname
                        if ((word == envvarname) or
                                (re.match(exportenvvarname, word))):
                            sosenv = sosenvval.rstrip()
                    line = configfile.readline()
    except OSError as e:
        raise e
    except IOError as e:
        pass
    finally:
        if (configfile):
            configfile.close()
        if (sosenv is None) or (len(sosenv) <= 0):
            if (envdefaultvalue):
                sosenv = envdefaultvalue
            else:
                sosenv = None
        if (envvarname == 'VIPR_HOSTNAME'):
            if (sosenv) and (sosenv != 'localhost'):
                return sosenv
            sosenv = socket.getfqdn()
            if (sosenv is None):
                sosenv = 'localhost'
        elif (envvarname == 'VIPR_PORT'):
            if (sosenv is None):
                sosenv = '4443'
        elif (envvarname == 'VIPR_UI_PORT'):
            if (sosenv is None):
                sosenv = '443'
        elif (envvarname == 'VIPR_CLI_INSTALL_DIR'):
            if (sosenv is None):
                sosenv = soscli_dir_path         
    return sosenv

'''
Prompt the user to get the confirmation
action could be "restart service", "reboot node", "poweroff cluster" etc
'''


def ask_continue(action):
    print("Do you really want to " + action + "(y/n)?:")
    response = sys.stdin.readline().rstrip()

    while(str(response) != "y" and str(response) != "n"):
        response = ask_continue(action)

    return response


# This method defines the standard and consistent error message format
# for all CLI error messages.
#
# Use it for any error message to be formatted
'''
@operationType create, update, add, etc
@component storagesystem, filesystem, vpool, etc
@errorCode Error code from the API call
@errorMessage Detailed error message
'''


def format_err_msg_and_raise(operationType, component,
                             errorMessage, errorCode):
    formatedErrMsg = "Error: Failed to " + operationType + " " + component
    if(errorMessage.startswith("\"\'") and errorMessage.endswith("\'\"")):
        # stripping the first 2 and last 2 characters, which are quotes.
        errorMessage = errorMessage[2:len(errorMessage) - 2]

    formatedErrMsg = formatedErrMsg + "\nReason:" + errorMessage
    raise SOSError(errorCode, formatedErrMsg)

'''
Terminate the script execution with status code.
Ignoring the exit status code means the script execution completed successfully
exit_status_code = 0, means success, its a default behavior
exit_status_code = integer greater than zero, abnormal termination
'''


def exit_gracefully(exit_status_code):
    sys.exit(exit_status_code)

'''
Reads the password from the standard console
TODO : Use this method to read the password throught the CLI module
'''


def get_password(componentName):
        if sys.stdin.isatty():
            passwd = getpass.getpass(
                prompt="Enter password of the " + componentName + ": ")
        else:
            passwd = sys.stdin.readline().rstrip()

        if (len(passwd) > 0):
            if sys.stdin.isatty():
                confirm_passwd = getpass.getpass(prompt="Retype password: ")
            else:
                confirm_passwd = sys.stdin.readline().rstrip()
            if (confirm_passwd != passwd):
                raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " +
                               sys.argv[1] + " " + sys.argv[2] +
                               ": error: Passwords mismatch")
        else:
            raise SOSError(SOSError.CMD_LINE_ERR, sys.argv[0] + " " +
                           sys.argv[1] + " " + sys.argv[2] +
                           ": error: Invalid password")

        return passwd


'''
Given the project name and component name, the search will be performed to find
if the component with the given name exists or not. If found, the uri of the
component will be returned
'''


def search_by_project_and_name(projectName, componentName, searchUri,
                               ipAddr, port):

        # check if the URI passed has both project and name parameters
        strUri = str(searchUri)
        if(strUri.__contains__("search") and
           strUri.__contains__("?project=") and strUri.__contains__("&name=")):
            # Get the project URI
            from project import Project
            proj_obj = Project(ipAddr, port)
            project_uri = proj_obj.project_query(projectName)

            (s, h) = service_json_request(
                ipAddr, port, "GET",
                searchUri.format(project_uri, componentName), None)

            o = json_decode(s)
            if not o:
                return None

            resources = get_node_value(o, "resource")
            if(len(resources) > 0):
                component_uri = resources[0]['id']
                return component_uri
        else:
            raise SOSError(SOSError.VALUE_ERR, "Search URI " + strUri +
                           " is not in the expected format, it should end" +
                           " with ?project={0}&name={1}")


'''
Fetches the list of resources in a given project
Parameter projectName : Name of the project in which resources to be searched
Parameter resourceSearchUri : resource type uri
( volume search uri, fileshare search uri etc )
'''


def search_by_project(projectName, resourceSearchUri, ipAddr, port):
        # check if the URI passed has both project and name parameters
        strUri = str(resourceSearchUri)
        if(strUri.__contains__("search") and strUri.__contains__("?project=")):
            # Get the project URI
            from project import Project
            proj_obj = Project(ipAddr, port)
            project_uri = proj_obj.project_query(projectName)

            (s, h) = service_json_request(
                ipAddr, port, "GET",
                resourceSearchUri.format(project_uri), None)

            o = json_decode(s)
            if not o:
                return None

            resources = get_node_value(o, "resource")

            resource_uris = []
            for resource in resources:
                resource_uris.append(resource["id"])
            return resource_uris
        else:
            raise SOSError(SOSError.VALUE_ERR, "Search URI " + strUri +
                           " is not in the expected format, it should end" +
                           " with ?project={0}")


'''
Fetches the list of resources with a given tag
Parameter resourceSearchUri : The tag based search uri.
                              Example: '/block/volumes/search?tag=tagexample1'
'''


def search_by_tag( resourceSearchUri, ipAddr, port):
        # check if the URI passed has both project and name parameters
        strUri = str(resourceSearchUri)
        if(strUri.__contains__("search") and strUri.__contains__("?tag=")):
            # Get the project URI

            (s, h) = service_json_request(
                ipAddr, port, "GET",
                resourceSearchUri, None)

            o = json_decode(s)
            if not o:
                return None

            resources = get_node_value(o, "resource")

            resource_uris = []
            for resource in resources:
                resource_uris.append(resource["id"])
            return resource_uris
        else:
            raise SOSError(SOSError.VALUE_ERR, "Search URI " + strUri +
                           " is not in the expected format, it should end" +
                           " with ?tag={0}")


# Shows information given its uri


def show_resource(ipAddr, port, componentType, uri,
                  show_inactive=False, xml=False):

        showUriConstant = singletonURIHelperInstance.getUri(
            componentType, "show")
        if(xml):
            (s, h) = service_json_request(ipAddr, port,
                                          "GET",
                                          showUriConstant.format(uri),
                                          None, None, xml)
            return s

        (s, h) = service_json_request(ipAddr, port,
                                      "GET",
                                      showUriConstant.format(uri),
                                      None)
        o = json_decode(s)
        if(show_inactive):
            return o
        inactive = get_node_value(o, 'inactive')
        if(inactive):
            return None
        return o


# Timeout handler for synchronous operations
def timeout_handler():
    global IS_TASK_TIMEOUT
    IS_TASK_TIMEOUT = True


# Blocks the operation until the task is complete/error out/timeout
def block_until_complete(componentType, resource_uri, task_id, ipAddr, port,synctimeout=0):
        global IS_TASK_TIMEOUT
        IS_TASK_TIMEOUT = False
        if synctimeout:
            t = Timer(synctimeout, timeout_handler)
        else:
            t = Timer(300, timeout_handler)
        t.start()
        while(True):
            out = get_task_by_resourceuri_and_taskId(
                componentType, resource_uri, task_id, ipAddr, port)

            if(out):
                if(out["state"] == "ready"):
                    
                    # cancel the timer and return
                    t.cancel()
                    break

                # if the status of the task is 'error' then cancel the timer
                # and raise exception
                if(out["state"] == "error"):
                    # cancel the timer
                    t.cancel()
                    error_message = "Please see logs for more details"
                    if("service_error" in out and
                       "details" in out["service_error"]):
                        error_message = out["service_error"]["details"]
                    raise SOSError(SOSError.VALUE_ERR, "Task: " + task_id +
                                   " is failed with error: " + error_message)

            if(IS_TASK_TIMEOUT):
                print "Task did not complete in %d secs. Task still in progress. Please check the logs for task status"%synctimeout
                IS_TASK_TIMEOUT = False
                break
        return


'''
Returns the list of tasks for a given resource_uri
'''


def get_tasks_by_resourceuri(componentType, resource_uri, ipAddr, port):

            task_list_uri_constant = singletonURIHelperInstance.getUri(
                componentType, "tasks_list")
            (s, h) = service_json_request(
                ipAddr, port, "GET",
                task_list_uri_constant.format(resource_uri), None)
            if (not s):
                return []
            o = json_decode(s)
            res = o["task"]
            return res


'''
Returns the single task details for a given resource and its associated task
parameter task_uri_constant : The URI constant for the task
'''


def get_task_by_resourceuri_and_taskId(componentType, resource_uri,
                                       task_id, ipAddr, port):

            
            task_uri_constant = singletonURIHelperInstance.getUri(
                componentType, "task")
            (s, h) = service_json_request(
                ipAddr, port, "GET",
                task_uri_constant.format(resource_uri,task_id), None)
            if (not s):
                return None
            o = json_decode(s)
            return o


'''
Returns the tasks details for a given task id
'''

def get_tasks_by_taskid(componentType, task_id, ipAddr, port):
      
            task_uri_constant = singletonURIHelperInstance.getUri(
                componentType, "task_by_id")
            (s, h) = service_json_request(
                ipAddr, port, "GET",
                task_uri_constant.format(task_id), None)
            if (not s):
                return None
            o = json_decode(s)
            return o
        

def list_tasks(ipAddr, port, componentType, project_name,
               resource_name=None, task_id=None):

    resourceSearchUri = singletonURIHelperInstance.getUri(
        componentType, "search_by_project")

    uris = search_by_project(project_name, resourceSearchUri, ipAddr, port)

    if(resource_name):
        for uri in uris:
            resource = show_resource(ipAddr, port, componentType,
                                     uri, True)
            if(resource['name'] == resource_name):
                if(not task_id):
                    return get_tasks_by_resourceuri(
                        componentType, resource["id"], ipAddr, port)

                else:
                    res = get_task_by_resourceuri_and_taskId(
                        componentType, resource["id"], task_id, ipAddr, port)
                    if(res):
                        return res
        raise SOSError(SOSError.NOT_FOUND_ERR, "Resource with name: " +
                       resource_name + " not found")
    elif(task_id):
	for uri in uris:
	    res = get_tasks_by_taskid(componentType, task_id, ipAddr, port)
	    if(res):
	        return res
        raise SOSError(SOSError.NOT_FOUND_ERR, "Task Id: " + task_id + "not found")
    
    else:
        # resourec_name is not given, get all tasks
        all_tasks = []
        for uri in uris:
            res = get_tasks_by_resourceuri(componentType, uri, ipAddr, port)
            if(res and len(res) > 0):
                all_tasks += res
        return all_tasks


def toDict(paramList):
    if (paramList is None):
        return

    dict = {}
    for i in range(len(paramList)):
        if (i % 2 == 0):
            dict[paramList[i]] = paramList[i + 1]

    return dict


class SOSError(Exception):

    '''
    Custom exception class used to report CLI logical errors
    Attibutes:
        err_code - String error code
        err_text - String text
    '''
    SOS_FAILURE_ERR = 1
    CMD_LINE_ERR = 2
    HTTP_ERR = 3
    VALUE_ERR = 4
    NOT_FOUND_ERR = 1
    ENTRY_ALREADY_EXISTS_ERR = 5
    MAX_COUNT_REACHED = 6

    def __init__(self, err_code, err_text):
        self.err_code = err_code
        self.err_text = err_text

    def __str__(self):
        return repr(self.err_text)


from xml.dom.minidom import Document
import copy


class dict2xml(object):

    '''
    Class to convert dictionary to xml
    '''
    doc = None

    def __init__(self, structure):
        self.doc = Document()
        if len(structure) == 1:
            rootName = str(structure.keys()[0])
            self.root = self.doc.createElement(rootName)

            self.doc.appendChild(self.root)
            self.build(self.root, structure[rootName])

    def build(self, father, structure):
        if isinstance(structure, dict):
            for k in structure:
                if(k == "operationStatus"):
                    # we don't want to have operation status in xml as it
                    # corrupts the xml
                    continue
                if (k.isdigit()):
                    tmp = "mytag_" + k
                    tag = self.doc.createElement(tmp)
                else:
                    tag = self.doc.createElement(k)

                father.appendChild(tag)
                self.build(tag, structure[k])

        elif isinstance(structure, list):
            grandFather = father.parentNode
            tagName = father.tagName
            grandFather.removeChild(father)
            for l in structure:
                tag = self.doc.createElement(tagName)
                self.build(tag, l)
                grandFather.appendChild(tag)

        else:
            data = str(structure)
            tag = self.doc.createTextNode(data)
            father.appendChild(tag)

    def display(self):
        return self.doc.toprettyxml(indent="  ")

from itertools import groupby
from xml.dom.minidom import parseString
from common import dict2xml


class TableGenerator(object):

    def __init__(self, json_list, headers):
        self.json_list = json_list
        self.headers = headers
        # Printing in table format was grouping by name.
        # Tsis was eliminating rows with duplicate names. Now,
        # since ID is unique for every record, all rows are displayed
        self.headers.insert(0, 'id')
        self.width = []
        self.json_list = []
        self.rows = []

        for index in range(len(self.headers)):
            self.width.append(0)

        self.updateWidth(self.headers)

        for entry in json_list:

            exampleXML = {'module': entry}
            xml = dict2xml(exampleXML)
            yXML = parseString(xml.display())
            row = []
            tmp_str = ""

            for module in yXML.getElementsByTagName('module'):
                for header in headers:

                    (pname, header) = get_parent_child_from_xpath(header)
                    tmp_str = ""

                    if(len(module.getElementsByTagName(header)) == 1):
                        for item in module.getElementsByTagName(header):
                            if item.firstChild.nodeValue.strip() != "":
                                if(item and item.firstChild
                                   and item.firstChild.nodeValue):
                                    row.append(item.firstChild.nodeValue)
                                else:
                                    row.append("")  
                            elif item.nodeName == "attributes":
                                tmp_str = tmp_str + self.getAttributesKeyValuePairs(item)
                                tmp_str = tmp_str[0:len(tmp_str) - 1]
                                row.append(tmp_str)
                            else:
                                row.append("")
                    elif(len(module.getElementsByTagName(header)) > 1):
                        for item in module.getElementsByTagName(header):
                            if(pname):
                                if(pname == item.parentNode.nodeName):
                                    tmp_str = tmp_str + \
                                        item.firstChild.nodeValue.strip() + ","
                            else:
                                if item.firstChild.nodeValue.strip() != "":
                                    tmp_str = tmp_str + \
                                        item.firstChild.nodeValue.strip() + ","
                                elif item.nodeName == "attributes":
                                     tmp_str = tmp_str + self.getAttributesKeyValuePairs(item)
                        tmp_str = tmp_str[0:len(tmp_str) - 1]
                        row.append(tmp_str)
                    elif(len(module.getElementsByTagName(header)) == 0):
                        row.append("")

            self.updateWidth(row)
            self.rows.append(row)

    def getAttributesKeyValuePairs(self, item):
        if item.nodeName == "attributes":
            tmp_str = ""
            for key in item.getElementsByTagName("key"):
                tmp_str = tmp_str + " " + key.firstChild.nodeValue.strip()

                tmp_str = tmp_str + "=["

                for key in item.getElementsByTagName("values"):
                    tmp_str = tmp_str + key.firstChild.nodeValue.strip()
                    tmp_str = tmp_str + ","

                tmp_str = tmp_str[0:len(tmp_str) - 1]
                tmp_str = tmp_str + "],"
        return tmp_str
                                        
    def line(self, column_headers):

        output = ""
        index = 1
        for item in column_headers:
            output = output + "".join(item.ljust(self.width[index]))
            index = index + 1

        return "  " + output

    def printTable(self):

        tmpHeaders = []
        for h in self.headers:
            (pname, header) = get_parent_child_from_xpath(h)
            tmpHeaders.append(header)

        # print self.line(h.upper() for h in self.headers)
        del tmpHeaders[0]
        print self.line(h.upper() for h in tmpHeaders)
        for item in groupby(sorted(self.rows),
                            lambda item: item[1:len(self.headers)]):
            row = []
            for index in range(len(self.headers) - 1):
                row.append(item[0][index].replace("\n", "").strip(" "))

            print self.line(row)

    def printXML(self):

        for entry in self.json_list:

            exampleXML = {'item': entry}
            xml = dict2xml(exampleXML)

            print xml.display()

    def updateWidth(self, row):

        for index in range(len(self.headers)):
            if(len(row[index].replace("\n", "").strip(" ")) >
               self.width[index] - 1):
                self.width[index] = \
                    len(row[index].replace("\n", "").strip(" ")) + 1

    def s3_hmac_base64_sig(self, method, bucket, objname, uid, secret,
                           content_type, _headers, parameters_to_sign=None):
        '''
        calculate the signature for S3 request

         StringToSign = HTTP-Verb + "\n" +
         * Content-MD5 + "\n" +
         * Content-Type + "\n" +
         * Date + "\n" +
         * CanonicalizedAmzHeaders +
         * CanonicalizedResource
        '''
        buf = ""
        # HTTP-Verb
        buf += method + "\n"

        # Content-MD5, a new line is needed even if it does not exist
        md5 = self._headers.get('Content-MD5')
        if md5 is not None:
            buf += md5
        buf += "\n"

        # Content-Type, a new line is needed even if it does not exist
        if content_type is not None:
            buf += content_type
        buf += "\n"

        # Date, it should be removed if "x-amz-date" is set
        if self._headers.get("x-amz-date") is None:
            date = self._headers.get('Date')
            if date is not None:
                buf += date
        buf += "\n"

        # CanonicalizedAmzHeaders, does not support multiple headers
        # with same name
        canonicalizedAmzHeaders = []
        for header in self._headers.keys():
            if header.startswith("x-amz-"):
                canonicalizedAmzHeaders.append(header)

        canonicalizedAmzHeaders.sort()

        for name in canonicalizedAmzHeaders:
            buf += name + ":" + self._headers[name] + "\n"

        # CanonicalizedResource represents the Amazon S3 resource targeted
        # by the request.
        buf += "/"
        if bucket is not None:
            buf += bucket
        if objname is not None:
            buf += "/" + objname

        if parameters_to_sign is not None:
            para_names = sorted(parameters_to_sign.keys())
            separator = '?'
            for name in para_names:
                value = parameters_to_sign[name]
                buf += separator
                buf += name
                if value is not None and value != "":
                    buf += "=" + value
                separator = '&'

        buf = buf.encode('UTF-8')
        if BOURNE_DEBUG == '1':
            print 'message to sign with secret[%s]: %s\n' % (secret, buf)
        macer = hmac.new(secret.encode('UTF-8'), buf, hashlib.sha1)

        signature = base64.b64encode(macer.digest())
        if BOURNE_DEBUG == '1':
            print "calculated signature:" + signature

        # The signature
        _headers['Authorization'] = 'AWS ' + uid + ':' + signature

        return _headers

    def _computeMD5(self, value):
        m = hashlib.md5()
        if value is not None:
            m.update(value)
        return m.hexdigest()
