# monitoring


./deploy.sh ask-privacera monitoring

0 9 * * * /home/ec2-user/monitoring/run_ssl_monitor.sh >> /home/ec2-user/monitoring/logs/ssl_monitor_cron.log 2>&1