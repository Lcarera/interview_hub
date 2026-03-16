import pulumi
import pulumi_gcp as gcp
from iam import cloudrun_sa, secret_access_binding
from secrets import secrets
from cloudtasks import email_queue, cloudtasks_enqueuer

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Image URIs are set at deploy time via CI; optional here so targeted `pulumi up` for secrets works
backend_image = config.get("backend_image") or "placeholder"
frontend_image = config.get("frontend_image") or "placeholder"
backend_domain = config.get("backend_domain") or "i-hub-be.lcarera.dev"

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

# Cloud Tasks worker URL - use the actual Cloud Run URL (not custom domain) for direct GCP-to-GCP communication
# This avoids routing through Cloudflare and ensures OIDC token audience matches the actual target
cloudtasks_worker_url = config.get("cloudtasks_worker_url")

backend_service = gcp.cloudrunv2.Service(
    "backend",
    name="interview-hub-backend",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=0),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding, cloudtasks_enqueuer]),
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
                        value=pulumi.Output.concat("https://", backend_domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="FRONTEND_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="MAIL_FROM",
                        value="noreply@lcarera.dev",
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="GOOGLE_CALENDAR_ID",
                        value="0cae724ce3870858a6213c7f351107891bd3c1265b336d3bfef5693c3a3cdc9d@group.calendar.google.com",
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="GCP_PROJECT_ID",
                        value=project,
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="GCP_LOCATION",
                        value=region,
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="CLOUD_TASKS_QUEUE_ID",
                        value=email_queue.name,
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="CLOUD_TASKS_ENABLED",
                        value="true",
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="CLOUD_TASKS_SA_EMAIL",
                        value=cloudrun_sa.email,
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="CLOUD_TASKS_WORKER_URL",
                        value=cloudtasks_worker_url,
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="CLOUD_TASKS_AUDIENCE",
                        value=cloudtasks_worker_url,
                    ),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "1Gi", "cpu": "1000m"},
                ),
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8080,
                    ),
                    initial_delay_seconds=15,
                    period_seconds=5,
                    failure_threshold=20,
                ),
            )
        ],
    ),
)

# Grant allUsers invoker so the services are publicly accessible via custom domains.
# INGRESS_TRAFFIC_ALL allows direct internet access through Cloudflare DNS proxy.
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
    ingress="INGRESS_TRAFFIC_ALL",
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
