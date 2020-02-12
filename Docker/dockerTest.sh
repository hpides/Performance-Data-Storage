#!/bin/sh
./buildAndRun.sh -d || exit 1
sleep 30
curl http://localhost:8080/tests/running
exit $?