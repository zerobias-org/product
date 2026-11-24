# Amazon Glacier

Amazon Glacier (formerly Amazon S3 Glacier) is the original vault-based archival storage service
from Amazon Web Services (AWS). It has its own API and is separate from the S3 Glacier storage
classes in Amazon S3. AWS stopped onboarding new customers to it on December 15, 2025; existing
customers keep access.

## Key capabilities

- **Vaults and archives:** Stores data as archives inside vaults.
- **Retrieval jobs:** Retrieves archives and vault inventories through asynchronous jobs.
- **Vault Lock:** Enforces compliance controls on a vault with a lockable vault policy.
- **Access control:** Controls access with IAM policies and vault access policies.

## Official resources

- Documentation: https://docs.aws.amazon.com/amazonglacier/latest/dev/introduction.html
- API reference: https://docs.aws.amazon.com/amazonglacier/latest/dev/amazon-glacier-api.html
- Pricing and availability: https://aws.amazon.com/s3/glacier/pricing/
