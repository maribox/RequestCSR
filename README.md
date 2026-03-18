# RequestCSR

Generate an RSA 4096 key pair on your Android device, export a PKCS#10 Certificate Signing Request (CSR), then import the CA-signed certificate into the system KeyChain.

The private key is generated on-device and installed into the Android system KeyChain, which hardware-backs it on supported devices (Titan M2, TEE). It never leaves the phone.

## Flow

1. **Generate Key + CSR** -creates an RSA 4096 key pair and a CSR. The CSR is saved to Downloads.
2. **Get the CSR signed** -transfer the `.csr.pem` to your CA and sign it.
3. **Import signed certificate** -tap the + button next to the pending key, select the signed `.crt` file. The app bundles the private key + signed cert into a PKCS#12 and launches the Android system KeyChain installer.
4. **Use the certificate** -apps like OpenVPN for Android can now select it from the system KeyChain.

## Example: OpenVPN with hardware-bound client keys

The traditional way to set up OpenVPN client certificates is to generate the key on a server, bundle it into a `.p12` or `.ovpn` file, and transfer it to the phone. The problem: the private key exists as a copyable file. Anyone who gets the `.ovpn` can clone your VPN identity.

With RequestCSR, the private key is generated **on the phone** and never leaves it:

1. Open RequestCSR, tap **Generate Key + CSR**, save `client.csr.pem`
2. Transfer the CSR to your server and sign it with your CA (e.g. EasyRSA):
   ```
   easyrsa sign-req client phone
   ```
3. Transfer the signed certificate back to the phone
4. In RequestCSR, tap **+** next to the pending key, select the signed `.crt` -the app installs the key + cert into the Android system KeyChain
5. In OpenVPN for Android, import your `.ovpn` profile (which contains only the CA cert and tls-crypt key -no client cert or private key)
6. Edit the profile, select the certificate from the system KeyChain, and connect

**What's protected:**
- The `.ovpn` file alone is useless -it contains no client credentials, only the CA cert (public) and tls-crypt key (DoS protection)
- The private key lives in the system KeyChain, hardware-backed by TEE/Titan M2 on supported devices
- Even if the phone is rooted, the key cannot be extracted from hardware-backed storage
- If the phone is lost, revoke the certificate server-side -no one can clone the key to another device

## Building

```
./gradlew assembleDebug
```

## AI disclosure

This app was built with the help of Claude (Anthropic).

## License

MIT
