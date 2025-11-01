#!/bin/bash

# DummyAuthorizer Runner Script
# This script runs the DummyAuthorizer with Ranger libraries and configuration

set -e  # Exit on any error
set -x
# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/ranger-monitoring"
RANGER_LIB_DIR="$PROJECT_DIR/dont_commit_ranger_pkg/lib"
RANGER_CONF_DIR="$PROJECT_DIR/dont_commit_ranger_pkg/ranger-conf"

# Java class
MAIN_CLASS="com.privacera.ranger.monitoring.DummyAuthorizer"

# Function to print colored output
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

# Function to check if directory exists
check_directory() {
    if [ ! -d "$1" ]; then
        print_error "Directory does not exist: $1"
        exit 1
    fi
}

# Function to build classpath
build_classpath() {
    local classpath=""
    
    # Add compiled classes from target/classes
    if [ -d "$PROJECT_DIR/target/classes" ]; then
        classpath="$PROJECT_DIR/target/classes"
    else
        print_error "Compiled classes not found. Please run 'mvn compile' first."
        exit 1
    fi
    
    classpath="$classpath:$RANGER_CONF_DIR"
    # Add all JAR files from Ranger lib directory (excluding problematic JNA libraries)
    if [ -d "$RANGER_LIB_DIR" ]; then
        for jar in "$RANGER_LIB_DIR"/*.jar; do
            if [ -f "$jar" ]; then
                # Include all libraries - we'll handle JNA architecture issues with system properties
                # No longer skipping JNA libraries since gethostname4j needs them
                
                if [ -z "$classpath" ]; then
                    classpath="$jar"
                else
                    classpath="$classpath:$jar"
                fi
            fi
        done
    else
        print_error "Ranger lib directory not found: $RANGER_LIB_DIR"
        exit 1
    fi
    
    echo "$classpath"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS] [ARGS...]"
    echo ""
    echo "Options:"
    echo "  -h, --help     Show this help message"
    echo "  -c, --compile  Compile the project before running"
    echo "  -v, --verbose  Enable verbose output"
    echo "  -t, --test     Run tests instead of main class"
    echo ""
    echo "Arguments:"
    echo "  Any arguments will be passed to the DummyAuthorizer main method"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Run with default test data"
    echo "  $0 --compile                          # Compile and run"
    echo "  $0 user1 group1 database1 table1 col1 # Run with custom parameters"
    echo "  $0 --test                             # Run unit tests"
}

# Parse command line arguments
COMPILE=false
VERBOSE=false
RUN_TESTS=false
ARGS=()

while [[ $# -gt 0 ]]; do
    case $1 in
        -h|--help)
            show_usage
            exit 0
            ;;
        -c|--compile)
            COMPILE=true
            shift
            ;;
        -v|--verbose)
            VERBOSE=true
            shift
            ;;
        -t|--test)
            RUN_TESTS=true
            shift
            ;;
        -*)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
        *)
            ARGS+=("$1")
            shift
            ;;
    esac
done

# Main execution
main() {
    print_info "Starting DummyAuthorizer Runner..."
    
    # Check required directories
    check_directory "$PROJECT_DIR"
    check_directory "$RANGER_LIB_DIR"
    check_directory "$RANGER_CONF_DIR"
    
    # Compile if requested
    if [ "$COMPILE" = true ]; then
        print_info "Compiling project..."
        cd "$PROJECT_DIR"
        mvn clean compile
        if [ $? -eq 0 ]; then
            print_success "Compilation completed successfully"
        else
            print_error "Compilation failed"
            exit 1
        fi
    fi
    
    # Build classpath
    print_info "Building classpath..."
    CLASSPATH=$(build_classpath)
    
    if [ "$VERBOSE" = true ]; then
        print_info "Classpath: $CLASSPATH"
    fi
    
    # Create logs directory if it doesn't exist
    LOGS_DIR="$PROJECT_DIR/logs"
    mkdir -p "$LOGS_DIR"
    
    # Set Java system properties for Ranger configuration
    JAVA_OPTS="-Dranger.service.name=hive"
    JAVA_OPTS="$JAVA_OPTS -Dranger.service.host=localhost"
    JAVA_OPTS="$JAVA_OPTS -Dranger.service.port=6080"
    JAVA_OPTS="$JAVA_OPTS -Dranger.service.user=ranger"
    JAVA_OPTS="$JAVA_OPTS -Dranger.service.password=ranger"
    JAVA_OPTS="$JAVA_OPTS -Dranger.policy.cache.dir=/tmp/ranger-policy-cache"
    JAVA_OPTS="$JAVA_OPTS -Dranger.temp.cache.dir=/tmp/ranger-temp-cache"
    JAVA_OPTS="$JAVA_OPTS -Dranger.audit.solr.urls=http://localhost:6083/solr/ranger_audits"
    
    # Logging configuration - use java.util.logging since log4j jars are not available
    if [ -f "$RANGER_CONF_DIR/logging.properties" ]; then
        JAVA_OPTS="$JAVA_OPTS -Djava.util.logging.config.file=$RANGER_CONF_DIR/logging.properties"
    elif [ -f "$RANGER_CONF_DIR/log4j.properties" ]; then
        # Try log4j if available (but won't work without log4j jars)
        JAVA_OPTS="$JAVA_OPTS -Dlog4j.configuration=file:$RANGER_CONF_DIR/log4j.properties"
    fi
    JAVA_OPTS="$JAVA_OPTS -Dranger.monitoring.logs.dir=$LOGS_DIR"
    JAVA_OPTS="$JAVA_OPTS -Djava.awt.headless=true"
    
    # Run tests or main class
    cd "$PROJECT_DIR"
    
    if [ "$RUN_TESTS" = true ]; then
        print_info "Running unit tests..."
        mvn test
        if [ $? -eq 0 ]; then
            print_success "All tests passed!"
        else
            print_error "Some tests failed"
            exit 1
        fi
    else
        print_info "Running DummyAuthorizer..."
        print_info "Arguments: ${ARGS[*]}"
        
        # Run the main class
        java $JAVA_OPTS -cp "$CLASSPATH" "$MAIN_CLASS" "${ARGS[@]}"
        
        if [ $? -eq 0 ]; then
            print_success "DummyAuthorizer completed successfully!"
        else
            print_error "DummyAuthorizer failed"
            exit 1
        fi
    fi
}

# Run main function
main "$@"
