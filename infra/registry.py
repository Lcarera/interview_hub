import pulumi
import pulumi_gcp as gcp

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")
region = gcp_config.require("region")

repo = gcp.artifactregistry.Repository(
    "interview-hub-repo",
    repository_id="interview-hub",
    location=region,
    format="DOCKER",
    description="Interview Hub Docker images",
    project=project,
)

registry_url = pulumi.Output.concat(
    region, "-docker.pkg.dev/", project, "/interview-hub"
)
