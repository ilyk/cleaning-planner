#!/bin/bash

# Cleaning Planner - Quick Setup Script

set -e

echo "🧹 Cleaning Planner - Project Setup"
echo "===================================="
echo ""

# Check if Android SDK is configured
if [ ! -f "local.properties" ]; then
    echo "⚠️  local.properties not found!"
    echo "📝 Please create local.properties from local.properties.template"
    echo "   and set your Android SDK path."
    echo ""
    echo "Example:"
    echo "  sdk.dir=/home/yourusername/Android/Sdk"
    echo ""
    read -p "Press Enter to continue anyway..."
fi

# Check Java version
echo "☕ Checking Java version..."
java -version 2>&1 | head -n 1

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17 or higher is required!"
    echo "   Current version: $JAVA_VERSION"
    exit 1
fi
echo "✅ Java version OK"
echo ""

# Make gradlew executable
echo "🔧 Making gradlew executable..."
chmod +x gradlew
echo "✅ Done"
echo ""

# Clean build
echo "🧹 Cleaning previous builds..."
./gradlew clean || echo "⚠️  Clean failed, continuing anyway..."
echo ""

# Build project
echo "🔨 Building project..."
echo "   This may take a few minutes on first run..."
./gradlew build -x test --no-daemon || {
    echo ""
    echo "❌ Build failed!"
    echo ""
    echo "Common issues:"
    echo "  1. Missing local.properties - copy from local.properties.template"
    echo "  2. Android SDK not installed"
    echo "  3. Missing google-services.json (optional for MVP)"
    echo ""
    echo "If google-services.json is missing:"
    echo "  - Comment out the google-services plugin in app/build.gradle.kts"
    echo "  - Or create a Firebase project and download the file"
    echo ""
    exit 1
}

echo ""
echo "✅ Build successful!"
echo ""
echo "📱 Next steps:"
echo "  1. Open the project in Android Studio"
echo "  2. Sync Gradle files"
echo "  3. Run on an emulator or device"
echo ""
echo "Or run directly with:"
echo "  ./gradlew installDebug"
echo ""
echo "🎉 Setup complete!"

