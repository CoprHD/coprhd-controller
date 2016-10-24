from flask import Flask
from flask import request
from flask import jsonify

import logging
from logging.handlers import RotatingFileHandler

app = Flask(__name__)


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
    return jsonify({'Err': None})


@app.route('/VolumeDriver.Path', methods=['POST'])
def path():
    return jsonify({'Mountpoint': '/tmp', 'Err': None})


@app.route('/VolumeDriver.Mount', methods=['POST'])
def mount():
    return jsonify({'Mountpoint': '/tmp', 'Err': None})


@app.route('/VolumeDriver.Unmount', methods=['POST'])
def unmount():
    return jsonify({'Err': None})


@app.route('/VolumeDriver.Remove', methods=['POST'])
def remove():
    return jsonify({'Err': None})


def run(debug=False):
    handler = RotatingFileHandler('logs/davenport.log',
                                  maxBytes=1024 * 1024 * 10,
                                  backupCount=10)
    handler.setLevel(logging.INFO)
    app.logger.addHandler(handler)
    app.run('0.0.0.0', 8000, debug)
