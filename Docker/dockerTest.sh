#!/bin/sh
./buildAndRun.sh -d || exit 1
sleep 30
docker exec docker_users_1 curl http://localhost:6080/tests/running || (docker logs docker_users_1 && exit 1)
