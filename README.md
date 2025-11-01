# monitoring


./deploy.sh ask-privacera monitoring

0 9 * * * /home/ec2-user/monitoring/run_ssl_monitor.sh >> /home/ec2-user/monitoring/logs/ssl_monitor_cron.log 2>&1


## Ranger Monitoring

./run_docker.sh build

./run_docker.sh package && ./run_docker.sh build-runtime && ./run_docker.sh run-monitoring
