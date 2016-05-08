# Copyright Hybrid Logic Ltd.
# Copyright 2015-2016 EMC Corporation
# See LICENSE file for details..

#from coprhd_blockdevice import configuration
import os
import yaml
from coprhd_flocker_plugin.coprhd_blockdevice import configuration,CoprHDCLIDriver

def _read_coprhd_yaml():
    '''
    Reads the yaml file and returns the file object 
    '''
    config_file_path = '/etc/flocker/agent.yml'
    with open(config_file_path, 'r') as stream:
        coprhd_conf = yaml.load(stream)
    return coprhd_conf['dataset']

    

def coprhd_client_for_test():
    '''
    This Method parse the attributes in yml file and assigns the values

    Returns the object of configuration in coprhd plugin driver
    '''
    dataset = _read_coprhd_yaml()
    coprhdhost=dataset['coprhdhost']
    port = dataset['port']
    username = dataset['username']
    password = dataset['password']
    tenant = dataset['tenant']
    project = dataset['project']
    varray = dataset['varray']
    cookiedir = dataset['cookiedir']
    vpool = dataset['vpool']
    vpool_platinum = dataset['vpool_platinum']
    vpool_gold = dataset['vpool_gold']
    vpool_silver = dataset['vpool_silver']
    vpool_bronze = dataset['vpool_bronze']
    hostexportgroup = dataset['hostexportgroup']
    coprhdcli_security_file = dataset['coprhdcli_security_file']
    cluster_id = dataset['cluster_id']
    
    config = configuration(coprhdhost, port, username, password, tenant,project, varray, cookiedir, vpool,vpool_platinum,vpool_gold,vpool_silver,vpool_bronze,hostexportgroup,coprhdcli_security_file,cluster_id)
    cli_obj = CoprHDCLIDriver(coprhdhost,port, username, password, tenant,project, varray, cookiedir, vpool,vpool_platinum,vpool_gold,vpool_silver,vpool_bronze,hostexportgroup,coprhdcli_security_file,cluster_id)
    return config,cli_obj

#coprhd_client_for_test()
def tidy_coprhd_client_for_test(test_case):
    '''
    This Method calls coprhd_client_for_test for CoprHD Plugin API 
    Returns API of CoprHD Plugin
    '''
    client,cli= coprhd_client_for_test()
    return client,cli

