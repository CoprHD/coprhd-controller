import os
import json


class VolumeDao(object):
    """
    Singleton DAO object which manages a volumes data structure like below.
    {
      'docker_vol_001': {
        'name': 'docker_vol_001',
        'id': '...',
        'formatted': True,
        'exported': {'host1_uuid': 'export_group1_id',
                     'host2_uuid': 'export_group2_id', ...},
        'mounted': {'host1_uuid': 'mount_path1',
                    'host2_uuid': 'mount_path2', ...}
      },
      'docker_vol_002': {
        ...
      }
    }
    """
    BASE_PATH = os.path.dirname(os.path.abspath(__file__))
    DATA_FILE = os.path.join(BASE_PATH, '..', 'data', 'data.json')

    def __init__(self, data_file=DATA_FILE):
        self.data_file = data_file
        self.volumes = self.load()

    def get_volumes(self):
        return self.volumes

    def get_volume(self, volume_key):
        """
        Query the volume information by the given volume name.
        Args:
            volume_key: The unique name of the volume as key in the dict.
        Returns: A dict instance of the volume information.
        """
        return self.volumes.get(volume_key)

    def set_volume(self, volume_key, volume):
        self.volumes[volume_key] = volume
        self.save(self.volumes)
        return volume

    def remove_volume(self, volume_key):
        volume = self.volumes.pop(volume_key, None)
        self.save(self.volumes)
        return volume

    def is_exported_to(self, volume_key, target_host_name):
        volume = self.volumes.get(volume_key)
        if volume.get('exported'):
            return volume['exported'].get(target_host_name)
        return False

    def get_mount_path(self, volume_key, target_host_name):
        volume = self.volumes.get(volume_key)
        if volume.get('mounted') and volume['mounted'].get(target_host_name):
            return volume['mounted'][target_host_name].get('mount_point')
        return None

    def get_exported_count(self, volume_key):
        volume = self.volumes.get(volume_key)
        return len(volume['exported'].keys()) if volume.get('exported') else 0

    def load(self):
        data = {}
        if os.path.exists(self.data_file):
            with open(self.data_file, 'r') as f:
                data = json.load(f)
        return data

    def save(self, data):
        with open(self.data_file, 'w') as f:
            json.dump(data, f, encoding='utf-8', indent=2, sort_keys=False)

dao = VolumeDao()
