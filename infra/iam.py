import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")

# Service account used by the Cloud Run backend container at runtime
cloudrun_sa = gcp.serviceaccount.Account(
    "cloudrun-sa",
    account_id="interview-hub-cloudrun",
    display_name="Interview Hub Cloud Run Service Account",
    project=project,
)

# Allow the service account to read secrets from Secret Manager
secret_access_binding = gcp.projects.IAMMember(
    "cloudrun-secret-access",
    project=project,
    role="roles/secretmanager.secretAccessor",
    member=pulumi.Output.concat("serviceAccount:", cloudrun_sa.email),
)

# The allUsers invoker bindings are set per-service in cloudrun.py.
# Combined with INGRESS_TRAFFIC_INTERNAL_LOAD_BALANCER, this allows
# the global LB to forward requests without an identity token, while
# preventing direct internet access to the services.
