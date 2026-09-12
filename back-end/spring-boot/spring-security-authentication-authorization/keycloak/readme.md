# Table of Contents:
- [1. Realm](#1-realm)
- [2. Client](#2-client)
- [3. Client Scopes](#3-client-scopes)
- [4. Realm Roles](#4-realm-roles)
- [5. Users](#5-users)
- [6. Groups](#6-groups)
- [7. Realm Settings](#7-realm-settings)

## 1. Realm
Realm is the workspace in Keycloak. It contains Users, Credentials, Roles, Clients, Groups, Policies & settings
## 2. Client
In Microservice, each Service is the Client.
### 2.1. Client settings
#### 2.1.1. General settings
ClientID, Name
#### 2.1.2. Access settings
- Root URL: Base of your app. Ex: https://localhost:8080/
- Home URL: Default page after login. Ex: http://localhost:8080/
- Valid redirect URIs: Allowed login return URLs. http://localhost:8080/*
    - when login to keycloak, client can give redirect uri that redirect user after they succesfully sign in. If there is not redirect uri, keycloak will redirect user to **Home URL**
- Valid post logout redirect URIs: Allowed logout return URLs. Ex: http://localhost:8080/*
    - After user log out, they are redirected to this URL. Client must give redirect URL when send log out request
- Web origins: Allowed browser API calls. If your base app url is not listed, CORS exception will be thrown. Ex: https://localhost:8080
- Admin URL: keycloak use this url to send log out request to your app in case of user's session log out in Keycloak or other services. Ex: https://localhost:8080/
#### 2.1.3. Capability config
- If the client is used for PasswordAuthentication(log in with userName, password, clientId), just enable **Standard flow** and **Direct access grants** in **Authentication Flow**
- If the client is used for CredentialAuthentication(log in with clientId, clientSerect), just enable **Client authentication** and in **Authentication flow**, enable **Standard flow** and **Service account roles**.
- When enable Service account role, keycloak will create a abstract account for this client, you can set up the group or role for this client.
#### 2.1.4. Logout settings
- Just enable Front channel logout, Front-channel logout session required
### 2.2. Client with `Client authentication` mode (CredentialAuthentication)
#### 2.2.1. Credentials tab
- Show clientSecret
#### 2.2.2. Service accounts roles
- Each client can have a service account in the format: `service-account-<clientId>`.
- This service account behaves like a regular user and supports the same features
## 3. Client Scopes

Client scopes (client's permission) define which mappers are included in a JWT token.

- **roles**: Adds user roles (e.g. `realm_access` and `roles`) to the access token.
- **web-origins**: Adds the `allowed-origins` mapper to the access token.
- **address, basic, email, profile**: Provide standard claims such as `scope`, `name`, `preferred_username`, `given_name`, etc.

### Custom Mapper

You can also define custom mappers in the default client scope using the format `<client-name>-dedicated`.

**Example:**

- Create a mapper named `group`
- Set type to **Group Membership**
- Enable **Add to access token**

**Result:**

The access token will include a `group` claim, for example:

```json
{
  "group": "/admin"
}
```

## 4. Realm roles
- **Realm roles** are roles defined at the realm level. They can be assigned to any user and used across all clients.
- Applications can use these roles for authorization. If a user does not have the required role, the application should return a `403 Forbidden` response.
## 5. Users
- Credentials: password
- Role mapping: list realm role that assigned to this user
- Group: list group that assigned to this user
- Sessions: list time that user is active 
## 6. Groups
- Role mapping: list realm role that assigned to this group
## 7. Realm settings and Client settings
### 7.1. Sessions
#### 7.1.1. Client -> Advanced tab
These settings override realm defaults for a specific client.

**Access Token Lifespan**: 8 hours
- Defines how long an access token is valid.
- After expiration, the client must use a refresh token to obtain a new one.

**Client Session Idle**: 16 hours
- Maximum idle time for a client session.
- If the user is inactive for this duration, the session expires.

**Client Session Max**: 1 days
- Maximum lifespan of a client session regardless of activity.
- Even if the user is active, the session will expire after this time.
- This is expired time of refresh token

**Client Offline Session Idle**: Inherits from realm settings

#### 7.1.2. Realm Settings
These are global defaults applied to all clients unless overridden

**SSO Session Idle**: 1 hours - has the same meaning with Client Session Idle but used with realm
- Maximum idle time for the Single Sign-On (SSO) session.
- If the user is inactive across all clients, they will be logged out.

**SSO Session Max**: 2 hours - has the same meaning with Client Session Max but used with realm
- Maximum lifespan of the SSO session regardless of activity.
- Forces re-authentication after this duration.

### 7.2. Tokens
#### 7.2.1. Client
**Use Refresh Tokens**: enable
- Enables issuing refresh tokens along with access tokens.
- Allows clients to renew access tokens without forcing the user to log in again.
#### 7.2.2. Session
**Access Token Lifespan**: 30 minutes
- Default lifespan for access tokens issued in the realm.

**Access Token Lifespan for Implicit Flow**: 15 minutes
- Specific lifespan for tokens issued via the implicit flow.
- Typically shorter due to security concerns.

**Client Login Timeout**: 1 minutes
- Maximum time allowed to complete the login process.
- If exceeded, authentication fails.

**OAuth 2.0 Device Code Lifespan**: 10 minutes
**OAuth 2.0 Device Polling Interval**: 5