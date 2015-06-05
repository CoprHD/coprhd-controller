# Copyright 2015 EMC Corporation
# All Rights Reserved

# This is python web server that serves OS install related files and intercepts
# calls to /success/* and /failure/* URLs.
# To run, type the following: python server.py
# Use double quotes for strings to avoid losing single quotes during file copy

import SocketServer
import SimpleHTTPServer
import os
import logging
import logging.handlers
import time

logger = logging.getLogger("serverlogger")
logger.setLevel(logging.INFO)
handler = logging.handlers.RotatingFileHandler("server.log", maxBytes=1000000, backupCount=10)
handler.setLevel(logging.INFO)
formatter = logging.Formatter("%(asctime)s - %(message)s")
handler.setFormatter(formatter)
logger.addHandler(handler)

PORT = {http.port}

class CustomHandler(SimpleHTTPServer.SimpleHTTPRequestHandler):
    def do_GET(self):
        arr = self.path.split("/")
        host = self.client_address[0]
        if arr[1]=="success":
            logger.info("%s, success, %s", host, arr[2])
            f = open("success/"+arr[2], "w")
            f.write("done")
            f.close()
            if os.path.isfile("../pxelinux.cfg/"+arr[2]):
                os.remove("../pxelinux.cfg/"+arr[2])
            if os.path.isfile("../pxelinux.cfg/"+arr[2]+".boot.cfg"):
                os.remove("../pxelinux.cfg/"+arr[2]+".boot.cfg")
            self.respondOk()
            return
        elif arr[1]=="failure":
            logger.info("%s, failure, %s", host, arr[2])
            f = open("failure/"+arr[2], "w")
            f.write("failed")
            f.close()
            self.respondOk()
            return

        logger.info("%s, get %s", host, self.path)
        SimpleHTTPServer.SimpleHTTPRequestHandler.do_GET(self)

    # override this for faster response (original method causes delay of several seconds)
    def log_request(self, code=None, size=None):
        if code != 200:
            logger.info("error %s", str(code))
        return

    # just send ok
    def respondOk(self):
        self.send_response(200)
        self.send_header("Content-type","text/html")
        self.end_headers()
        self.wfile.write("ok")

httpd = None
connected = False
attempt = 0
while (attempt < 20):
    try:
        httpd = SocketServer.ThreadingTCPServer(("", PORT),CustomHandler)
        connected = True
        break
    except Exception, e:
        attempt = attempt + 1
        logger.info("cant connect, attempt %s", attempt)
        time.sleep(5)

if connected == True:
    logger.info("server started")
    try:
        logger.info("serving at port %s", PORT)
        httpd.serve_forever()
    except KeyboardInterrupt:
        logger.info("shutting down server")
        httpd.socket.close()
else:
    logger.info("cant start server, exiting!")
