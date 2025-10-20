# Clara Voice Chat - Quick Reference

## 🎯 What Clara Does

Clara conducts a **focused, 5-minute onboarding interview** to gather everything needed to set up your Cleaning Planner app.

### **The 7 Pieces Clara Collects**
1. 🏠 **Rooms** - How many? What types?
2. 📏 **Size** - Square footage?
3. 👥 **People** - Who lives there?
4. 🐕 **Pets** - Any pets?
5. 🗓️ **Frequency** - How often do you clean?
6. ⚠️ **Problem areas** - What needs attention?
7. ⏰ **Time** - How much time available?

---

## 🗣️ How to Use

### **Starting a Conversation**

1. Open app
2. Enter API key (first time only)
3. Tap **"Voice Chat"**
4. Clara greets: *"Hi! I'm Clara. Tell me about your home..."*
5. Answer her questions naturally

### **Example Conversation**

```
Clara: "Tell me about your home - how many rooms?"

You: "3 bedrooms, 2 bathrooms"

Clara: "Got it! Who lives there?"

You: "Me, my wife, and two kids"

Clara: "Great! Any pets?"

You: "We have a dog"

Clara: "Perfect. How often do you clean?"

You: "Once a week"

Clara: "What areas need attention?"

You: "Kitchen gets messy"

Clara: "How much time can you dedicate?"

You: "About 2 hours on weekends"

Clara: "Perfect! Anything else?"

You: "No, that's all"

Clara: "Great! I have everything. Let's get started!"

[Dashboard opens with your info pre-filled] ✨
```

---

## 💬 Conversation Rules

### **✅ Clara Will**:
- Ask focused questions about your home
- Keep responses short (1-2 sentences)
- Ask ONE question at a time
- Redirect if you go off-topic
- Remember your answers
- Detect when you're done

### **❌ Clara Won't**:
- Make small talk about weather/sports
- Answer general knowledge questions
- Tell jokes or stories
- Offer to clean your house
- Switch languages randomly
- Forget her name

---

## 🌍 Language Support

### **Supported Languages**
- 🇺🇸 English (en)
- 🇪🇸 Spanish (es)
- 🇫🇷 French (fr)
- 🇩🇪 German (de)
- 🇺🇦 Ukrainian (uk)

### **How to Switch**
Select your language on the Welcome screen before starting Voice Chat.

**Note**: Language switcher during voice chat is currently not available (coming soon).

---

## 🎤 Voice Features

### **Full-Duplex Conversation**
- ✅ Talk while Clara is speaking (natural interruptions)
- ✅ Clara will stop and listen to you
- ✅ Just like talking to a real person

### **No Echo**
- ✅ Works with phone speaker (no headphones needed)
- ✅ Built-in echo cancellation
- ✅ Clear audio both ways

### **Automatic Turn Detection**
- ✅ No "push to talk" button
- ✅ Server detects when you speak
- ✅ Server detects when you're done (700ms silence)
- ✅ Natural conversation rhythm

---

## 🛑 How to End Conversation

Say any of these phrases:

- "I'm done"
- "That's all"
- "Let's finish"
- "Stop"
- "That's enough"
- "Done"
- "That's it"
- "No more"

Clara will confirm and automatically navigate to your dashboard!

---

## 📱 Technical Requirements

### **Device**
- Android 6.0+ (API 23+)
- Microphone access required
- Internet connection required

### **Network**
- Stable WiFi or 4G/5G
- WebSocket connection
- ~50-100 KB/s bandwidth

### **Permissions**
- 🎤 RECORD_AUDIO (for speaking to Clara)

---

## 🔧 Troubleshooting

### **Clara Doesn't Respond**
- Check internet connection
- Verify API key is configured
- Check microphone permission
- Restart voice chat

### **Echo or Feedback**
- Should not happen with speaker
- If it does: Use headphones
- Or: Hold phone to ear (uses earpiece)

### **Wrong Language**
- Select correct language on Welcome screen
- Restart voice chat
- Check app language settings

### **Clara Goes Off-Topic** (shouldn't happen)
- This is a bug - report it!
- Expected: Clara always redirects to home info

### **Conversation Stuck**
- Say "I'm done" to force completion
- Or use back button to exit
- Session will reset on next visit

---

## ⚡ Performance

| Metric | Value |
|--------|-------|
| **Response Latency** | 320ms-1s |
| **Connection Time** | 1-2s |
| **Language Switch** | <300ms |
| **Conversation Length** | 3-7 minutes typical |
| **Audio Quality** | Broadcast (24kHz) |

---

## 💰 Cost (per conversation)

Typical 5-turn onboarding conversation:
- **Estimated cost**: $0.02-0.05
- **User value**: Complete app setup
- **ROI**: High (10x better UX vs forms)

---

## 🎬 Example Opening Greetings (All Languages)

### **English**
> "Hi! I'm Clara. I'll help set up your cleaning plan. Tell me about your home - how many rooms do you have, and what types?"

### **Spanish**
> "¡Hola! Soy Clara. Te ayudaré a configurar tu plan de limpieza. Háblame de tu hogar - ¿cuántas habitaciones tienes y de qué tipos?"

### **French**
> "Bonjour! Je suis Clara. Je vais vous aider à configurer votre plan de nettoyage. Parlez-moi de votre maison - combien de pièces avez-vous et de quels types?"

### **German**
> "Hallo! Ich bin Clara. Ich helfe dir, deinen Putzplan einzurichten. Erzähl mir von deinem Zuhause - wie viele Räume hast du und welche Arten?"

### **Ukrainian**
> "Привіт! Я Клара. Я допоможу налаштувати ваш план прибирання. Розкажіть мені про ваш дім - скільки у вас кімнат і яких типів?"

---

## 🚀 Quick Start

```bash
# Build
./gradlew assembleDebug

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Run
# (Open app on device)

# Test
1. Enter API key
2. Select language
3. Tap "Voice Chat"
4. Talk to Clara
5. Say "I'm done"
6. View dashboard
```

---

## 📊 User Feedback Metrics to Track

- ⏱️ **Time to complete onboarding**
- ✅ **Completion rate** (% who finish vs abandon)
- 🎯 **Data quality** (% with all 7 pieces)
- 😊 **User satisfaction** (post-onboarding survey)
- 🔄 **Retry rate** (% who restart)
- 📱 **Device compatibility** (% successful on different devices)

---

**🎯 Clara is ready! Build, test, and enjoy natural voice onboarding!** 🚀✨

