# AnonCred Verification Flow Requirements

## 1. Purpose

This document defines the requirements and use cases for verifying an AnonCred credential after successful issuance.

In AnonCreds, verification is not done by simply checking the raw credential. Verification is done through a proof/presentation flow:

```text
Issuer issues credential
Holder stores credential
Verifier sends proof request
Holder creates proof from stored credential
Verifier verifies proof
```

The goal is to implement a complete verification flow where a verifier can request proof of claims from a holder, and the holder can generate a valid AnonCred proof using an issued credential.

---

## 2. Actors

### Issuer

The party that issues an AnonCred credential.

Example:

- University
- Government agency
- Company
- Certificate provider

### Holder

The user wallet that receives and stores the issued credential.

Example:

- Mobile wallet app
- KMP wallet app
- Student wallet

### Verifier

The party that wants to verify some information from the holder.

Example:

- Website
- Service provider
- Employer
- School system
- Access control system

---

## 3. Main Use Case

### Use Case: Verify an Issued AnonCred Credential

#### Goal

Allow a verifier to confirm that the holder owns a valid AnonCred credential and can prove selected claims without exposing the full credential.

#### Preconditions

- The issuer has already created a schema.
- The issuer has already created a credential definition.
- The issuer has successfully issued an AnonCred credential.
- The holder has stored the issued credential.
- The verifier knows what attributes or predicates it wants to verify.

#### Postconditions

- The verifier receives a proof presentation from the holder.
- The verifier checks the proof against the proof request, schema, credential definition, and revocation data if needed.
- The verifier gets a verification result: `verified` or `failed`.

---

## 4. Functional Requirements

### FR-01: Store Issued AnonCred Credential

This feature already implement by store by wallet SDK.

### FR-02: Create Proof Request

The verifier must be able to create a proof request that defines which claims must be proven.

#### Example requested attributes

```json
{
  "name": "required",
  "student_id": "required",
  "university": "required"
}
```

#### Example requested predicates

```json
{
  "age": {
    "operator": ">=",
    "value": 18
  }
}
```

#### Acceptance Criteria

- The proof request contains a unique request ID.
- The proof request defines requested attributes and/or predicates.
- The proof request can be sent to the holder.

---

### FR-03: Receive Proof Request

The holder must be able to receive and parse a proof request from the verifier.

#### Acceptance Criteria

- The holder app detects the incoming proof request.
- The holder app displays requested attributes/predicates.
- The holder app finds matching credentials.

---

### FR-04: Select Matching Credential

The holder must select a credential that satisfies the proof request.

#### Acceptance Criteria

- The app lists valid matching credentials.
- The app prevents proof creation if no matching credential exists.
- The selected credential must match the schema and credential definition required by the verifier.

---

### FR-05: Create Proof Presentation

The holder must create an AnonCred proof presentation from the selected credential.

#### Input

- Proof request
- Selected credential
- Holder link secret/master secret
- Schema
- Credential definition
- Revocation state if revocation is enabled

#### Output

- Proof presentation message

#### Acceptance Criteria

- The proof includes only requested attributes.
- Hidden attributes are not revealed unless requested.
- Predicates can be proven without revealing the original value.
- The generated proof can be sent to the verifier.

---

### FR-06: Send Proof Presentation

The holder must send the generated proof presentation back to the verifier.

#### Acceptance Criteria

- The verifier receives the proof presentation.
- The message is associated with the original proof request.
- The proof presentation has not been modified in transit.

---

### FR-07: Verify Proof Presentation

The verifier must verify the received proof presentation.

#### Input

- Proof request
- Proof presentation
- Schema
- Credential definition
- Revocation registry/status if enabled

#### Output

```json
{
  "verified": true,
  "reason": null
}
```

or

```json
{
  "verified": false,
  "reason": "Invalid proof"
}
```

#### Acceptance Criteria

- A valid proof returns `verified = true`.
- An invalid or modified proof returns `verified = false`.
- Expired or revoked credentials fail verification if revocation is enabled.
- The verifier shows a clear result to the user.

---

## 5. Non-Functional Requirements

### NFR-01: Privacy

The holder must only reveal requested attributes.

Example:

If the verifier only requests `age >= 18`, the holder must not reveal the exact age.

### NFR-02: Security

The verifier must not trust claims without verifying the proof cryptographically.

### NFR-03: Persistence

Issued credentials should remain available after application restart.

### NFR-04: Error Handling

The app must handle:

- No matching credential
- Invalid proof request
- Failed proof generation
- Failed proof verification
- Missing schema
- Missing credential definition
- Revoked credential
- Expired credential
- Network or DIDComm message failure

---

## 6. Suggested Module Design

### Holder Side

```kotlin
interface HolderVerificationManager {
    suspend fun getProofRequests(): List<ProofRequest>
    suspend fun findMatchingCredentials(proofRequest: ProofRequest): List<Credential>
    suspend fun createProof(
        proofRequest: ProofRequest,
        credentialId: String
    ): ProofPresentation

    suspend fun sendProofPresentation(
        proofPresentation: ProofPresentation
    )
}
```

### Verifier Side

```kotlin
interface VerifierVerificationManager {
    suspend fun createProofRequest(
        requestedAttributes: List<String>,
        requestedPredicates: List<Predicate>
    ): ProofRequest

    suspend fun sendProofRequest(
        proofRequest: ProofRequest,
        holderConnectionId: String
    )

    suspend fun verifyProofPresentation(
        proofRequest: ProofRequest,
        proofPresentation: ProofPresentation
    ): VerificationResult
}
```

### Common Models

```kotlin
data class ProofRequest(
    val id: String,
    val name: String,
    val requestedAttributes: List<String>,
    val requestedPredicates: List<Predicate>
)

data class Predicate(
    val attributeName: String,
    val operator: String,
    val value: Int
)

data class ProofPresentation(
    val id: String,
    val proofRequestId: String,
    val data: String
)

data class VerificationResult(
    val verified: Boolean,
    val reason: String? = null
)
```

---

## 7. Basic Flow Sequence

```text
Verifier -> Holder: Send proof request
Holder -> Holder: Find matching credential
Holder -> Holder: Create proof presentation
Holder -> Verifier: Send proof presentation
Verifier -> Verifier: Verify proof
Verifier -> User: Show verification result
```

---

## 8. Example Scenario

### Scenario: Verify Student Credential

A university issued a student credential to a holder.

The verifier wants to check:

- The holder is a student.
- The holder belongs to a specific university.
- The holder is over 18.

The verifier sends this proof request:

```json
{
  "requested_attributes": ["student_id", "university"],
  "requested_predicates": [
    {
      "attribute": "age",
      "operator": ">=",
      "value": 18
    }
  ]
}
```

The holder selects the student credential and creates a proof.

The verifier verifies the proof and receives:

```json
{
  "verified": true,
  "reason": null
}
```

---

## 9. Edge Cases

### Case 1: No Matching Credential

If the holder has no credential that satisfies the proof request, the app must show:

```text
No matching credential found.
```

### Case 2: Revoked Credential

If the credential has been revoked, verification must fail.

```json
{
  "verified": false,
  "reason": "Credential has been revoked"
}
```

### Case 3: Modified Proof

If the proof presentation is modified, verification must fail.

```json
{
  "verified": false,
  "reason": "Invalid proof signature"
}
```

### Case 4: Missing Schema or Credential Definition

If verifier cannot resolve schema or credential definition, verification must fail.

```json
{
  "verified": false,
  "reason": "Missing schema or credential definition"
}
```

---

## 10. AI Implementation Tasks

An AI coding agent should implement the flow in this order:

1. Confirm issued AnonCred credential is stored successfully.
2. Create proof request model.
3. Implement verifier function to send proof request.
4. Implement holder function to receive proof request.
5. Implement holder function to search matching credentials.
6. Implement holder function to create proof presentation.
7. Implement holder function to send proof presentation.
8. Implement verifier function to verify proof presentation.
9. Add UI state for pending verification requests.
10. Add UI result screen for verification success or failure.
11. Add error handling for missing credential, invalid proof, and revoked credential.
12. Add logs for every verification step.

---

## 11. Definition of Done

The verification flow is complete when:

- A credential can be issued and stored.
- A verifier can create and send a proof request.
- A holder can receive the proof request.
- A holder can select a matching credential.
- A holder can generate a proof presentation.
- A verifier can verify the proof presentation.
- The UI shows verification success or failure.
- Invalid, missing, or revoked credentials fail correctly.
