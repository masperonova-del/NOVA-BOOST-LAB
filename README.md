# NOVA BOOST LAB 0.1.0

Standalone Android Compose project for testing network connection metrics and preparing a future NOVA relay/VPN implementation.

## Current features
- Independent Android application shell.
- TCP connection timing to `connectivitycheck.gstatic.com:443`.
- HTTPS request timing to a connectivity endpoint.
- VPN permission flow via Android `VpnService.prepare`.
- Clear UI distinction between direct measurements and unconfigured relay.

## Important limitations
- The VPN service is deliberately a scaffold and does not create a TUN interface.
- No traffic is rerouted and no relay server exists in this prototype.
- TCP/HTTPS timings are not the in-game UDP ping of Call of Duty Mobile.
- Intermediate hop timings are not yet implemented; Android networks may block ICMP/traceroute probes.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), allow Gradle sync, then run:
`./gradlew assembleDebug`

The generated APK will be at:
`app/build/outputs/apk/debug/app-debug.apk`

No Gradle wrapper is included in this first source package. Android Studio can import the project using its installed Gradle tooling, or a wrapper can be added in the next iteration.
