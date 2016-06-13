#!/bin/sh

NODE_DIR=mkrootimg.d/etc/node
NPM=${NODE_DIR}/bin/npm

rm -rf ${NODE_DIR}/lib/node_modules/on-http
rm -rf ${NODE_DIR}/lib/node_modules/on-taskgraph

$NPM install /workspace/RackHD/on-http -g
$NPM install /workspace/RackHD/on-taskgraph -g
