#from coprhd_blockdevice import configuration
import os
import yaml
from coprhd_flocker_plugin.coprhd_blockdevice import configuration

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
    hostexportgroup = dataset['hostexportgroup']
    
    config = configuration(coprhdhost, port, username, password, tenant,project, varray, cookiedir, vpool, hostexportgroup)
    return config

#coprhd_client_for_test()
def tidy_coprhd_client_for_test(test_case):
    '''
    This Method calls coprhd_client_for_test for CoprHD Plugin API 
    Returns API of CoprHD Plugin
    '''
    client = coprhd_client_for_test()
    return client

