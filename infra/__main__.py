import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects
from secrets import secrets  # noqa: F401 — imported for side effects
from cloudtasks import email_queue  # noqa: F401 — imported for side effects
from cloudrun import backend_service, frontend_service, eureka_service

pulumi.export("registry_url", registry_url)
pulumi.export("backend_url", backend_service.uri)
pulumi.export("frontend_url", frontend_service.uri)
pulumi.export("eureka_url", eureka_service.uri)
pulumi.export("email_queue_name", email_queue.name)
