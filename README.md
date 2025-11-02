# monitoring


./deploy.sh ask-privacera monitoring

0 9 * * * /home/ec2-user/monitoring/run_ssl_monitor.sh >> /home/ec2-user/monitoring/logs/ssl_monitor_cron.log 2>&1


## Observability


## Ranger Monitoring

# Build builder docker image
./run_docker.sh build


# Build the runtime docker image
./run_docker.sh package
./run_docker.sh build-runtime

# Run single test
./run_docker.sh --deployment-path ranger-monitoring/dont_commit_ranger_targets/pcloud-datalake run-direct


# Run long running monitoring

./run_docker.sh --deployment-path ranger-monitoring/dont_commit_ranger_targets/pcloud-datalake run-monitoring -d

./run_docker.sh package && ./run_docker.sh build-runtime && ./run_docker.sh --deployment-path ranger-monitoring/dont_commit_ranger_targets/pcloud-datalake run-monitoring -d &&  docker logs ranger-monitoring-app -f 
