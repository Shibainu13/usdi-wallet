# Mobile verifier through cloud-agent HTTP

This flow lets a mobile device control a verifier cloud agent through HTTP endpoints.
It does not change the existing case where the server independently acts as verifier.

## Flow

```text
Verifier mobile -> Cloud agent HTTP -> Holder mobile
```

1. Open `Verify`.
2. Select `Server HTTP`.
3. Enter the cloud agent URL, for example `http://10.0.2.2:8085`.
4. Enter the API key if your cloud agent requires one.
5. If the holder already has a cloud-agent connection, paste that holder `connectionId`.
6. If the holder is not connected yet, tap `New holder`, copy the invitation URL, and let the holder accept it in `Contacts`.
7. On the verifier mobile, tap `Check` until the connection reaches the expected connected state.
8. Enter the credential definition ID if the proof should be restricted to one AnonCred credential definition.
9. Add proof claims, for example `birthday` and `location`.
10. Tap `Send via server`.
11. The holder mobile receives the proof request and uses the existing proof request sheet to select a credential.

## HTTP endpoints used

Create connection invitation:

```http
POST /connections
```

Check connection:

```http
GET /connections/{connectionId}
```

Send AnonCred proof request:

```http
POST /present-proof/presentations
```

The request uses the same cloud-agent format already documented in `CONNECTION.md`.

## Important

An invitation is only needed to create a new holder-to-cloud-agent connection.
If both devices are already known by the same cloud agent, the verifier mobile only needs the holder's `connectionId`.
