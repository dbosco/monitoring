#!/bin/bash
# SSL Certificate Monitoring Cron Job Script
# This script sets up the environment and runs the SSL monitoring

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Change to the script's directory
cd "$SCRIPT_DIR" || {
    echo "Error: Cannot change to script directory $SCRIPT_DIR"
    exit 1
}

echo "Working directory: $(pwd)"

# Function to send Slack notifications
send_slack_notification() {
    local message="$1"
    
    # Check if Slack notifications are enabled
    if [ "$SLACK_ENABLED" != "true" ]; then
        return 0
    fi
    
    local webhook_url="$SLACK_WEBHOOK_URL"
    local user_id="$SLACK_STATUS_NOTIFY_USER_ID"
    
    if [ -n "$webhook_url" ]; then
        # If user ID is specified, send as DM to that user
        if [ -n "$user_id" ]; then
            curl -X POST -H 'Content-type: application/json' \
                --data "{\"channel\":\"$user_id\",\"text\":\"$message\"}" \
                "$webhook_url" >/dev/null 2>&1
        else
            # Send to default channel
            curl -X POST -H 'Content-type: application/json' \
                --data "{\"text\":\"$message\"}" \
                "$webhook_url" >/dev/null 2>&1
        fi
    fi
}

# Load environment variables from .env file if it exists
if [ -f ".env" ]; then
    echo "Loading environment variables from .env file..."
    export $(grep -v '^#' .env | xargs)
fi

# Run setup script to ensure environment is ready
echo "Running setup to ensure environment is ready..."
./setup.sh

# Check if setup was successful
if [ $? -ne 0 ]; then
    echo "Error: Setup failed"
    exit 1
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Create logs directory if it doesn't exist
echo "Creating logs directory..."
mkdir -p logs

# Run the SSL monitoring script
echo "Starting SSL certificate monitoring..."
python3 ssl_monitor.py

# Capture exit code
EXIT_CODE=$?

# Deactivate virtual environment if it was activated
if [ -d "venv" ]; then
    deactivate
fi

# Log the result
if [ $EXIT_CODE -eq 0 ]; then
    echo "SSL monitoring completed successfully"
elif [ $EXIT_CODE -eq 2 ]; then
    echo "SSL monitoring completed with SSL certificate issues (exit code: $EXIT_CODE)"
else
    echo "SSL monitoring completed with system errors (exit code: $EXIT_CODE)"
    # Send Slack notification for system errors (not SSL issues)
    send_slack_notification "🚨 SSL Monitoring System Error\n\nExit code: $EXIT_CODE\n\nPlease check the monitoring system configuration and logs."
fi

exit $EXIT_CODE
