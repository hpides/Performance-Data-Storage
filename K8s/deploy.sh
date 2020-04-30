cd ../Docker
export tag=$(date +%s)
export deploy=true
export NODENAME=$(kubectl get nodes --no-headers | awk '{ print $1 }' | head -1)
./build.sh
cd ../K8s/
for f in manifests/*
do
  envsubst < $f | kubectl -n reqgen apply -f -
done
