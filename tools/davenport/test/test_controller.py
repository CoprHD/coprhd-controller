import unittest
import json

from davenport.controller import Controller
from davenport import util


# How to run:
# python -m unittest test.test_controller
# python -m unittest test.test_controller.TestController.test_create_volume
# python -m unittest test.test_controller.TestController.test_export_volume
# python -m unittest test.test_controller.TestController.test_remove_volume
class TestController(unittest.TestCase):

    def setUp(self):
        self.user = 'root'
        self.password = 'ChangeMe1!'
        # self.vipr_host = '10.247.98.169'
        # self.varray = 'varray1'
        # self.vpool = 'vpool2'
        # self.project = 'dev'
        # self.target_host = 'winhost-d8-1'
        self.vipr_host = '10.247.135.142'
        self.varray = 'docker_varray1'
        self.vpool = 'docker_vpool1'
        self.project = 'docker'
        self.target_host = '10.32.72.204'
        self.target_host_1 = 'winhost-d8-1'
        self.target_host_2 = 'winhost-d8-2'

    def test_create_volume(self):
        controller = Controller(self.user, self.password, self.vipr_host)
        volume_name = 'docker_vol_{0}'.format(util.get_current_time_str())
        params = {'virtualArray': self.varray, 'virtualPool': self.vpool,
                  'project': self.project, 'size': '2'}
        created_volume = controller.create_volume(volume_name, params)
        print('created volume: {0}'.format(json.dumps(created_volume)))

    def test_export_volume(self):
        # 1. Create a volume.
        controller = Controller(self.user, self.password, self.vipr_host)
        volume_name = 'docker_vol_{0}'.format(util.get_current_time_str())
        params = {'virtualArray': self.varray, 'virtualPool': self.vpool,
                  'project': self.project, 'size': '2'}
        volume = controller.create_volume(volume_name, params)
        print('created volume: {0}'.format(json.dumps(volume)))
        # 2. Export the volume.
        volume = controller.export_volume(volume, self.target_host)
        print('exported volume: {0}'.format(json.dumps(volume)))

    def test_remove_volume(self):
        # 1. Create a volume.
        controller = Controller(self.user, self.password, self.vipr_host)
        volume_name = 'docker_vol_{0}'.format(util.get_current_time_str())
        params = {'virtualArray': self.varray, 'virtualPool': self.vpool,
                  'project': self.project, 'size': '2'}
        volume = controller.create_volume(volume_name, params)
        print('created volume: {0}'.format(json.dumps(volume)))
        # 2. Export the volume to host 1.
        volume = controller.export_volume(volume, self.target_host_1)
        print('exported volume(round 1): {0}'.format(json.dumps(volume)))
        # 3. Export the volume to host 2.
        volume = controller.export_volume(volume, self.target_host_2)
        print('exported volume(round 2): {0}'.format(json.dumps(volume)))
        # 4. Remove the volume from host 1(since the volume has 2 target hosts,
        # when removing it from host 1, the real operation is unexport instead
        # of real removing).
        volume = controller.unexport_volume(volume, self.target_host_1)
        print('removing volume(round 1): {0}'.format(json.dumps(volume)))
        # 5. Remove the volume from host 2.
        volume = controller.remove_volume(volume, self.target_host_2)
        print('removing volume(round 2): {0}'.format(json.dumps(volume)))

    def test_remove_volume_two(self):
        # 1. Create a volume.
        controller = Controller(self.user, self.password, self.vipr_host)
        volume_name = 'docker_vol_{0}'.format(util.get_current_time_str())
        params = {'virtualArray': self.varray, 'virtualPool': self.vpool,
                  'project': self.project, 'size': '2'}
        volume = controller.create_volume(volume_name, params)
        print('created volume: {0}'.format(json.dumps(volume)))
        # 2. Export the volume to host 1.
        volume = controller.export_volume(volume, self.target_host)
        print('exported volume(round 1): {0}'.format(json.dumps(volume)))
        # 3. Remove the volume from host 2.
        volume = controller.remove_volume(volume, self.target_host)
        print('removing volume(round 2): {0}'.format(json.dumps(volume)))
