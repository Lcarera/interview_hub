import pulumi
import pulumi_gcp as gcp
from cloudrun import backend_service, frontend_service

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Static external IP
static_ip = gcp.compute.GlobalAddress(
    "interview-hub-ip",
    name="interview-hub-ip",
    project=project,
)

# Managed SSL certificate (auto-provisioned by GCP after DNS is pointed at the IP)
ssl_cert = gcp.compute.ManagedSslCertificate(
    "interview-hub-cert",
    name="interview-hub-cert",
    project=project,
    managed=gcp.compute.ManagedSslCertificateManagedArgs(
        domains=[domain],
    ),
)

# Serverless NEG for backend Cloud Run service
backend_neg = gcp.compute.RegionNetworkEndpointGroup(
    "backend-neg",
    name="interview-hub-backend-neg",
    network_endpoint_type="SERVERLESS",
    region=region,
    project=project,
    cloud_run=gcp.compute.RegionNetworkEndpointGroupCloudRunArgs(
        service=backend_service.name,
    ),
)

# Serverless NEG for frontend Cloud Run service
frontend_neg = gcp.compute.RegionNetworkEndpointGroup(
    "frontend-neg",
    name="interview-hub-frontend-neg",
    network_endpoint_type="SERVERLESS",
    region=region,
    project=project,
    cloud_run=gcp.compute.RegionNetworkEndpointGroupCloudRunArgs(
        service=frontend_service.name,
    ),
)

# Backend services (global LB components pointing to the NEGs)
# Note: health_checks are NOT supported for serverless NEGs — omit them
backend_lb_service = gcp.compute.BackendService(
    "backend-lb-service",
    name="interview-hub-backend-service",
    project=project,
    protocol="HTTP",
    load_balancing_scheme="EXTERNAL_MANAGED",
    backends=[
        gcp.compute.BackendServiceBackendArgs(
            group=backend_neg.id,
        )
    ],
)

frontend_lb_service = gcp.compute.BackendService(
    "frontend-lb-service",
    name="interview-hub-frontend-service",
    project=project,
    protocol="HTTP",
    load_balancing_scheme="EXTERNAL_MANAGED",
    backends=[
        gcp.compute.BackendServiceBackendArgs(
            group=frontend_neg.id,
        )
    ],
)

# URL map: route API paths to backend, everything else to frontend
url_map = gcp.compute.URLMap(
    "url-map",
    name="interview-hub-url-map",
    project=project,
    default_service=frontend_lb_service.id,
    host_rules=[
        gcp.compute.URLMapHostRuleArgs(
            hosts=["*"],
            path_matcher="allpaths",
        )
    ],
    path_matchers=[
        gcp.compute.URLMapPathMatcherArgs(
            name="allpaths",
            default_service=frontend_lb_service.id,
            path_rules=[
                gcp.compute.URLMapPathMatcherPathRuleArgs(
                    paths=[
                        "/auth/google",
                        "/auth/google/*",
                        "/auth/token",
                        "/interviews",
                        "/interviews/*",
                        "/shadowing-requests",
                        "/shadowing-requests/*",
                        "/actuator",
                        "/actuator/*",
                    ],
                    service=backend_lb_service.id,
                )
            ],
            # Note: /auth/callback is an Angular client-side route — it correctly
            # falls through to the frontend via the default_service above.
        )
    ],
)

# HTTPS proxy (terminates TLS, uses SSL cert)
https_proxy = gcp.compute.TargetHttpsProxy(
    "https-proxy",
    name="interview-hub-https-proxy",
    project=project,
    url_map=url_map.id,
    ssl_certificates=[ssl_cert.id],
)

# Forwarding rule: binds static IP + port 443 to the HTTPS proxy
forwarding_rule = gcp.compute.GlobalForwardingRule(
    "forwarding-rule",
    name="interview-hub-forwarding-rule",
    project=project,
    target=https_proxy.id,
    port_range="443",
    ip_address=static_ip.address,
    load_balancing_scheme="EXTERNAL_MANAGED",
)
