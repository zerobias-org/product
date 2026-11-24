# AWS Security Token Service

AWS Security Token Service (AWS STS) is a service from Amazon Web Services (AWS) that issues
temporary, limited-privilege credentials for AWS.

## Key capabilities

- **Role assumption:** Returns temporary credentials for an IAM role (AssumeRole).
- **Federation:** Issues credentials for SAML and web identity (OIDC) federated users.
- **Session tokens:** Issues session credentials, including for MFA-protected access.
- **Caller identity:** Returns details about the identity making a request.

## Official resources

- Documentation: https://docs.aws.amazon.com/IAM/latest/UserGuide/id_credentials_temp.html
- API reference: https://docs.aws.amazon.com/STS/latest/APIReference/welcome.html
