import pulumi
import pulumi_gcp as gcp
from iam import cloudrun_sa, secret_access_binding
from secrets import secrets

gcp_config = pulumi.Config("gcp")
config = pulumi.Config("interview-hub-infra")
project = gcp_config.require("project")
region = gcp_config.require("region")
domain = config.require("domain")

# Image URIs are set at deploy time via CI; optional here so targeted `pulumi up` for secrets works
backend_image = config.get("backend_image") or "placeholder"
frontend_image = config.get("frontend_image") or "placeholder"
eureka_image = config.get("eureka_image") or "placeholder"
notification_image = config.get("notification_image") or "placeholder"
gateway_image = config.get("gateway_image") or "placeholder"
backend_domain = config.get("backend_domain") or "i-hub-be.lcarera.dev"

eureka_service = gcp.cloudrunv2.Service(
    "eureka-server",
    name="interview-hub-eureka-server",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=1),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=eureka_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8761)],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
                ),
                startup_probe=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeArgs(
                    http_get=gcp.cloudrunv2.ServiceTemplateContainerStartupProbeHttpGetArgs(
                        path="/actuator/health",
                        port=8761,
                    ),
                    initial_delay_seconds=15,
                    period_seconds=5,
                    failure_threshold=20,
                ),
            )
        ],
    ),
)

gcp.cloudrunv2.ServiceIamMember(
    "eureka-server-invoker",
    project=project,
    location=region,
    name=eureka_service.name,
    role="roles/run.invoker",
    member="allUsers",
)

_notification_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name=name,
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secrets[name].secret_id,
                version="latest",
            )
        ),
    )
    for name in ["RESEND_API_KEY", "RABBITMQ_URL"]
]

notification_service = gcp.cloudrunv2.Service(
    "notification-service",
    name="interview-hub-notification",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=1),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=notification_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8080)],
                envs=_notification_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="EUREKA_URL",
                        value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="FRONTEND_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="MAIL_FROM",
                        value="noreply@lcarera.dev",
                    ),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
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

gcp.cloudrunv2.ServiceIamMember(
    "notification-service-invoker",
    project=project,
    location=region,
    name=notification_service.name,
    role="roles/run.invoker",
    member="allUsers",
)

_gateway_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name="JWT_SIGNING_SECRET",
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secrets["JWT_SIGNING_SECRET"].secret_id,
                version="latest",
            )
        ),
    ),
]

api_gateway_service = gcp.cloudrunv2.Service(
    "api-gateway",
    name="interview-hub-api-gateway",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
    scaling=gcp.cloudrunv2.ServiceScalingArgs(min_instance_count=0),
    opts=pulumi.ResourceOptions(depends_on=[secret_access_binding]),
    template=gcp.cloudrunv2.ServiceTemplateArgs(
        service_account=cloudrun_sa.email,
        containers=[
            gcp.cloudrunv2.ServiceTemplateContainerArgs(
                image=gateway_image,
                ports=[gcp.cloudrunv2.ServiceTemplateContainerPortArgs(container_port=8080)],
                envs=_gateway_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="EUREKA_URL",
                        value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="APP_BASE_URL",
                        value=pulumi.Output.concat("https://", backend_domain),
                    ),
                ],
                resources=gcp.cloudrunv2.ServiceTemplateContainerResourcesArgs(
                    limits={"memory": "512Mi", "cpu": "500m"},
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

gcp.cloudrunv2.ServiceIamMember(
    "api-gateway-invoker",
    project=project,
    location=region,
    name=api_gateway_service.name,
    role="roles/run.invoker",
    member="allUsers",
)

# Backend secrets exclude RESEND_API_KEY — that is a notification-service concern
_backend_secret_names = [
    "DB_URL", "DB_USERNAME", "DB_PASSWORD",
    "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
    "JWT_SIGNING_SECRET", "GOOGLE_CALENDAR_REFRESH_TOKEN",
    "RABBITMQ_URL",
]
_backend_secret_envs = [
    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
        name=name,
        value_source=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceArgs(
            secret_key_ref=gcp.cloudrunv2.ServiceTemplateContainerEnvValueSourceSecretKeyRefArgs(
                secret=secrets[name].secret_id,
                version="latest",
            )
        ),
    )
    for name in _backend_secret_names
]

backend_service = gcp.cloudrunv2.Service(
    "backend",
    name="interview-hub-backend",
    location=region,
    project=project,
    ingress="INGRESS_TRAFFIC_ALL",
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
                envs=_backend_secret_envs + [
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="EUREKA_URL",
                        value=eureka_service.uri.apply(lambda u: u + "/eureka/"),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="APP_BASE_URL",
                        value=pulumi.Output.concat("https://", backend_domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="FRONTEND_URL",
                        value=pulumi.Output.concat("https://", domain),
                    ),
                    gcp.cloudrunv2.ServiceTemplateContainerEnvArgs(
                        name="GOOGLE_CALENDAR_ID",
                        value="0cae724ce3870858a6213c7f351107891bd3c1265b336d3bfef5693c3a3cdc9d@group.calendar.google.com",
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
