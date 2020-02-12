if [ -z ${tag+x} ];
 then tag="latest";
fi
docker build -t performancedatastorage:${tag} ../ -f ./Dockerfile
if [ -z ${deploy+x} ];
  then echo "no push";
else
  docker tag performancedatastorage:${tag} localhost:5000/performancedatastorage:${tag}
  docker push localhost:5000/performancedatastorage:${tag}
fi