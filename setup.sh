#!/bin/bash
# SSL Certificate Monitoring Setup Script

echo "Setting up SSL Certificate Monitoring..."

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Change to the script's directory
cd "$SCRIPT_DIR" || {
    echo "Error: Cannot change to script directory $SCRIPT_DIR"
    exit 1
}

echo "Working directory: $(pwd)"

# Create virtual environment if it doesn't exist
if [ ! -d "venv" ]; then
    echo "Creating Python virtual environment..."
    python3 -m venv venv
    if [ $? -eq 0 ]; then
        echo "Virtual environment created successfully"
    else
        echo "Error: Failed to create virtual environment"
        exit 1
    fi
else
    echo "Virtual environment already exists"
fi

# Activate virtual environment
echo "Activating virtual environment..."
source venv/bin/activate

# Install requirements
echo "Installing Python requirements..."
pip3 install -r requirements.txt

if [ $? -eq 0 ]; then
    echo "Main requirements installed successfully"
else
    echo "Error: Failed to install main requirements"
    exit 1
fi

# Install dimple_utils requirements
echo "Installing dimple_utils requirements..."
pip3 install -r dimple_utils/requirements.txt

if [ $? -eq 0 ]; then
    echo "Dimple_utils requirements installed successfully"
else
    echo "Error: Failed to install dimple_utils requirements"
    exit 1
fi

echo ""
echo "Setup complete!"
echo ""
echo "Next steps:"
echo "1. Edit configs/sites.json to configure your sites"
echo "2. Test the script: ./run_ssl_monitor.sh"
echo "3. Add to crontab for daily monitoring"
echo ""
echo "Crontab entry for daily monitoring at 9 AM:"
echo "0 9 * * * $PROJECT_DIR/run_ssl_monitor.sh >> $PROJECT_DIR/ssl_monitor.log 2>&1"
