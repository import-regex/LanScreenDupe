##  LanScreenDupe

**LanScreenDupe** is a private screen mirroring application with remote control functionality. It mirrors your Android device's screen to a web browser over a local Wi-Fi, hotspot or bluetooth connection - completely offline.

###  Features
- **Private and offline:** Works over **local** Wi-Fi, Hotspot, Bluetooth tethering or Ethernet. No internet used, no data stored, no ads.
- **High-Performance:** Encoded video stream (WebRTC) and user-selected quality settings.
- **Web-Based Viewer:** No client apps. Load a URL in Chrome (or related), Firefox or Safari.
- **Remote Control:** Send taps and swipes from the browser back to the Android device.
- **Audio Support:** Multiple microphone capture modes (off, standard and raw).
- **Photography Optimized:** Can enable fixed tap coordinates for reliable shutter triggering.

###  Setup & Usage
1. **Connect:** Ensure both devices share the same network - Wi-Fi, Hotspot or Bluetooth tethering.
2. **Start:** Open LanScreenDupe on your Android and accept the screen capture permission.
3. **View:** Open the URL shown in the app (e.g., `http://192.168.1.5:8080`) on your remote device.
4. **Interact:** Enable "Remote Control" in the app settings to allow the browser to send touch events.
5. **Set:** Toggle various video quality, audio mode settings and tap behavior settings in the menu and load/reload the page in the browser to apply.
   
###  Download
- Visit the [releases](https://github.com/import-regex/LanScreenDupe/releases/) page and download the latest .apk under *"> Assets"* (click to expand)

###  Requirements
 **Android:** 
<br>5.0 - Lollipop for basic screen sharing 
<br>7.0 - Nougat for remote control
<br>8.0 - Oreo for high quality remote control

 **Browser:**
<br>Any modern browser with WebRTC support

###  License
This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the [`LICENSE`](https://github.com/import-regex/LanScreenDupe/edit/main/LICENSE) file for details.

###  Acknowledgments
- Inspired by the [scrcpy](https://github.com/Genymobile/scrcpy) project.
- Built with [LiveKit](https://livekit.io/) (WebRTC) and [Ktor](https://ktor.io/).
