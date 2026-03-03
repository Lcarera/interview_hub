import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

# These are the 7 env vars the backend needs (values set manually via gcloud)
_SECRET_NAMES = [
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "GOOGLE_CLIENT_ID",
    "GOOGLE_CLIENT_SECRET",
    "JWT_SIGNING_SECRET",
    "TOKEN_ENCRYPTION_KEY",
]

secrets: dict[str, gcp.secretmanager.Secret] = {}
for _name in _SECRET_NAMES:
    _resource_id = _name.lower().replace("_", "-")
    secrets[_name] = gcp.secretmanager.Secret(
        f"secret-{_resource_id}",
        secret_id=f"interview-hub-{_resource_id}",
        project=project,
        replication=gcp.secretmanager.SecretReplicationArgs(
            auto=gcp.secretmanager.SecretReplicationAutoArgs(),
        ),
    )
