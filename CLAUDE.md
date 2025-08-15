# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

XMTP Android is a Kotlin SDK for building messaging applications on the XMTP protocol. The project consists of:

- **Library module** (`library/`): Core XMTP SDK implementation
- **Example app** (`example/`): Demo application showcasing SDK usage
- **Native bindings**: Uses libxmtp via JNI for core messaging functionality

## Architecture

### Core Components

- **Client** (`Client.kt`): Main entry point for XMTP functionality
- **Conversations** (`Conversations.kt`, `Conversation.kt`): Message thread management
- **Codecs** (`codecs/`): Content encoding/decoding (text, attachments, reactions, etc.)
- **Groups** (`Group.kt`, `Dm.kt`): Group messaging and direct messages
- **Push notifications** (`push/`): Firebase/notification integration
- **Crypto utilities** (`Crypto.kt`, `SignedData.kt`): Cryptographic operations

### Key Directories

- `library/src/main/java/org/xmtp/android/library/`: Main library source
- `library/src/androidTest/`: Integration tests (require emulator)
- `library/src/test/`: Unit tests
- `example/src/main/`: Example application source
- `library/src/main/jniLibs/`: Native library binaries

## Development Commands

### Building
```bash
./gradlew build
```

### Testing
```bash
# Unit tests
./gradlew library:testDebug

# Integration tests (requires emulator and Docker)
dev/up  # Start local XMTP infrastructure
./gradlew connectedCheck
```

### Linting and Code Quality
```bash
# Kotlin linting
./gradlew ktlintCheck

# Android lint
./gradlew :library:lintDebug

# Fix lint issues
./gradlew ktlintFormat
```

### Local Development Environment
```bash
# Start local XMTP node and infrastructure
dev/up

# Or using Docker directly
script/local
```

### Example App
```bash
# Build example app
./gradlew :example:assembleDebug

# Install example app
./gradlew :example:installDebug
```

## Development Notes

### Testing Requirements
- Integration tests require an Android emulator (API level 29+)
- Local XMTP infrastructure via Docker (`dev/up`) must be running for tests
- Use `dev/up` to start the local test environment

### Code Style
- Uses ktlint for Kotlin code formatting
- Follows Android SDK patterns and conventions
- Minimum SDK: API 23 (library), API 27 (example)
- Target SDK: 35 (library), 34 (example)
- Java 17 compatibility

### Native Dependencies
- Uses libxmtp via JNI bindings
- Native libraries are pre-built and included in `jniLibs/`
- Protobuf used for message serialization

### Key Libraries
- Kotlin Coroutines for async operations
- gRPC for network communication
- Protobuf for message serialization
- Tink for cryptography
- Web3j for Ethereum operations

### Release Process
- Publishes to Maven Central as `org.xmtp:android`
- Uses environment variables for versioning and signing
- Documentation generated with Dokka