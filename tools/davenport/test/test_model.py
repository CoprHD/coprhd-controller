import unittest

from davenport.model import dao


# How to run:
# python -m unittest test.test_model
class TestModel(unittest.TestCase):

    def test_save(self):
        demo = {
            'docker_vol_001': {
                'name': 'docker_vol_001',
                'id': 'test001',
                'formatted': True,
                'exported': {'host1': 'host_uuid', 'host2': 'host_uuid'},
                'mounted': {'host1': 'mount_path', 'host2': 'mount_path'}
            },
            'docker_vol_002': {
                'name': 'docker_vol_002',
                'id': 'test002',
                'formatted': True,
                'exported': {'host1': 'host_uuid', 'host2': 'host_uuid'},
                'mounted': {'host1': 'mount_path', 'host2': 'mount_path'}
            }
        }
        dao.save(demo)
        data = dao.load()
        self.assertEqual(len(demo), len(data))
        dao.remove_volume('docker_vol_001')
        dao.remove_volume('docker_vol_002')

    def test_add_volume(self):
        original_length = len(dao.get_volumes())
        dao.set_volume('test_key1', 'volume_test1')
        self.assertEqual(len(dao.volumes), original_length + 1)
        dao.remove_volume('test_key1')
        self.assertEqual(len(dao.volumes), original_length)

if __name__ == '__main__':
    unittest.main()
