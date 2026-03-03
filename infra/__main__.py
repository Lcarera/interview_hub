import pulumi
from registry import registry_url

pulumi.export("registry_url", registry_url)
