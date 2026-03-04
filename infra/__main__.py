import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects
from secrets import secrets  # noqa: F401 — imported for side effects
from cloudrun import backend_service, frontend_service
from loadbalancer import static_ip

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
pulumi.export("load_balancer_ip", static_ip.address)
