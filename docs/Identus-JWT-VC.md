# How to run Connection Module

## Prerequisites
### Store Github account and token in your Gradle directory
#### Window
1. Go to your global `.gradle` directory `C:\Users\\\<username\>\\.gradle\\`
2. Create a file called `gradle.properties` (if not exist) and add your credentials:
```shell
gpr.user=YOUR_GITHUB_USER_NAME
gpr.key=YOUR_GITHUB_TOKEN
```

#### Linux
1. Create / edit `gradle.properties` in global `.gradle` directory:
```shell
$ cd ~/.gradle
$ sudo nano gradle.properties
```
2. Add your credentials to `gradle.properties`:
```shell
gpr.user=YOUR_GITHUB_USER_NAME
gpr.key=YOUR_GITHUB_TOKEN
```
3. Save your changes with `Ctrl + O` then `Enter` and `Ctrl + X` to exit.

## Hyperledger Identus JWT-VC

Full documentation of issuer API can be found [here](http://13.90.44.25:8085/docs/). Step 1, 2 and 3 only need to be run once initially.

1. Create a new DID for the issuer. I will refer to the DID in the response as `didRef`:
    ```shell
    $ curl -X 'POST' \
      'http://13.90.44.25:8085/did-registrar/dids' \
      -H 'Content-Type: application/json' \
      -d '{
      "documentTemplate": {
        "publicKeys": [
          {
            "id": "auth-1",
            "purpose": "authentication"
          },
          {
            "id": "assertion-1",
            "purpose": "assertionMethod"
          }
        ],
        "services": []
      }
    }'
    ```

2. Publish the DID to VDR:
    ```shell
    $ curl -X 'POST' \
      'http://13.90.44.25:8085/did-registrar/dids/{didRef}/publications' \
      -H 'accept: application/json' \
      -d ''
    ```

3. Create a new credential schema. Take note of the schema `id` attribute in the response, since we will use it later:
    ```shell
    $ curl -X 'POST' \
      'http://13.90.44.25:8085/schema-registry/schemas' \
      -H 'accept: application/json' \
      -H 'Content-Type: application/json' \
      -d '{
      "name": "FaberCollegeGraduate",
      "version": "1.0.0",
      "description": "Simple credential schema for the university graduate verifiable credential.",
      "type": "https://w3c-ccg.github.io/vc-json-schemas/schema/2.0/schema.json",
      "schema": {
        "$id": "https://example.com/university-graduate-1.0",
        "$schema": "https://json-schema.org/draft/2020-12/schema",
        "description": "University graduate",
        "type": "object",
        "properties": {
          "emailAddress": {
            "type": "string",
            "format": "email"
          },
          "givenName": {
            "type": "string"
          },
          "familyName": {
            "type": "string"
          },
          "dateOfIssuance": {
            "type": "string",
            "format": "date-time"
          },
          "faculty": {
            "type": "string"
          },
          "gpa": {
            "type": "number"
          }
        },
        "required": [
          "emailAddress",
          "familyName",
          "dateOfIssuance",
          "faculty",
          "gpa"
        ],
        "additionalProperties": false
      },
      "tags": [
        "university",
        "graduate",
        "id"
      ],
      "author": "did:prism:46e4ec58b6464ba3d818657b4707837a9f23a3ac28a395c29e266ecbe29ed6dc"
    }'
    ```

4. Create a Connection Invitation, then paste the `invitationUrl` value in the response to our app. Also take note of the `connectionId` and `guid`, as we need to use it later:
    ```shell
    $ curl -X 'POST' \
      'http://13.90.44.25:8085/connections' \
      -H 'accept: application/json' \
      -H 'Content-Type: application/json' \
      -d '{
      "label": "test-wallet-300126-1651",
      "goalCode": "issue-vc",
      "goal": "To issue a Faber College Graduate credential"
    }'
    ```

5. Wait some moment (or call this API to check) for the connection state to become `ConnectionResponseSent`:
    ```shell
    $ curl -X 'GET' \
      'http://13.90.44.25:8085/connections/{connectionId}' \
      -H 'accept: application/json'
    ```

6. Now everything is set! We can issue a simple certificate:
    ```shell
    $ curl -X 'POST' \   
      'http://13.90.44.25:8085/issue-credentials/credential-offers' \   
      -H 'Content-Type: application/json'   -d '{
      "validityPeriod": 3600,
      "credentialFormat": "JWT",
      "claims": {
        "emailAddress": "alice@wonderland.com",
        "givenName": "Alice",
        "familyName": "Wonderland",
        "dateOfIssuance": "2024-01-30T00:00:00Z",
        "faculty": "Computer Science",
        "gpa": 3
      },
      "schemaId": "http://13.90.44.25:8085/schema-registry/schemas/ffd1a019-7925-32a3-ac17-85208842bb08/schema",
      "credentialDefinitionId": "ffd1a019-7925-32a3-ac17-85208842bb08",
      "automaticIssuance": true,
      "connectionId": "{connectionId}",
      "issuingDID": "did:prism:46e4ec58b6464ba3d818657b4707837a9f23a3ac28a395c29e266ecbe29ed6dc",
      "goalCode": "issue-vc",
      "goal": "test-wallet",
      "domain": "faber-college-jwt-vc"
      }'
    ```

7. We can call this API to trigger the presentation flow:
   ```shell
   $ curl -X 'POST' \
     'http://localhost:8085/present-proof/presentations' \
     -H 'accept: application/json' \
     -H 'Content-Type: application/json' \
     -d '{
       "goalCode": "present-vp",
       "goal": "Request proof of vaccine",
       "connectionId": "{conectionId}",
       "options": {
         "challenge": "11c91493-01b3-4c4d-ac36-b336bab5bddf",
         "domain": "https://example-verifier.com"
       },
       "proofs": [],
       "claims": {
         "firstname": {},
         "lastname": {}
       },
       "presentationFormat": "JWT",
       "credentialFormat": "JWT"
     }'
   ```
