# Mobile verifier cloud-agent HTTP flow

This mobile-app flow has been removed.

AnonCreds verification no longer sends a presentation request from the wallet to a cloud-agent `present-proof` endpoint, waits for an invitation URL, and renders that URL as a QR code. Use the local Bluetooth proof exchange from the `Verify` screen for mobile-to-mobile AnonCreds verification.

The app may still read cloud-agent credential definitions and schemas to build the list of supported AnonCreds credential types. The proof request itself is now created locally and sent over Bluetooth.
