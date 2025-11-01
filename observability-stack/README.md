# Observability Stack

This directory contains Docker Compose configuration for running Prometheus, Grafana, and Jaeger together for monitoring and observability.

## Quick Start - Connecting Your App Container

**If you're starting a new container:**
```bash
# Start observability stack
docker-compose up -d

# Run your app on the same network (using default network name)
docker run --rm --network monitoring_observability-network your-image

# Or with custom network name (see Configuration section below)
DOCKER_NETWORK_NAME=my-custom-network docker-compose up -d
docker run --rm --network my-custom-network your-image
```

## Configuration

### Network Name

The observability network name is configurable via the `DOCKER_NETWORK_NAME` environment variable. The default is `monitoring_observability-network`.

**Option 1: Using environment variable**
```bash
# Set before running docker-compose
export DOCKER_NETWORK_NAME=my-custom-network
docker-compose up -d
```

**Option 2: Using .env file**
Create a `.env` file in the `observability-stack` directory (or copy from `env.example`):
```bash
cp env.example .env
# Edit .env and set your custom network name
docker-compose up -d
```

Or create it directly:
```bash
echo "DOCKER_NETWORK_NAME=my-custom-network" > .env
docker-compose up -d
```

**Option 3: Inline with docker-compose**
```bash
DOCKER_NETWORK_NAME=my-custom-network docker-compose up -d
```

## Services

### Prometheus
- **Port**: `9090`
- **UI**: http://localhost:9090
- **Graph/Query**: http://localhost:9090/graph
- **Targets Status**: http://localhost:9090/targets
- **Purpose**: Metrics collection and storage

**Quick Verification:**
1. Check targets: http://localhost:9090/targets - Should show `ranger-monitoring` job as UP
2. Query metrics: http://localhost:9090/graph - Try: `ranger_access_checks_total`

### Grafana
- **Port**: `3000`
- **UI**: http://localhost:3000
- **Default credentials**: 
  - Username: `admin`
  - Password: `admin`
- **Purpose**: Metrics visualization and dashboards

**Set Mount Everest Dashboard as Default Home:**
To show the "Mount Everest" dashboard when accessing the root URL:
1. Log in to Grafana (http://localhost:3000)
2. Click on your user icon (bottom left) → **Preferences**
3. Under **Home Dashboard**, select **Mount Everest** from the dropdown
4. Click **Save**

Alternatively, use the API (replace `<your-password>` with your Grafana admin password):
```bash
curl -X PUT "http://admin:<your-password>@localhost:3000/api/user/preferences" \
  -H "Content-Type: application/json" \
  -d '{"homeDashboardUID":"mount-everest"}'
```

### Jaeger
- **Ports**:
  - `16686` - Jaeger UI (http://localhost:16686)
  - `4317` - OTLP gRPC receiver
  - `4318` - OTLP HTTP receiver
  - `14250` - Jaeger gRPC
  - `14268` - Jaeger HTTP
  - `6831/udp` - Jaeger agent (UDP)
  - `6832/udp` - Jaeger agent (UDP)
- **Purpose**: Distributed tracing

## Usage

### Start all services
```bash
docker-compose up -d
```

### Stop all services
```bash
docker-compose down
```

### Stop and remove volumes (clean slate)
```bash
docker-compose down -v
```

### View logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f prometheus
docker-compose logs -f grafana
docker-compose logs -f jaeger
```

### Access Services

- **Prometheus**: 
  - Main UI: http://localhost:9090
  - Query/Graph: http://localhost:9090/graph
  - Targets Status: http://localhost:9090/targets
- **Grafana**: http://localhost:3000 (admin/admin)
- **Jaeger**: http://localhost:16686

### Verify Metrics are Working

**Manual checks:**
1. **Check Prometheus targets**: http://localhost:9090/targets
   - Look for `ranger-monitoring` job
   - Status should be **UP** (green)
   
2. **Test a query**: http://localhost:9090/graph
   - Try: `ranger_access_checks_total`
   - Or: `ranger_monitoring_plugin_running`

3. **Check metrics endpoint directly**:
   ```bash
   # From host (if port exposed)
   curl http://localhost:6085/metrics
   
   # From Prometheus container
   docker exec prometheus wget -qO- http://ranger-monitoring-app:6085/metrics | head -20
   ```

**If you see "No data" or empty dashboard:**
- Verify application container is on `skynet` network
- Check application logs: `docker logs ranger-monitoring-app`
- Ensure metrics server started: Look for "Prometheus metrics server started on port 6085"

## Configuration

### Prometheus
Edit `prometheus/prometheus.yml` to add scrape targets for your applications.

### Grafana
- Datasources are automatically provisioned from `grafana/provisioning/datasources/`
- Dashboards should be placed in `grafana/dashboards/` directory
- First login with admin/admin, then change the password

### Jaeger
Jaeger is configured with OTLP collectors enabled by default. Your applications can send traces to:
- **OTLP gRPC**: `http://localhost:4317`
- **OTLP HTTP**: `http://localhost:4318`
- **Jaeger HTTP**: `http://localhost:14268/api/traces`

## Integration with Your Application

### Prometheus Metrics
Add a scrape config in `prometheus/prometheus.yml`:
```yaml
- job_name: 'your-service'
  static_configs:
    - targets: ['host.docker.internal:6085']
```

### Jaeger Tracing
Configure your application to send traces to:
- OTLP endpoint: `http://localhost:4317` (gRPC) or `http://localhost:4318` (HTTP)
- Jaeger endpoint: `http://localhost:14268/api/traces` (HTTP)

## Network

All services run on a shared Docker network (`observability-network`) so they can communicate with each other using service names (e.g., `prometheus:9090`).

## Connecting Your Application Container

If your application runs in a separate Docker container, you have several options to connect it to the observability stack:

### Option 1: Join the Observability Network (Recommended)

This is the cleanest approach for container-to-container communication.

**Step 1: Ensure the observability stack is running**
```bash
cd observability-stack
docker-compose up -d
```

**Step 2: Determine the network name**
```bash
# If using default
NETWORK_NAME=monitoring_observability-network

# Or if using custom network (check .env or environment variable)
# The network name is: ${DOCKER_NETWORK_NAME:-monitoring_observability-network}
```

**Step 3: Run your application container connected to that network**
```bash
# Using --network flag with default network name
docker run --rm \
  --network monitoring_observability-network \
  your-application-image

# Or with custom network name (from DOCKER_NETWORK_NAME)
docker run --rm \
  --network ${DOCKER_NETWORK_NAME:-monitoring_observability-network} \
  your-application-image

# Or if using docker-compose for your app, add:
networks:
  default:
    external:
      name: ${DOCKER_NETWORK_NAME:-monitoring_observability-network}
```

**Step 4: Configure your application endpoints**
- **Prometheus scrape endpoint**: `http://prometheus:9090`
- **Jaeger OTLP gRPC**: `http://jaeger:4317`
- **Jaeger OTLP HTTP**: `http://jaeger:4318`
- **Jaeger HTTP**: `http://jaeger:14268/api/traces`

### Option 2: Use host.docker.internal (Simple but limited)

If your application container can access the host network, use `host.docker.internal`:

```bash
docker run --rm \
  --add-host=host.docker.internal:host-gateway \
  your-application-image
```

Then configure endpoints as:
- **Prometheus**: `http://host.docker.internal:9090`
- **Jaeger OTLP gRPC**: `http://host.docker.internal:4317`
- **Jaeger OTLP HTTP**: `http://host.docker.internal:4318`
- **Jaeger HTTP**: `http://host.docker.internal:14268/api/traces`

**Note**: This works on Docker Desktop (Mac/Windows) and Docker 20.10+ on Linux.

### Option 3: Use Host Network Mode

Run your container with host networking:

```bash
docker run --rm --network host your-application-image
```

Then use `localhost` endpoints:
- **Prometheus**: `http://localhost:9090`
- **Jaeger OTLP gRPC**: `http://localhost:4317`
- etc.

**Note**: This only works on Linux (not Docker Desktop).

### Option 4: Make Network External (For Multiple Compose Files)

If you want to use the observability network from another docker-compose.yml:

**1. Make the network external in docker-compose.yml:**
```yaml
networks:
  observability-network:
    external: true
    name: monitoring_observability-network
```

**2. Start observability stack first:**
```bash
cd observability-stack
docker-compose up -d
```

**3. In your application's docker-compose.yml:**
```yaml
services:
  your-app:
    image: your-application-image
    networks:
      - observability-network

networks:
  observability-network:
    external: true
    name: monitoring_observability-network
```

## Example: Connecting Ranger Monitoring Application

If you're running the Ranger monitoring application from this project:

**Method 1: Modify run_docker.sh to join network**
```bash
# In run_docker.sh, add --network flag to docker run:
docker run --rm \
  --network monitoring_observability-network \
  --user 1000:1000 \
  # ... rest of your options
```

**Method 2: Connect existing container**
```bash
# Start observability stack
cd observability-stack
docker-compose up -d

# Connect your running container to the network
docker network connect ${DOCKER_NETWORK_NAME:-monitoring_observability-network} <your-container-name>
```

## Prometheus Scraping Configuration

After connecting your application, update Prometheus to scrape your app's metrics:

**Edit `prometheus/prometheus.yml`:**
```yaml
scrape_configs:
  - job_name: 'ranger-monitoring'
    # If connected via network:
    static_configs:
      - targets: ['ranger-monitoring-app:6085']  # Adjust port/container name
    # If using host.docker.internal:
    #   - targets: ['host.docker.internal:6085']
```

**Reload Prometheus configuration:**
```bash
curl -X POST http://localhost:9090/-/reload
```

## Verification

**Check if containers are on the same network:**
```bash
# Using default or custom network name
docker network inspect ${DOCKER_NETWORK_NAME:-monitoring_observability-network}
```

You should see both the observability services and your application container listed.

**Test connectivity from your app container:**
```bash
# If your app container is running
docker exec -it your-app-container ping prometheus
docker exec -it your-app-container curl http://jaeger:14268/api/traces
```

**Connect existing running container to network:**
```bash
docker network connect ${DOCKER_NETWORK_NAME:-monitoring_observability-network} <your-container-name>
```

