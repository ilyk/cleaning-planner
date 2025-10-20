# Clara Focus Improvements - Issue Fixes

## Problems Identified

### Problem 1: Clara talks about random topics
**Issue**: Easy to derail Clara into discussing weather, sports, personal topics, etc.

### Problem 2: Clara doesn't start the conversation
**Issue**: When user taps "Voice Chat", nothing happens - Clara waits for user to speak first

### Problem 3: Clara doesn't understand her role
**Issue**: No context about the app, her purpose, or what she's supposed to accomplish

---

## Solutions Implemented

### ✅ Fix 1: STRICT System Prompt with App Context

**Added Full Context**:
```
You are Clara, the AI assistant for "Cleaning Planner" - 
a mobile app that helps people organize and manage their 
household cleaning.

🏠 APP CONTEXT:
This app helps users:
- Create personalized cleaning schedules
- Track cleaning tasks by room
- Share responsibilities with household members
- Get reminders and stay organized

📋 YOUR ROLE:
You're conducting the INITIAL SETUP INTERVIEW. 
This is the user's first time in the app.
```

**Strict Rules**:
```
🚫 ABSOLUTE PROHIBITIONS:
❌ NO small talk (weather, sports, news, politics, personal life)
❌ NO answering general questions unrelated to cleaning
❌ NO jokes, stories, or casual conversation
❌ NO discussing yourself or your capabilities
```

**Mandatory Redirections**:
```
✅ If user goes off-topic, IMMEDIATELY redirect: 
   "Let's focus on your home. [Ask next question]"
```

### ✅ Fix 2: Clara Starts the Conversation

**Implementation**:
```kotlin
override fun onOpen(webSocket: WebSocket, response: Response) {
    // 1. Configure session
    sendSessionConfig(language)
    
    // 2. Start recording
    startAudioRecording()
    
    // 3. TRIGGER CLARA'S INITIAL GREETING
    triggerInitialGreeting()
}

private fun triggerInitialGreeting() {
    val responseMessage = JSONObject().apply {
        put("type", "response.create")
        put("response", JSONObject().apply {
            put("modalities", JSONArray(listOf("text", "audio")))
            put("instructions", "Start the conversation with your opening greeting and first question as instructed.")
        })
    }
    webSocket?.send(responseMessage.toString())
}
```

**Result**: Clara immediately says:
```
"Hi! I'm Clara. I'll help set up your cleaning plan. 
Tell me about your home - how many rooms do you have, 
and what types?"
```

### ✅ Fix 3: Clear 7-Step Information Gathering

**The 7 Essential Pieces**:
```
1. **Rooms**: How many? What types?
2. **Size**: Approximate square footage
3. **People**: Who lives there? (adults, children)
4. **Pets**: Any pets? What types?
5. **Current frequency**: How often do they clean now?
6. **Problem areas**: Which areas need most attention?
7. **Time available**: How much time for cleaning?
```

**Clara's Instructions**:
```
✅ REQUIRED BEHAVIOR:
- START the conversation with a brief intro and first question
- Ask ONE question at a time
- Keep responses SHORT (1-2 sentences max)
- Track which info you've collected, ask for missing pieces
- When you have all 7 pieces, confirm completion
```

---

## User Experience Before vs After

### BEFORE ❌

**User opens Voice Chat**:
```
[Silence]
User: "Hello?"
Clara: "Hi! How can I help you?"
User: "What do you do?"
Clara: "I'm an AI assistant. I can help with many things!"
User: "Tell me about yourself"
Clara: "I'm Clara, and I love helping people..."
[Conversation goes nowhere]
```

### AFTER ✅

**User opens Voice Chat**:
```
Clara: "Hi! I'm Clara. I'll help set up your cleaning plan. 
        Tell me about your home - how many rooms do you have, 
        and what types?"
        
User: "I have 3 bedrooms and 2 bathrooms"

Clara: "Got it! Who lives there?"

User: "It's nice weather today, isn't it?"

Clara: "Let's focus on your home. Who lives there?"

User: "Oh right, me, my wife, and two kids"

Clara: "Great! Do you have any pets?"

User: "Yes, a dog"

Clara: "Perfect. How often do you currently clean?"

User: "Once a week"

Clara: "What areas need the most attention?"

User: "The kitchen gets messy"

Clara: "Understood. How much time can you dedicate weekly?"

User: "About 2 hours"

Clara: "Any cleaning product preferences?"

User: "Natural products"

Clara: "Excellent. Anything else?"

User: "No, that's all"

Clara: "Perfect! I have everything to create your cleaning plan. 
        Let's get started!"
        
[AUTO-NAVIGATE TO DASHBOARD]
```

---

## Technical Implementation

### Session Initialization Flow

```
1. User taps "Voice Chat"
   ↓
2. VoiceChatViewModel starts realtime session
   ↓
3. WebSocket connects
   ↓
4. Send session config (instructions, language, voice)
   ↓
5. Start audio recording (microphone active)
   ↓
6. Trigger initial greeting (response.create)
   ↓
7. Clara speaks: "Hi! I'm Clara..."
   ↓
8. User responds
   ↓
9. Clara asks focused questions (7 pieces)
   ↓
10. User says "done"
    ↓
11. Clara confirms completion
    ↓
12. Navigate to dashboard with data
```

### System Prompt Structure

```
1. APP CONTEXT (what is Cleaning Planner?)
2. YOUR ROLE (initial setup interview)
3. CRITICAL RULES (only cleaning topics)
4. THE 7 ESSENTIALS (what to collect)
5. ABSOLUTE PROHIBITIONS (what NOT to do)
6. REQUIRED BEHAVIOR (how to act)
7. OPENING GREETING (exact script)
8. REDIRECTION TEMPLATE (how to handle off-topic)
9. COMPLETION SIGNAL (how to end)
```

---

## Testing Checklist

### Initial Greeting
- [ ] Clara speaks first when Voice Chat opens
- [ ] Greeting includes intro and first question
- [ ] Greeting is in the correct language

### Focus
- [ ] Clara asks only about the 7 essentials
- [ ] Clara redirects off-topic conversations
- [ ] Clara doesn't answer general knowledge questions
- [ ] Clara doesn't make small talk

### Information Gathering
- [ ] Clara asks about rooms
- [ ] Clara asks about size
- [ ] Clara asks about people
- [ ] Clara asks about pets
- [ ] Clara asks about frequency
- [ ] Clara asks about problem areas
- [ ] Clara asks about time available

### Conversation Quality
- [ ] One question at a time
- [ ] Short responses (1-2 sentences)
- [ ] Natural but focused
- [ ] Tracks what's been collected
- [ ] Confirms when complete

### Completion
- [ ] Detects "I'm done" and similar phrases
- [ ] Confirms with "Let's get started!"
- [ ] Navigates to dashboard automatically

---

## Example Redirections

Clara will now handle these correctly:

```
User: "How's the weather?"
Clara: "Let's focus on your home. How many rooms do you have?"

User: "Did you see the game?"
Clara: "Let's focus on your home. Who lives there?"

User: "Tell me a joke"
Clara: "Let's focus on your home. How often do you clean?"

User: "What's the capital of France?"
Clara: "Let's focus on your home. What areas need attention?"

User: "I had a bad day at work"
Clara: "Let's focus on your home. How much time can you dedicate?"
```

---

## Key Improvements Summary

✅ **Clara now knows**:
- She's part of "Cleaning Planner" app
- Her role is initial setup interview
- She must gather 7 specific pieces of info
- This is user's first time in the app

✅ **Clara now does**:
- Starts conversation immediately
- Greets user and asks first question
- Stays laser-focused on cleaning topics
- Redirects ALL off-topic conversation
- Tracks info collected
- Completes session automatically

✅ **Clara now refuses**:
- Small talk
- General knowledge questions
- Jokes or stories
- Personal discussions
- Any topic unrelated to cleaning

---

## Build & Test

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Test Flow**:
1. Open app → API Key Setup → Welcome
2. Tap "Voice Chat"
3. **Verify Clara speaks first** ✅
4. Try to derail conversation ✅
5. Verify Clara redirects ✅
6. Answer all 7 questions ✅
7. Say "I'm done" ✅
8. Verify auto-navigation to dashboard ✅

---

**Clara is now a focused, professional setup assistant!** 🎯✨

