# Copyright Hybrid Logic Ltd.
# Copyright 2015 EMC Corporation
# See LICENSE file for details.

from setuptools import setup, find_packages
import codecs  # To use a consistent encoding
from os import path

# Get the long description from the relevant file
with codecs.open('DESCRIPTION.rst', encoding='utf-8') as f:
    long_description = f.read()

setup(
    name='coprhd_flocker_plugin',
    version='1.0',
    description='coprhd Backend Plugin for ClusterHQ/Flocker ',
    long_description=long_description,
    author='hemanth makaraju',
    author_email='hemanth.makaraju@emc.com',
    url='to be updated',
    license='Apache 2.0',

    classifiers=[

    'Development Status :: 4 - Beta',

    'Intended Audience :: System Administrators',
    'Intended Audience :: Developers',
    'Topic :: Software Development :: Libraries :: Python Modules',

    'License :: OSI Approved :: Apache Software License',

    # Python versions supported 
    'Programming Language :: Python :: 2.7',
    ],

    keywords='backend, plugin, flocker, docker, python',
    packages=find_packages(exclude=['test*']),
)
