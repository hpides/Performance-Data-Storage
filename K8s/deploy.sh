#
# WALT - A realistic load generator for web applications.
#
# Copyright 2020 Eric Ackermann <eric.ackermann@student.hpi.de>, Hendrik Bomhardt
# <hendrik.bomhardt@student.hpi.de>, Benito Buchheim
# <benito.buchheim@student.hpi.de>, Juergen Schlossbauer
# <juergen.schlossbauer@student.hpi.de>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
cd ../Docker
export tag=$(date +%s)
export deploy=true
if [ -z ${REGISTRY+x} ];
  then export REGISTRY=localhost:5000;
fi
printf "Using docker registry $REGISTRY"
export image=${REGISTRY}/performancedatastorage
export NODENAME=$(kubectl get nodes --no-headers | awk '{ print $1 }' | head -1)
# if not building from source, pull existing image from docker hub
if [ -z ${DEV+x} ];
  then
        export image=worldofjarcraft/performancedatastorage;
        export tag=latest;
else
  ./build.sh;
fi
echo $image
cd ../K8s/
for f in manifests/*
do
  envsubst < $f | kubectl -n ${NAMESPACE:-walt} apply -f -
done
