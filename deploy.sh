#!/bin/bash
# SSL Certificate Monitoring Deployment Script
# This script syncs the required files including ranger-monitoring to a remote server

# Usage: ./deploy.sh <remote_server> <remote_path>
# Example: ./deploy.sh user@server.com /opt/ssl-monitoring

if [ $# -ne 2 ]; then
    echo "Usage: $0 <remote_server> <remote_path>"
    echo "Example: $0 user@server.com /opt/ssl-monitoring"
    exit 1
fi

REMOTE_SERVER="$1"
REMOTE_PATH="$2"

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Change to the script's directory
cd "$SCRIPT_DIR" || {
    echo "Error: Cannot change to script directory $SCRIPT_DIR"
    exit 1
}

echo "Deploying SSL Certificate Monitoring and ranger-monitoring to $REMOTE_SERVER:$REMOTE_PATH"
echo "Working directory: $(pwd)"
echo "Excluding: .bk files, target/, logs/, audits/, cache/, and dont_commit_dont_sync/ folders"

# Create rsync exclude file
EXCLUDE_FILE="/tmp/ssl_monitor_exclude.txt"
cat > "$EXCLUDE_FILE" << EOF
# Python artifacts
__pycache__/
*.pyc
*.pyo
*.pyd
.Python
*.so
*.egg
*.egg-info/
dist/
build/

# Virtual environment
venv/
env/
ENV/

# Environment files
.env
.env.local
.env.*.local

# IDE files
.vscode/
.idea/
*.swp
*.swo
*~

# OS files
.DS_Store
.DS_Store?
._*
.Spotlight-V100
.Trashes
ehthumbs.db
Thumbs.db

# Git
.git/
.gitignore

# Logs
logs/
**/logs/
*.log

# Build artifacts
target/
**/target/

# Audit logs
audits/
**/audits/

# Cache directories
cache/
**/cache/
ranger-monitoring/**/cache
ranger-monitoring/**/cache/

# Dont commit/sync folders
dont_commit_dont_sync/
**/dont_commit_dont_sync/

# Temporary files
*.tmp
*.temp
/tmp/

# Backup files
*.bak
*.backup
*.bk
**/*.bk
EOF

echo "Created exclude file: $EXCLUDE_FILE"

# Test connection to remote server
echo "Testing connection to $REMOTE_SERVER..."
if ! ssh -o ConnectTimeout=10 -o BatchMode=yes "$REMOTE_SERVER" "echo 'Connection successful'" 2>/dev/null; then
    echo "Error: Cannot connect to $REMOTE_SERVER"
    echo "Make sure SSH key authentication is set up"
    exit 1
fi

# Create remote directory if it doesn't exist
echo "Creating remote directory: $REMOTE_PATH"
ssh "$REMOTE_SERVER" "mkdir -p '$REMOTE_PATH'"

# Perform rsync
echo "Syncing files to remote server..."
rsync -avz --delete \
    --exclude-from="$EXCLUDE_FILE" \
    --progress \
    ./ "$REMOTE_SERVER:$REMOTE_PATH/"

RSYNC_EXIT_CODE=$?

# Clean up exclude file
rm -f "$EXCLUDE_FILE"

if [ $RSYNC_EXIT_CODE -eq 0 ]; then
    echo ""
    echo "✅ Deployment successful!"
    echo ""
    echo "Next steps on the remote server:"
    echo "1. SSH to the server: ssh $REMOTE_SERVER"
    echo "2. Navigate to the directory: cd $REMOTE_PATH"
    echo "3. Run setup: ./setup.sh"
    echo "4. Configure environment variables:"
    echo "   export SLACK_ENABLED=true"
    echo "   export SLACK_WEBHOOK_URL='your_webhook_url'"
    echo "   export SLACK_STATUS_NOTIFY_USER_ID='your_user_id'"
    echo "5. Test the script: ./run_ssl_monitor.sh"
    echo "6. Add to crontab for daily monitoring:"
    echo "   0 9 * * * $REMOTE_PATH/run_ssl_monitor.sh >> $REMOTE_PATH/ssl_monitor.log 2>&1"
else
    echo "❌ Deployment failed with exit code: $RSYNC_EXIT_CODE"
    exit $RSYNC_EXIT_CODE
fi
