## LanScreenDupe

**LanScreenDupe** is a high-performance, low-latency screen mirroring application tailored for outdoors photography or just general remote interaction. It allows you to mirror your Android device's screen to ANY device with a web browser over a local Wi-Fi or hotspot connection - completely offline.

###  Features
- **Offline First:** No internet used. Works over local Wi-Fi, Hotspot, Bluetooth tethering or Ethernet.
- **High-Performance:** Encoded video stream (WebRTC) and user-selected quality settings.
- **Web-Based Viewer:** No client app used. Just open a URL in Chrome, Firefox or Safari.
- **Remote Control:** Send taps and swipes from the browser back to the Android device.
- **Audio Support:** Multiple microphone capture modes (off, standard and raw).
- **Photography Optimized:** Can enable fixed tap coordinates for reliable shutter triggering.
- **Absolute Privacy:** No data leaves your local network. NO accounts, NO tracking, NO ads.

###  Setup & Usage
1. **Connect:** Ensure both devices share the same network - Wi-Fi, Hotspot or Bluetooth tethering.
2. **Start:** Open LanScreenDupe on your Android and accept the screen capture permission.
3. **View:** Open the URL shown in the app (e.g., `http://192.168.1.5:8080`) on your remote device.
4. **Interact:** Enable "Remote Control" in the app settings to allow the browser to send touch events.
5. **Set** Toggle various video quality, audio mode settings and tap behavior settings in the menu and load/reload the page in the browser to apply.

###  Requirements
- **Android:** 
5.0 - Lollipop for basic screen sharing 
7.0 - Nougat for remote control
8.0 - Oreo for high quality remote control
- **Browser:** Any modern browser with WebRTC support (Chrome, Firefox, Safari).

###  License
This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the `LICENSE` file for details.

###  Acknowledgments
- Inspired by the [scrcpy](https://github.com/Genymobile/scrcpy) project.
- Built with [LiveKit](https://livekit.io/) (WebRTC) and [Ktor](https://ktor.io/).
