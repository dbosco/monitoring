#!/bin/bash

# Docker-based build and run script for Ranger Monitoring
# This script provides Docker-based alternatives to the native shell script

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
PROJECT_DIR="$SCRIPT_DIR/ranger-monitoring"

# Default Ranger deployment path (can be overridden with --deployment-path or RANGER_DEPLOYMENT_PATH env var)
# Expand to absolute path if it exists
if [ -n "$RANGER_DEPLOYMENT_PATH" ] && [ -d "$RANGER_DEPLOYMENT_PATH" ]; then
    RANGER_DEPLOYMENT_PATH="$(cd "$RANGER_DEPLOYMENT_PATH" && pwd)"
elif [ -z "$RANGER_DEPLOYMENT_PATH" ]; then
    RANGER_DEPLOYMENT_PATH="$PROJECT_DIR/dont_commit_ranger_pkg"
fi

# Check if Docker is available
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    print_success "Docker is available"
}

# Build Docker builder image
build_image() {
    print_info "Building Docker builder image..."
    cd "$PROJECT_DIR"
    
    docker build -f Dockerfile.build -t ranger-monitoring-builder:latest .
    
    if [ $? -eq 0 ]; then
        print_success "Docker builder image built successfully"
    else
        print_error "Failed to build Docker builder image"
        exit 1
    fi
}

# Build Docker runtime image
build_runtime_image() {
    print_info "Building Docker runtime image..."
    cd "$PROJECT_DIR"
    
    docker build -f Dockerfile.runtime -t ranger-monitoring:latest .
    
    if [ $? -eq 0 ]; then
        print_success "Docker runtime image built successfully"
    else
        print_error "Failed to build Docker runtime image"
        exit 1
    fi
}

# Run Maven commands with Docker builder
run_maven() {
    local maven_command="$1"
    if [ -z "$maven_command" ]; then
        maven_command="clean compile"
    fi
    
    print_info "Running Maven command: $maven_command"
    
    # Check if builder image exists
    if ! docker image inspect ranger-monitoring-builder:latest &> /dev/null; then
        print_error "Builder image not found. Please run './run_docker.sh build' first"
        exit 1
    fi
    
    cd "$PROJECT_DIR"
    
    docker run --rm \
        -v "$(pwd):/workspace" \
        -v "$HOME/.m2:/root/.m2" \
        -w /workspace \
        ranger-monitoring-builder:latest \
        sh -c "mvn $maven_command"
    
    if [ $? -eq 0 ]; then
        print_success "Maven command completed successfully"
    else
        print_error "Maven command failed"
        exit 1
    fi
}

# Run with Docker directly
run_with_docker() {
    local shell_mode=false
    local java_args=()
    
    # Parse arguments for run-direct
    while [[ $# -gt 0 ]]; do
        case $1 in
            --shell)
                shell_mode=true
                shift
                ;;
            *)
                java_args+=("$1")
                shift
                ;;
        esac
    done
    
    if [ "$shell_mode" = true ]; then
        print_info "Starting container with shell..."
    else
        print_info "Running with Docker directly..."
    fi
    print_info "Using Ranger deployment path: $RANGER_DEPLOYMENT_PATH"
    
    # Validate deployment path
    if [ ! -d "$RANGER_DEPLOYMENT_PATH" ]; then
        print_error "Ranger deployment directory not found: $RANGER_DEPLOYMENT_PATH"
        exit 1
    fi
    
    if [ ! -d "$RANGER_DEPLOYMENT_PATH/ranger-conf" ]; then
        print_error "ranger-conf directory not found in deployment path: $RANGER_DEPLOYMENT_PATH/ranger-conf"
        exit 1
    fi
    
    # Ensure network exists
    ensure_network
    
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Build classpath (libraries are now in /app/lib from the image)
    CLASSPATH="/app/classes:/app/ranger-conf:/app/lib/*"
    
    # Get network name
    local network_name="${DOCKER_NETWORK_NAME:-skynet}"
    
    # Ensure logs directory exists in deployment path
    local logs_dir="$RANGER_DEPLOYMENT_PATH/logs"
    mkdir -p "$logs_dir"
    chmod 755 "$logs_dir" 2>/dev/null || true
    
    # Ensure cache directory exists (for policy cache)
    local cache_dir="$RANGER_DEPLOYMENT_PATH/cache"
    mkdir -p "$cache_dir"
    
    # Ensure audits directory exists
    local audits_dir="$RANGER_DEPLOYMENT_PATH/audits"
    mkdir -p "$audits_dir"
    
    # Build docker run command
    if [ "$shell_mode" = true ]; then
        print_info "Starting interactive shell in container..."
        print_info "You can access mounted directories at /app/ranger-conf, /app/cache, /app/logs, /app/audits"
        docker run --rm -it \
            --network "${network_name}" \
            --user 1000:1000 \
            -v "$RANGER_DEPLOYMENT_PATH/ranger-conf:/app/ranger-conf" \
            -v "$cache_dir:/app/cache" \
            -v "$logs_dir:/app/logs" \
            -v "$audits_dir:/app/audits" \
            ranger-monitoring:latest \
            /bin/bash
    else
        docker run --rm \
            --network "${network_name}" \
            --user 1000:1000 \
            -v "$RANGER_DEPLOYMENT_PATH/ranger-conf:/app/ranger-conf" \
            -v "$cache_dir:/app/cache" \
            -v "$logs_dir:/app/logs" \
            -v "$audits_dir:/app/audits" \
            ranger-monitoring:latest \
            java \
            -Dlog4j.configuration=file:/app/ranger-conf/log4j.properties \
            -Dranger.monitoring.logs.dir=/app/logs \
            -cp "$CLASSPATH" \
            com.privacera.ranger.monitoring.DummyAuthorizer "${java_args[@]}"
    fi
}

# Ensure network exists
ensure_network() {
    local network_name="${DOCKER_NETWORK_NAME:-skynet}"
    if ! docker network ls | grep -qE "(^| )${network_name}( |$)"; then
        print_info "Creating Docker network '${network_name}'..."
        docker network create "${network_name}"
        if [ $? -eq 0 ]; then
            print_success "Network '${network_name}' created successfully"
        else
            print_error "Failed to create network '${network_name}'"
            exit 1
        fi
    else
        print_info "Network '${network_name}' already exists"
    fi
}

# Run MonitoringRangerPlugin with Docker
run_monitoring() {
    local detached=false
    local java_args=()
    
    # Parse arguments for run-monitoring
    while [[ $# -gt 0 ]]; do
        case $1 in
            -d|--detached)
                detached=true
                shift
                ;;
            *)
                java_args+=("$1")
                shift
                ;;
        esac
    done
    
    print_info "Running MonitoringRangerPlugin with Docker..."
    print_info "Using Ranger deployment path: $RANGER_DEPLOYMENT_PATH"
    if [ "$detached" = true ]; then
        print_info "Running in detached mode (background)"
    fi
    
    # Validate deployment path
    if [ ! -d "$RANGER_DEPLOYMENT_PATH" ]; then
        print_error "Ranger deployment directory not found: $RANGER_DEPLOYMENT_PATH"
        exit 1
    fi
    
    if [ ! -d "$RANGER_DEPLOYMENT_PATH/ranger-conf" ]; then
        print_error "ranger-conf directory not found in deployment path: $RANGER_DEPLOYMENT_PATH/ranger-conf"
        exit 1
    fi
    
    # Ensure network exists
    ensure_network
    
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Build classpath (libraries are now in /app/lib from the image)
    CLASSPATH="/app/classes:/app/ranger-conf:/app/lib/*"
    
    # Get network name
    local network_name="${DOCKER_NETWORK_NAME:-skynet}"
    
    # Build docker run command
    local docker_cmd="docker run"
    if [ "$detached" = true ]; then
        docker_cmd="$docker_cmd -d"
        # Remove existing container if it exists (for detached mode)
        docker rm -f ranger-monitoring-app 2>/dev/null || true
        # Add container name for detached mode (easier to reference)
        docker_cmd="$docker_cmd --name ranger-monitoring-app"
    else
        docker_cmd="$docker_cmd --rm"
        # For non-detached mode, we can still use a name but it's less critical
        # Skip --name when using --rm to avoid conflicts on quick re-runs
    fi
    
    # Add network connection
    docker_cmd="$docker_cmd --network ${network_name}"
    
    # Ensure logs directory exists in deployment path
    local logs_dir="$RANGER_DEPLOYMENT_PATH/logs"
    mkdir -p "$logs_dir"
    chmod 755 "$logs_dir" 2>/dev/null || true
    
    # Ensure cache directory exists (for policy cache)
    local cache_dir="$RANGER_DEPLOYMENT_PATH/cache"
    mkdir -p "$cache_dir"
    
    # Ensure audits directory exists
    local audits_dir="$RANGER_DEPLOYMENT_PATH/audits"
    mkdir -p "$audits_dir"
    
    $docker_cmd \
        --user 1000:1000 \
        -v "$RANGER_DEPLOYMENT_PATH/ranger-conf:/app/ranger-conf" \
        -v "$cache_dir:/app/cache" \
        -v "$logs_dir:/app/logs" \
        -v "$audits_dir:/app/audits" \
        ranger-monitoring:latest \
        java \
        -Dlog4j.configuration=file:/app/ranger-conf/log4j.properties \
        -Dranger.monitoring.logs.dir=/app/logs \
        -cp "$CLASSPATH" \
        com.privacera.ranger.monitoring.MonitoringRangerPlugin "${java_args[@]}"
    
    if [ "$detached" = true ]; then
        print_success "MonitoringRangerPlugin started in detached mode"
        print_info "Container name: ranger-monitoring-app"
        print_info "Network: ${network_name}"
        print_info "Deployment path: $RANGER_DEPLOYMENT_PATH"
        print_info "Use 'docker ps' to see running containers"
        print_info "Use 'docker logs ranger-monitoring-app' to view logs"
        print_info "Use 'docker stop ranger-monitoring-app' to stop the container"
    fi
}

# Run shell with Docker (same mounts as run-direct/run-monitoring)
run_shell() {
    print_info "Starting container with shell..."
    print_info "Using Ranger deployment path: $RANGER_DEPLOYMENT_PATH"
    
    # Validate deployment path
    if [ ! -d "$RANGER_DEPLOYMENT_PATH" ]; then
        print_error "Ranger deployment directory not found: $RANGER_DEPLOYMENT_PATH"
        exit 1
    fi
    
    if [ ! -d "$RANGER_DEPLOYMENT_PATH/ranger-conf" ]; then
        print_error "ranger-conf directory not found in deployment path: $RANGER_DEPLOYMENT_PATH/ranger-conf"
        exit 1
    fi
    
    # Ensure network exists
    ensure_network
    
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Get network name
    local network_name="${DOCKER_NETWORK_NAME:-skynet}"
    
    # Ensure logs directory exists in deployment path
    local logs_dir="$RANGER_DEPLOYMENT_PATH/logs"
    mkdir -p "$logs_dir"
    chmod 755 "$logs_dir" 2>/dev/null || true
    
    # Ensure cache directory exists (for policy cache)
    local cache_dir="$RANGER_DEPLOYMENT_PATH/cache"
    mkdir -p "$cache_dir"
    
    # Ensure audits directory exists
    local audits_dir="$RANGER_DEPLOYMENT_PATH/audits"
    mkdir -p "$audits_dir"
    
    print_info "Starting interactive shell in container..."
    print_info "Deployment path: $RANGER_DEPLOYMENT_PATH"
    print_info "You can access mounted directories at /app/ranger-conf, /app/cache, /app/logs, /app/audits"
    
    docker run --rm -it \
        --network "${network_name}" \
        --user 1000:1000 \
        -v "$RANGER_DEPLOYMENT_PATH/ranger-conf:/app/ranger-conf" \
        -v "$cache_dir:/app/cache" \
        -v "$logs_dir:/app/logs" \
        -v "$audits_dir:/app/audits" \
        ranger-monitoring:latest \
        /bin/bash
}

# Run tests with Docker
run_tests() {
    print_info "Running tests with Docker..."
    cd "$PROJECT_DIR"
    
    docker run --rm \
        -v "$PROJECT_DIR/src:/app/src" \
        -v "$PROJECT_DIR/target:/app/target" \
        ranger-monitoring:latest \
        mvn test
}

# Clean up Docker resources
cleanup() {
    print_info "Cleaning up Docker resources..."
    
    # Stop and remove containers
    docker stop ranger-monitoring-app 2>/dev/null || true
    docker rm ranger-monitoring-app 2>/dev/null || true
    
    # Remove images
    docker rmi ranger-monitoring:latest 2>/dev/null || true
    docker rmi ranger-monitoring-builder:latest 2>/dev/null || true
    
    print_success "Cleanup completed"
}

# Show usage
show_usage() {
    echo "Usage: $0 [OPTIONS] [COMMAND]"
    echo ""
    echo "Commands:"
    echo "  build           Build Docker builder image"
    echo "  build-runtime   Build Docker runtime image"
    echo "  compile         Compile the Java application"
    echo "  test            Run tests"
    echo "  package         Build the application package"
    echo "  maven CMD       Run custom Maven command"
    echo "  run-direct      Run DummyAuthorizer with Docker directly (supports --shell flag)"
    echo "  run-monitoring  Run MonitoringRangerPlugin (supports -d/--detached and --interval flags)"
    echo "  cleanup         Clean up Docker resources"
    echo "  shell           Open shell in container (execs into running container or starts new one with same mounts)"
    echo ""
    echo "Options:"
    echo "  --deployment-path PATH  Path to Ranger deployment folder (default: ranger-monitoring/dont_commit_ranger_pkg)"
    echo "                          This folder should contain:"
    echo "                          - ranger-conf/ (with logging.properties)"
    echo "                          - logs/ (for log output)"
    echo "                          - cache/ (for policy cache)"
    echo "                          - audits/ (for audit logs)"
    echo "  --verbose       Enable verbose output"
    echo "  --help          Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  RANGER_DEPLOYMENT_PATH   Path to Ranger deployment folder (overridden by --deployment-path)"
    echo ""
    echo "Examples:"
    echo "  $0 build                    # Build Docker builder image"
    echo "  $0 build-runtime            # Build Docker runtime image"
    echo "  $0 compile                  # Compile application"
    echo "  $0 test                     # Run tests"
    echo "  $0 maven clean package -DskipTests  # Custom Maven command"
    echo "  $0 --deployment-path /path/to/deployment run-direct  # Run with specific deployment"
    echo "  $0 run-direct --shell                 # Start container with interactive shell instead of running Java app"
    echo "  $0 --deployment-path /path/to/deployment run-direct --shell  # Start shell with specific deployment"
    echo "  $0 run-monitoring            # Run MonitoringRangerPlugin with default deployment"
    echo "  $0 --deployment-path /path/to/deployment run-monitoring --interval 30  # Run with specific deployment and interval"
    echo "  $0 run-monitoring -d         # Run MonitoringRangerPlugin in detached/background mode"
    echo "  $0 --deployment-path /path/to/deployment run-monitoring -d  # Run in detached mode with specific deployment"
    echo "  $0 cleanup                   # Clean up resources"
}

# Main script logic
main() {
    local command=""
    local verbose=false
    
    # Parse arguments - handle options first, then commands
    while [[ $# -gt 0 ]]; do
        case $1 in
            --deployment-path)
                if [ -z "$2" ]; then
                    print_error "--deployment-path requires a path argument"
                    show_usage
                    exit 1
                fi
                # Expand to absolute path
                if [ -d "$2" ]; then
                    RANGER_DEPLOYMENT_PATH="$(cd "$2" && pwd)"
                else
                    RANGER_DEPLOYMENT_PATH="$2"
                fi
                shift 2
                ;;
            --verbose)
                verbose=true
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            build|build-runtime|compile|test|package|maven|run-direct|run-monitoring|cleanup|shell)
                command="$1"
                shift
                # For run-direct and run-monitoring, collect remaining args
                if [ "$command" = "run-direct" ] || [ "$command" = "run-monitoring" ]; then
                    break
                fi
                ;;
            *)
                # Check if it might be a command we haven't seen yet (before setting command)
                if [ -z "$command" ]; then
                    print_error "Unknown option or command: $1"
                    show_usage
                    exit 1
                else
                    # Unknown option after command - for run-direct and run-monitoring, pass through
                    if [ "$command" = "run-direct" ] || [ "$command" = "run-monitoring" ]; then
                        break
                    fi
                    print_error "Unknown option: $1"
                    show_usage
                    exit 1
                fi
                ;;
        esac
    done
    
    # Enable verbose mode if requested
    if [ "$verbose" = true ]; then
        set -x
    fi
    
    # Check Docker availability
    check_docker
    
    # Execute command
    case $command in
        build)
            build_image
            ;;
        build-runtime)
            build_runtime_image
            ;;
        compile)
            run_maven "clean compile"
            ;;
        test)
            run_maven "test"
            ;;
        package)
            run_maven "clean package"
            run_maven "dependency:copy-dependencies -DincludeScope=compile"
            ;;
        maven)
            shift # Remove "maven" from arguments
            run_maven "$*"
            ;;
        run-direct)
            run_with_docker "$@"
            ;;
        run-monitoring)
            run_monitoring "$@"
            ;;
        cleanup)
            cleanup
            ;;
        shell)
            # Try to exec into running container first, otherwise start a new one
            if docker ps --format '{{.Names}}' | grep -qE '^ranger-monitoring-app$'; then
                print_info "Opening shell in running container..."
                docker exec -it ranger-monitoring-app /bin/bash
            else
                print_warning "No running container found. Starting new container with shell..."
                run_shell
            fi
            ;;
        "")
            print_error "No command specified"
            show_usage
            exit 1
            ;;
        *)
            print_error "Unknown command: $command"
            show_usage
            exit 1
            ;;
    esac
}

# Run main function
main "$@"
