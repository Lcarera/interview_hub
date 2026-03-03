import pulumi
from registry import registry_url
from iam import cloudrun_sa  # noqa: F401 — imported for side effects

pulumi.export("registry_url", registry_url)
