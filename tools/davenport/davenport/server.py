from flask import Flask
from flask import request
from flask import jsonify

import logging
from logging.handlers import RotatingFileHandler

from davenport.model import dao
from davenport.controller import Controller

app = Flask(__name__)

vipr = {'user': None, 'password': None, 'host': None}


@app.route('/<operation>', methods=['GET'])
def hello(operation):
    app.logger.info('This is a "get" method, operation={0}'.format(operation))
    app.logger.info('Request = {0}'.format(request))
    return 'Please use POST method to operate "{0}" (Client: {1}).'\
        .format(operation, request.remote_addr)


@app.route('/Plugin.Activate', methods=['POST'])
def activate():
    return jsonify({'Implements': ['VolumeDriver']})


@app.route('/VolumeDriver.Create', methods=['POST'])
def create():
    """
    1. Check if the given volume name exists, if not, create the volume;
    2. Check if the given volume has been exported to this current host,
       if not, export it to the host, then mark it as exported to the host.
    The two steps have to be separated since for the second host maybe only
    the second step is needed.

    Returns: The error message needed by Docker daemon.
    """
    request_data = request.get_json(force=True)
    app.logger.info('Create request = {0}'.format(request_data))
    app.logger.info('Create request.Name = {0}'.format(request_data['Name']))
    app.logger.info('Create request.Opts = {0}'.format(request_data['Opts']))
    volume_name = request_data['Name']
    volume_opts = request_data['Opts']
    target_host_name = request.remote_addr
    app.logger.info('Target host address = {0}'.format(target_host_name))
    # 1. Check if the given volume name exists, if not, create the volume.
    existing_volume = dao.get_volume(volume_name)
    controller = Controller(vipr['user'], vipr['password'], vipr['host'])
    if not existing_volume:
        volume = controller.create_volume(volume_name, volume_opts)
        dao.set_volume(volume_name, volume)
    volume = dao.get_volume(volume_name)
    # 2. Check if the given volume has been exported to this current host,
    # if not, export it to the host, then mark it as exported to the host.
    if not dao.is_exported_to(volume_name, target_host_name):
        volume = controller.export_volume(volume, target_host_name)
        dao.set_volume(volume_name, volume)
    return jsonify({'Err': None})


@app.route('/VolumeDriver.Path', methods=['POST'])
def path():
    """
    Query the mount path of the volume on the target host and return.

    Returns: Mount path needed by Docker daemon.
    """
    request_data = request.get_json(force=True)
    app.logger.info('Path request = {0}'.format(request_data))
    app.logger.info('Path request.Name = {0}'.format(request_data['Name']))
    volume_name = request_data['Name']
    target_host_name = request.remote_addr
    app.logger.info('Target host address = {0}'.format(target_host_name))
    mount_path = dao.get_mount_path(volume_name, target_host_name)
    return jsonify({'Mountpoint': mount_path, 'Err': None})


@app.route('/VolumeDriver.Mount', methods=['POST'])
def mount():
    """
    Check if the given volume has been mounted to this current host, if not,
    mount it to the host, when doing so also check if the volume is
    formatted, if not, use "format" option in the "mount" operation. When
    the mount operation succeeds, mark it as mounted to the host, and
    mark the volume as formatted if run with "format" option.

    Returns: Mount path needed by Docker daemon.
    """
    request_data = request.get_json(force=True)
    app.logger.info('Mount request = {0}'.format(request_data))
    app.logger.info('Mount request.Name = {0}'.format(request_data['Name']))
    volume_name = request_data['Name']
    target_host_name = request.remote_addr
    app.logger.info('Target host address = {0}'.format(target_host_name))
    volume = dao.get_volume(volume_name)
    mount_path = dao.get_mount_path(volume_name, target_host_name)
    if mount_path:
        # If the volume is already mounted to the target host, just increase
        # counter.
        volume['mounted'][target_host_name]['count'] += 1
        dao.set_volume(volume_name, volume)
        return jsonify({'Mountpoint': mount_path, 'Err': None})
    else:
        # Else it means it's the first time to mount the volume to the target
        # host, do the ViPR mount operation.
        controller = Controller(vipr['user'], vipr['password'], vipr['host'])
        volume = controller.mount_volume(volume, target_host_name)
        dao.set_volume(volume_name, volume)
        mount_path = dao.get_mount_path(volume_name, target_host_name)
        return jsonify({'Mountpoint': mount_path, 'Err': None})


@app.route('/VolumeDriver.Unmount', methods=['POST'])
def unmount():
    """
    Unmount the volume from the target host.

    Returns: The error message needed by Docker daemon.
    """
    request_data = request.get_json(force=True)
    app.logger.info('Unmount request = {0}'.format(request_data))
    app.logger.info('Unmount request.Name = {0}'.format(request_data['Name']))
    volume_name = request_data['Name']
    target_host_name = request.remote_addr
    app.logger.info('Target host address = {0}'.format(target_host_name))
    volume = dao.get_volume(volume_name)
    if volume['mounted'][target_host_name]['count'] > 1:
        # If there is multiple Docker volumes on the same target host map to
        # this ViPR volume, just decrease the counter.
        volume['mounted'][target_host_name]['count'] -= 1
        dao.set_volume(volume_name, volume)
    else:
        # Else it means it's the last Docker volume mapping to the ViPR volume
        # on the target host, do the ViPR unmount operation.
        controller = Controller(vipr['user'], vipr['password'], vipr['host'])
        volume = controller.unmount_volume(volume, target_host_name)
        dao.set_volume(volume_name, volume)
    return jsonify({'Err': None})


@app.route('/VolumeDriver.Remove', methods=['POST'])
def remove():
    """
    1. Unexport the volume from the target host;
    2. Check if the current target host is the last target host of this volume,
    if yes, remove the volume.

    Returns: The error message needed by Docker daemon.
    """
    request_data = request.get_json(force=True)
    app.logger.info('Remove request = {0}'.format(request_data))
    app.logger.info('Remove request.Name = {0}'.format(request_data['Name']))
    volume_name = request_data['Name']
    target_host_name = request.remote_addr
    app.logger.info('Target host address = {0}'.format(target_host_name))
    volume = dao.get_volume(volume_name)
    controller = Controller(vipr['user'], vipr['password'], vipr['host'])
    exp_count = dao.get_exported_count(volume_name)
    if exp_count > 1:
        volume = controller.unexport_volume(volume, target_host_name)
        dao.set_volume(volume_name, volume)
        return jsonify({'Err': None})
    elif exp_count == 1:
        controller.remove_volume(volume, target_host_name)
        dao.remove_volume(volume_name)
        return jsonify({'Err': None})
    else:
        return jsonify({'Err': 'Illegal export count: {0}'.format(exp_count)})


def run(debug=False):
    handler = RotatingFileHandler('logs/davenport.log',
                                  maxBytes=1024 * 1024 * 10,
                                  backupCount=10)
    handler.setLevel(logging.INFO)
    app.logger.addHandler(handler)
    app.run('0.0.0.0', 8000, debug)
