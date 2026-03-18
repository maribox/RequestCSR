# RequestCSR

Generate an RSA 4096 key pair on your Android device, export a PKCS#10 Certificate Signing Request (CSR), then import the CA-signed certificate into the system KeyChain.

The private key is generated on-device and installed into the Android system KeyChain, which hardware-backs it on supported devices (Titan M2, TEE). It never leaves the phone.

## Flow

1. **Generate Key + CSR** — creates an RSA 4096 key pair and a CSR. The CSR is saved to Downloads.
2. **Get the CSR signed** — transfer the `.csr.pem` to your CA and sign it.
3. **Import signed certificate** — tap the + button next to the pending key, select the signed `.crt` file. The app bundles the private key + signed cert into a PKCS#12 and launches the Android system KeyChain installer.
4. **Use the certificate** — apps like OpenVPN for Android can now select it from the system KeyChain.

## Use case

This app was built for OpenVPN client certificate authentication where the private key must stay on the device. OpenVPN for Android uses the system KeyChain (`KeyChain.choosePrivateKeyAlias()`) to select client certificates, so the key must be installed there rather than in the app-private Android Keystore.

## Building

```
./gradlew assembleDebug
```

## License

MIT
