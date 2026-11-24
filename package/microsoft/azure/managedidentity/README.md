# Managed identities for Azure resources

Managed identities for Azure resources is a Microsoft Entra ID feature. It gives Azure resources an
automatically managed identity they can use to authenticate to other services.

## Key capabilities

- **No stored credentials:** Removes the need to manage secrets in code or configuration.
- **System-assigned identities:** Ties an identity to one resource and its lifecycle.
- **User-assigned identities:** Creates standalone identities that several resources can share.
- **Microsoft Entra authentication:** Gets tokens for services that support Microsoft Entra
  authentication.

## Official resources

- Documentation: https://learn.microsoft.com/en-us/entra/identity/managed-identities-azure-resources/overview
- REST API reference: https://learn.microsoft.com/en-us/rest/api/managedidentity/
