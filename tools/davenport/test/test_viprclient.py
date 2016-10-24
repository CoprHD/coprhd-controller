import json
import os
import unittest
from datetime import datetime

from davenport.viprclient import ViprClient
from davenport import util


# How to run:
# python -m unittest test.test_viprclient
# python -m unittest test.test_viprclient.TestViprClient.test_submit_order
# python -m unittest test.test_viprclient.TestViprClient.test_get_varray_id
# python -m unittest test.test_viprclient.TestViprClient.test_get_vpool_id
# python -m unittest test.test_viprclient.TestViprClient.test_get_project_id
# python -m unittest test.test_viprclient.TestViprClient.test_catalog
class TestViprClient(unittest.TestCase):

    def setUp(self):
        self.user = 'root'
        self.password = 'ChangeMe1!'
        self.vipr_host = '10.247.98.169'
        self.varray = 'varray1'
        self.vpool = 'vpool2'
        self.project = 'dev'
        self.target_host = 'winhost-d8-1'

    def test_submit_order(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            path_list = ['BlockStorageServices', 'CreateBlockVolume']
            varray = client.query_resource_id(self.varray, 'VirtualArray')
            vpool = client.query_resource_id(self.vpool, 'VirtualPool')
            project = client.query_resource_id(self.project, 'Project')
            params = {
                'virtualArray': varray,
                'virtualPool': vpool,
                'project': project,
                'name': "vol_davenport_" + util.get_current_time_str(),
                'numberOfVolumes': '1',
                'size': '7'
            }
            order_info = client.submit_order(path_list, params)
        print('The order info is: {0}'.format(json.dumps(order_info)))

    def test_get_varray_id(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            varray_id = client.query_varray(self.varray)
            print('The virtual array id is = {0}'.format(varray_id))

    def test_get_varray_cache(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            varray_id = client.query_varray(self.varray)
            print('The virtual array id is = {0}'.format(varray_id))
            print('Get virtual array id by cache...')
            start_time = datetime.now()
            varray_id = client.query_varray(self.varray)
            end_time = datetime.now()
            print('The virtual array id is = {0}'.format(varray_id))
            print('Cost time is {0}'.format(end_time - start_time))

    def test_get_vpool(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            vpool_id = client.query_vpool(self.vpool)
            print('The virtual pool id is = {0}'.format(vpool_id))

    @unittest.skip("Skipping...")
    def test_get_vpool_cache(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            vpool_id = client.query_vpool(self.vpool)
            print('The virtual pool id is = {0}'.format(vpool_id))
            print('Get the virtual pool id by cache...')
            start_time = datetime.now()
            vpool_id = client.query_vpool(self.vpool)
            end_time = datetime.now()
            print('The virtual pool id is = {0}'.format(vpool_id))
            print('Cost time is {0}'.format(end_time - start_time))

    def test_get_project(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            project_id = client.query_project(self.project)
            print('The project id is = {0}'.format(project_id))

    def test_get_project_cache(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            project_id = client.query_project(self.project)
            print('The project id is = {0}'.format(project_id))
            print "Get the project by cache..."
            start_time = datetime.now()
            project_id = client.query_project(self.project)
            print('The project id is = {0}'.format(project_id))
            end_time = datetime.now()
            print('Cost time is {0}'.format(end_time - start_time))

    def test_get_host(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            host_id = client.query_host(self.target_host)
            print ('The host is is {0}'.format(host_id))

    def test_get_host_cache(self):
        with ViprClient(self.user, self.password, self.vipr_host) as client:
            host_id = client.query_host(self.target_host)
            print ('The host is is {0}'.format(host_id))
            print ('Test get host cache...')
            start_time = datetime.now()
            host_id = client.query_host(self.target_host)
            end_time = datetime.now()
            print ('The host is is {0}'.format(host_id))
            print('Cost time is {0}'.format(end_time - start_time))

    def test_catalog(self):
        client = ViprClient(self.user, self.password, self.vipr_host)
        client.login()
        uuid = client.get_catalog_service_uuid(['BlockStorageServices',
                                                'CreateBlockVolume'])
        print('The service uuid = {0}'.format(uuid))
        client.logout()

    def test_vipr_client(self):
        client = ViprClient(self.user, self.password, self.vipr_host)
        (au, cookie_file) = client.login()
        print('Login successful, cookie file is: {0}'.format(cookie_file))
        # Get health content.
        health_content = client.get_health()
        print('health_content: {0}'.format(json.dumps(health_content)))
        # Get stats content.
        stats_content = client.get_stats()
        print('stats_content: {0}'.format(json.dumps(stats_content)))
        script_dir = os.path.dirname(os.path.realpath(__file__))
        # Get version.
        version = client.get_version()
        print('version: {0}'.format(version))
        logout_result = client.logout()
        print('Logout result is: {0}'.format(logout_result))

if __name__ == '__main__':
    unittest.main()
