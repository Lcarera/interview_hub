import pulumi
import pulumi_gcp as gcp
from iam import cloudrun_sa, secret_access_binding
from secrets import secrets

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Image URIs are set at deploy time via CI (or Pulumi config for manual deploys)
backend_image = config.get("backend_image") or "gcr.io/cloudrun/hello"
frontend_image = config.get("frontend_image") or "gcr.io/cloudrun/hello"

# Build secret env var list from Secret Manager secrets
_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name=name,
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secret.secret_id,
                version="latest",
            )
        ),
    )
    for name, secret in secrets.items()
]

backend_service = gcp.cloudrunv2.Service(
    "backend",
    name="interview-hub-backend",
    location=region,
    project=project,
    # Only reachable via the global LB — not directly from the internet
    ingress="INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=0),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=backend_image,
                ports=[
                    gcp.cloudrunv2.ServiceTemplateContainerPortArgs(
                        container_port=8080
                    )
                ],
                envs=_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="APP_BASE_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="FRONTEND_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                ],
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8080,
                    ),
                    initial_delay_seconds=10,
                    period_seconds=5,
                    failure_threshold=12,
                ),
            )
        ],
    ),
)

# Grant allUsers invoker so the global LB's serverless NEG can forward requests
# without an identity token. INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER ensures
# only the LB network path can reach this service — direct internet access is blocked.
gcp.cloudrunv2.ServiceIamMember(
    "backend-invoker",
    project=project,
    location=region,
    name=backend_service.name,
    role="roles/run.invoker",
    member="allUsers",
)

frontend_service = gcp.cloudrunv2.Service(
    "frontend",
    name="interview-hub-frontend",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=0),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=frontend_image,
                ports=[
                    gcp.cloudrunv2.ServiceTemplateContainerPortArgs(
                        container_port=80
                    )
                ],
            )
        ],
    ),
)

gcp.cloudrunv2.ServiceIamMember(
    "frontend-invoker",
    project=project,
    location=region,
    name=frontend_service.name,
    role="roles/run.invoker",
    member="allUsers",
)
