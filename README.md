# OverQuack Android Client

An Android app to manage `.oqs` payload files on OverQuack devices (Raspberry Pi Pico W). This app allows you to connect to the device over Wi-Fi, upload, run, read, delete, and download `.oqs` payloads with advanced features like free memory checking and reliable upload handling.


## 🫡 Acknowledgments

This App is inspired from [VexilonHacker](https://github.com/VexilonHacker/OverQuack). I liked his project and since its seems like OverQuack only supports Linux so I thought on making an app to support his Work. I love Raspberry Pico W 💕 how well it works with OverQuack code.

![OverQuack Android Client](https://img.shields.io/badge/Platform-Android-green)
![API Level](https://img.shields.io/badge/API%20Level-24+-blue)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen)

---

## 📱 Features

### Core Functionality
- **🔗 Auto-Connect**: Automatically discover OverQuack Pico W devices on local network
- **📋 List Payloads**: Display all `.oqs` payload files stored on the device
- **⬆️ Smart Upload**: Upload `.oqs` payloads with **free memory validation**
- **▶️ Execute Payloads**: Run `.oqs` payloads remotely on the device
- **👁️ Read Content**: View payload code and commands
- **🗑️ Safe Delete**: Remove payloads with confirmation dialog
- **⬇️ Download**: Save payloads to Android Downloads folder

### Advanced Features
- **🧠 Memory Management**: Checks device free memory before upload (75% safety limit)
- **🔄 Retry Logic**: Robust network handling with automatic retry for large files
- **🎨 Material Design 3**: Modern, responsive UI with card-based layout
- **🔍 Multi-Port Discovery**: Automatic detection on ports 80, 8000, 8080
- **📱 File Picker Integration**: Native Android file selection for uploads
- **⚡ Async Operations**: Smooth UI with Kotlin coroutines

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio**: Iguana | 2023.2.1 or newer
- **Android SDK**: Platform 34 (Android 14)
- **Java**: Version 21
- **Gradle**: 8.4
- **Minimum Android**: API 24 (Android 7.0)

### Installation

1. **Clone the Project**
   ```bash
   git clone <your-repo-url>
   cd OverQuack
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Open the `OverQuack` project folder
   - Wait for Gradle sync to complete

3. **Build & Install**
   - Connect your Android device or start an emulator
   - Click "Run" to build and install the app

4. **Network Setup**
   - Ensure your Android device is on the same Wi-Fi network as the OverQuack Pico W device
   - The device should be accessible at IP `10.10.5.1` on one of the supported ports

---

## 📖 Usage Guide

### 1. **Connecting to Device**
- Tap **"Connect to Device"**
- App automatically scans ports 80, 8000, 8080
- Connection status displays in real-time
- Success shows "Connected to [device-url]"

### 2. **Managing Payloads**
**View All Payloads:**
- Connected app automatically lists all `.oqs` files
- Displays count: "Found X .oqs payload(s)"

**Payload Actions:**
- **Tap any payload** → Opens action menu
- **Run**: Execute payload on device immediately  
- **Read Content**: View payload code in dialog
- **Delete**: Remove from device (with confirmation)
- **Download**: Save to Android Downloads folder

### 3. **Uploading Payloads**
**Smart Upload Process:**
1. Tap **"Upload .oqs Payload"**
2. Select `.oqs` file from device storage
3. App checks device free memory automatically
4. Shows **"Max file size: X KB"** based on available memory
5. Upload proceeds if file size is safe (<75% of free memory)
6. Large files show warning with "Upload Anyway" option

**Memory Safety:**
- Prevents uploads that could crash the device
- Uses 75% of free memory as safe upload limit (like Linux client)
- Clear feedback on file size vs. available memory

### 4. **Additional Features**
- **Refresh Button** (↻): Reload payload list manually
- **Status Updates**: Real-time feedback for all operations
- **Error Handling**: Clear error messages with suggested actions
- **Retry Logic**: Automatic retry for failed large uploads

---

## 🔧 Technical Details

### Network Protocol
The app communicates with OverQuack devices using HTTP POST requests:

| Command | Purpose | Format |
|---------|---------|--------|
| `SEP` | Get separator string | Returns device separator |
| `LS` | List all files | Returns file list |
| `FREE_MEM` | Check available memory | Returns free memory in bytes |
| `RUN{sep}{filename}` | Execute payload | Runs specified payload |
| `READ{sep}{filename}` | Read payload content | Returns file content |
| `DELETE{sep}{filename}` | Delete payload | Removes specified file |
| `WRITE{sep}{filename}{sep}\n{content}` | Upload payload | Writes file to device |

### Memory Management
- Mirrors Linux client behavior exactly
- Sends `FREE_MEM` command before upload
- Calculates safe upload limit: `safeMemory = freeMemory * 0.75`
- Compares file size against safe limit
- Shows warning dialog for oversized files

### File Handling
- **Upload**: Native Android file picker with `.oqs` filtering
- **Download**: Uses MediaStore API to save to Downloads folder
- **Content Reading**: UTF-8 encoding with proper error handling
- **Filename Sanitization**: Removes leading dots (matches Linux client)

---

## 📁 Project Structure

```
OverQuack/
├── app/
│   ├── build.gradle.kts                    # App-level build configuration
│   ├── src/main/
│   │   ├── java/com/overquack/android/
│   │   │   └── MainActivity.kt             # Main application logic
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml       # Main UI layout
│   │   │   │   └── item_payload.xml        # Payload item layout
│   │   │   ├── values/
│   │   │   │   ├── colors.xml              # App color scheme
│   │   │   │   ├── strings.xml             # Text resources
│   │   │   │   └── themes.xml              # Material Design themes
│   │   │   └── xml/
│   │   │       ├── backup_rules.xml        # Backup configuration
│   │   │       └── data_extraction_rules.xml
│   │   └── AndroidManifest.xml             # App permissions & config
├── build.gradle.kts                        # Project-level build config
├── gradle.properties                       # Gradle settings
├── settings.gradle.kts                     # Project settings
└── README.md                               # This file
```

---

## 🔐 Permissions

The app requires these permissions:

- **INTERNET**: Network communication with OverQuack device
- **ACCESS_NETWORK_STATE**: Check network connectivity  
- **READ_EXTERNAL_STORAGE**: Access files for upload (Android 12 and below)
- **READ_MEDIA_***: Access media files for upload (Android 13+)

*Note: Downloads to public folder use MediaStore API and don't require additional permissions on Android 10+*

---

## 🛠️ Dependencies

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
```

---

## ⚠️ Important Notes

### Hardware Requirements
- **GPIO Pin 5** must be connected to GND on the Pico W for write operations (upload/delete)
- Device must be running compatible OverQuack firmware
- Wi-Fi connection required between Android device and Pico W

### Compatibility
- **Minimum Android**: 7.0 (API 24)
- **Target Android**: 14 (API 34)
- **Architecture**: ARM, ARM64, x86, x86_64

### Known Limitations
- Upload size limited by device free memory
- Single device connection at a time
- Requires same Wi-Fi network as target device

---

## 🐛 Troubleshooting

### Connection Issues
- **Can't connect**: Verify both devices on same Wi-Fi network
- **Connection timeout**: Check if device is powered and running
- **Wrong IP**: Modify `DEFAULT_IP` in code if device uses different IP

### Upload Problems  
- **Upload fails**: Ensure GPIO pin 5 is connected
- **File too large**: Check device memory or use "Upload Anyway"
- **Invalid file**: Ensure file has `.oqs` extension

### General Issues
- **App crashes**: Check Android version compatibility (API 24+)
- **Slow performance**: Close other apps to free memory
- **UI freezing**: Restart app and reconnect

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

### Development Setup
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- OverQuack community for the original project concept
- Android development community for guidance and best practices
- Contributors who helped test and improve the application

---

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Troubleshooting](#-troubleshooting) section
2. Search existing [Issues](../../issues)
3. Create a new issue with detailed information

---

**Built with ❤️ for the OverQuack community**
