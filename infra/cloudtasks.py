import pulumi
import pulumi_gcp as gcp
from iam import cloudrun_sa

gcp_config = pulumi.Config("gcp")
project = gcp_config.require("project")
region = gcp_config.require("region")

# Enable Cloud Tasks API
cloudtasks_api = gcp.projects.Service(
    "cloudtasks-api",
    project=project,
    service="cloudtasks.googleapis.com",
    disable_on_destroy=False,
)

# Cloud Tasks queue for async email sending with rate limiting
email_queue = gcp.cloudtasks.Queue(
    "email-queue",
    name="email-queue",
    location=region,
    project=project,
    rate_limits=gcp.cloudtasks.QueueRateLimitsArgs(
        max_dispatches_per_second=2,
        max_concurrent_dispatches=2,
    ),
    retry_config=gcp.cloudtasks.QueueRetryConfigArgs(
        max_attempts=5,
        min_backoff="10s",
        max_backoff="300s",
    ),
    opts=pulumi.ResourceOptions(depends_on=[cloudtasks_api]),
)

# Grant the Cloud Run service account permission to enqueue tasks
cloudtasks_enqueuer = gcp.cloudtasks.QueueIamMember(
    "cloudrun-cloudtasks-enqueuer",
    project=project,
    location=region,
    name=email_queue.name,
    role="roles/cloudtasks.enqueuer",
    member=pulumi.Output.concat("serviceAccount:", cloudrun_sa.email),
)

# Allow the Cloud Run SA to create OIDC tokens as itself (for Cloud Tasks HTTP callbacks)
service_account_user = gcp.serviceaccount.IAMMember(
    "cloudrun-sa-user",
    service_account_id=cloudrun_sa.name,
    role="roles/iam.serviceAccountUser",
    member=pulumi.Output.concat("serviceAccount:", cloudrun_sa.email),
)
