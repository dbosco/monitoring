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

# Check if Docker is available
check_docker() {
    if ! command -v docker &> /dev/null; then
        print_error "Docker is not installed or not in PATH"
        exit 1
    fi
    
    # Check for modern docker compose (v2) or legacy docker-compose (v1)
    if ! docker compose version &> /dev/null && ! command -v docker-compose &> /dev/null; then
        print_error "Docker Compose is not installed or not in PATH"
        exit 1
    fi
    
    print_success "Docker and Docker Compose are available"
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

# Run with Docker Compose
run_with_compose() {
    print_info "Running MonitoringRangerPlugin with Docker Compose..."
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Use modern docker compose command if available, otherwise fall back to docker-compose
    if docker compose version &> /dev/null; then
        docker compose up
    else
        docker-compose up
    fi
}

# Run with Docker directly
run_with_docker() {
    print_info "Running with Docker directly..."
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Check if lib directory exists
    if [ ! -d "$PROJECT_DIR/dont_commit_ranger_pkg/lib" ]; then
        print_error "Ranger lib directory not found: $PROJECT_DIR/dont_commit_ranger_pkg/lib"
        exit 1
    fi
    
    # Build classpath
    CLASSPATH="/app/target/classes:/app/dont_commit_ranger_pkg/ranger-conf"
    
    # Add all JAR files from Ranger lib directory
    for jar in dont_commit_ranger_pkg/lib/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:/app/dont_commit_ranger_pkg/lib/$(basename "$jar")"
        fi
    done
    
    docker run --rm \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/ranger-conf:/app/dont_commit_ranger_pkg/ranger-conf" \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/lib:/app/dont_commit_ranger_pkg/lib" \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/cache:/app/cache" \
        -v "$PROJECT_DIR/target/classes:/app/target/classes" \
        ranger-monitoring:latest \
        java -cp "$CLASSPATH" com.privacera.ranger.monitoring.DummyAuthorizer "$@"
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
    if [ "$detached" = true ]; then
        print_info "Running in detached mode (background)"
    fi
    cd "$PROJECT_DIR"
    
    # Check if runtime image exists
    if ! docker image inspect ranger-monitoring:latest &> /dev/null; then
        print_warning "Runtime image not found. Building it now..."
        build_runtime_image
    fi
    
    # Check if lib directory exists
    if [ ! -d "$PROJECT_DIR/dont_commit_ranger_pkg/lib" ]; then
        print_error "Ranger lib directory not found: $PROJECT_DIR/dont_commit_ranger_pkg/lib"
        exit 1
    fi
    
    # Build classpath
    CLASSPATH="/app/target/classes:/app/dont_commit_ranger_pkg/ranger-conf"
    
    # Add all JAR files from Ranger lib directory
    for jar in dont_commit_ranger_pkg/lib/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:/app/dont_commit_ranger_pkg/lib/$(basename "$jar")"
        fi
    done
    
    # Build docker run command
    local docker_cmd="docker run"
    if [ "$detached" = true ]; then
        docker_cmd="$docker_cmd -d"
    else
        docker_cmd="$docker_cmd --rm"
    fi
    
    $docker_cmd \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/ranger-conf:/app/dont_commit_ranger_pkg/ranger-conf" \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/lib:/app/dont_commit_ranger_pkg/lib" \
        -v "$PROJECT_DIR/dont_commit_ranger_pkg/cache:/app/cache" \
        -v "$PROJECT_DIR/target/classes:/app/target/classes" \
        ranger-monitoring:latest \
        java -cp "$CLASSPATH" com.privacera.ranger.monitoring.MonitoringRangerPlugin "${java_args[@]}"
    
    if [ "$detached" = true ]; then
        print_success "MonitoringRangerPlugin started in detached mode"
        print_info "Use 'docker ps' to see running containers"
        print_info "Use 'docker logs <container_id>' to view logs"
    fi
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
    
    # Remove containers using modern docker compose command if available
    if docker compose version &> /dev/null; then
        docker compose -f "$PROJECT_DIR/docker-compose.yml" down 2>/dev/null || true
    else
        docker-compose -f "$PROJECT_DIR/docker-compose.yml" down 2>/dev/null || true
    fi
    
    # Remove image
    docker rmi ranger-monitoring:latest 2>/dev/null || true
    docker rmi ranger-monitoring-builder:latest 2>/dev/null || true
    
    # Remove volumes
    docker volume rm ranger-monitoring_ranger-cache 2>/dev/null || true
    docker volume rm ranger-monitoring_ranger-temp-cache 2>/dev/null || true
    
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
    echo "  run             Run MonitoringRangerPlugin with Docker Compose"
    echo "  run-direct      Run with Docker directly"
    echo "  run-monitoring  Run MonitoringRangerPlugin (supports -d/--detached and --interval flags)"
    echo "  cleanup         Clean up Docker resources"
    echo "  shell           Open shell in running container"
    echo ""
    echo "Options:"
    echo "  --verbose       Enable verbose output"
    echo "  --help          Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 build                    # Build Docker builder image"
    echo "  $0 build-runtime            # Build Docker runtime image"
    echo "  $0 compile                  # Compile application"
    echo "  $0 test                     # Run tests"
    echo "  $0 maven clean package -DskipTests  # Custom Maven command"
    echo "  $0 run                       # Run MonitoringRangerPlugin with Docker Compose"
    echo "  $0 run-monitoring            # Run MonitoringRangerPlugin with default 60s interval"
    echo "  $0 run-monitoring --interval 30  # Run MonitoringRangerPlugin with 30s interval"
    echo "  $0 run-monitoring -d         # Run MonitoringRangerPlugin in detached/background mode"
    echo "  $0 run-monitoring -d --interval 30  # Run in detached mode with 30s interval"
    echo "  $0 cleanup                   # Clean up resources"
}

# Main script logic
main() {
    local command=""
    local verbose=false
    
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            build|build-runtime|compile|test|package|maven|run|run-direct|run-monitoring|cleanup|shell)
                command="$1"
                shift
                ;;
            --verbose)
                verbose=true
                shift
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                # For run-monitoring, collect remaining args
                if [ "$command" = "run-monitoring" ]; then
                    break
                fi
                print_error "Unknown option: $1"
                show_usage
                exit 1
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
            ;;
        maven)
            shift # Remove "maven" from arguments
            run_maven "$*"
            ;;
        run)
            run_with_compose
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
            print_info "Opening shell in running container..."
            docker exec -it ranger-monitoring-app /bin/bash
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
