#!/bin/bash

# Observability Stack Startup Script
# This script handles docker compose startup with .env file support and network creation

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Default network name
DEFAULT_NETWORK="skynet"

# Function to find .env file
find_env_file() {
    # Check parent directory first (common location)
    if [ -f "$SCRIPT_DIR/../.env" ]; then
        echo "$SCRIPT_DIR/../.env"
        return 0
    fi
    
    # Check current directory
    if [ -f "$SCRIPT_DIR/.env" ]; then
        echo "$SCRIPT_DIR/.env"
        return 0
    fi
    
    return 1
}

# Function to ensure network exists
ensure_network() {
    local network_name="${DOCKER_NETWORK_NAME:-$DEFAULT_NETWORK}"
    
    if ! docker network ls | grep -qE "(^| )${network_name}( |$)"; then
        print_info "Creating Docker network '${network_name}'..."
        if docker network create "${network_name}"; then
            print_success "Network '${network_name}' created successfully"
        else
            print_error "Failed to create network '${network_name}'"
            exit 1
        fi
    else
        print_info "Network '${network_name}' already exists"
    fi
}

# Function to check required files/directories exist
check_requirements() {
    local missing=0
    
    # Check required files
    if [ ! -f "$SCRIPT_DIR/prometheus/prometheus.yml" ]; then
        print_error "Missing: prometheus/prometheus.yml"
        missing=1
    fi
    
    if [ ! -f "$SCRIPT_DIR/grafana/grafana.ini" ]; then
        print_error "Missing: grafana/grafana.ini"
        missing=1
    fi
    
    # Check required directories
    if [ ! -d "$SCRIPT_DIR/grafana/dashboards" ]; then
        print_warning "Creating grafana/dashboards directory..."
        mkdir -p "$SCRIPT_DIR/grafana/dashboards"
    fi
    
    if [ ! -d "$SCRIPT_DIR/grafana/provisioning" ]; then
        print_error "Missing: grafana/provisioning directory"
        missing=1
    fi
    
    if [ $missing -eq 1 ]; then
        print_error "Some required files or directories are missing"
        exit 1
    fi
    
    print_success "All required files and directories are present"
}

# Main execution
main() {
    print_info "Starting Observability Stack..."
    
    # Change to script directory
    cd "$SCRIPT_DIR"
    
    # Check requirements
    check_requirements
    
    # Find .env file
    ENV_FILE=""
    if find_env_file > /dev/null; then
        ENV_FILE=$(find_env_file)
        print_info "Found .env file: $ENV_FILE"
    else
        print_warning ".env file not found (checking ../.env or ./.env)"
        print_warning "Continuing without .env file (using defaults and environment variables)"
    fi
    
    # Ensure network exists
    # Load .env if exists to get network name
    if [ -n "$ENV_FILE" ]; then
        # Source the .env file to get DOCKER_NETWORK_NAME
        set -a
        source "$ENV_FILE"
        set +a
    fi
    ensure_network
    
    # Build docker compose command
    DOCKER_COMPOSE_CMD="docker compose"
    
    # Add --env-file if .env file exists
    if [ -n "$ENV_FILE" ]; then
        # Convert to relative path from current directory (macOS compatible)
        if command -v realpath >/dev/null 2>&1 && realpath --help 2>&1 | grep -q "\-\-relative-to"; then
            # Linux realpath with --relative-to support
            ENV_REL_PATH=$(realpath --relative-to="$SCRIPT_DIR" "$ENV_FILE")
        else
            # macOS/BSD compatible: use python or manual calculation
            if command -v python3 >/dev/null 2>&1; then
                ENV_REL_PATH=$(python3 -c "import os; print(os.path.relpath('$ENV_FILE', '$SCRIPT_DIR'))")
            else
                # Fallback: use the absolute path (docker compose accepts both)
                ENV_REL_PATH="$ENV_FILE"
            fi
        fi
        DOCKER_COMPOSE_CMD="$DOCKER_COMPOSE_CMD --env-file $ENV_REL_PATH"
        print_info "Using .env file: $ENV_REL_PATH"
    fi
    
    # Pass remaining arguments to docker compose
    print_info "Running: $DOCKER_COMPOSE_CMD $*"
    
    # Execute docker compose
    eval "$DOCKER_COMPOSE_CMD" "$@"
    
    if [ $? -eq 0 ]; then
        print_success "Observability Stack started successfully!"
        echo ""
        print_info "Services available at:"
        echo "  - Grafana:    http://localhost:3000 (admin/admin)"
        echo "  - Prometheus: http://localhost:9090"
        echo "  - Jaeger:     http://localhost:16686"
    else
        print_error "Failed to start Observability Stack"
        exit 1
    fi
}

# Show usage
show_usage() {
    echo "Usage: $0 [DOCKER_COMPOSE_ARGS...]"
    echo ""
    echo "This script starts the observability stack (Prometheus, Grafana, Jaeger)"
    echo "with automatic .env file detection and network creation."
    echo ""
    echo "The script will:"
    echo "  1. Look for .env file in parent directory (../.env) or current directory (./.env)"
    echo "  2. Create Docker network if it doesn't exist"
    echo "  3. Start all services using docker compose"
    echo ""
    echo "Examples:"
    echo "  $0 up -d              # Start in detached mode"
    echo "  $0 up                 # Start in foreground"
    echo "  $0 down              # Stop all services"
    echo "  $0 restart           # Restart all services"
    echo "  $0 logs -f            # Follow logs"
    echo ""
    echo "Any docker compose arguments can be passed through."
}

# Handle help
if [[ "$1" == "--help" ]] || [[ "$1" == "-h" ]]; then
    show_usage
    exit 0
fi

# Run main function
main "$@"

