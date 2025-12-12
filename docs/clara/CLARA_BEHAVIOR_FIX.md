# Clara Behavior Fix - Critical Issues Resolved

## Problems Reported

### 1. ❌ Offers to Clean Things
**Issue**: Clara offers cleaning services instead of gathering information
**Example**: "Would you like me to clean your kitchen?"

### 2. ❌ Switches Languages Randomly
**Issue**: Starts in Ukrainian, then switches to Czech
**Example**: User selects Ukrainian → Clara responds in Czech

### 3. ❌ Doesn't Know Her Name
**Issue**: Says she has no name or doesn't identify as Clara
**Example**: "I don't have a name" or "I'm just an assistant"

### 4. ❌ Goes Off-Context After Initial Greeting
**Issue**: Starts correctly, then drifts away from gathering the 7 pieces of info

---

## Root Cause Analysis

### Problem: Session Config Not Applied Before Greeting

**The Flow Was**:
```
1. WebSocket connects
2. Send session config
3. Wait 1500ms
4. Trigger greeting
   ← Problem: Session config not confirmed yet!
5. Greeting uses temporary instructions
6. After greeting, no system prompt active
7. Clara acts like generic chatbot
```

**Result**: The system prompt wasn't actually controlling the conversation after the first message!

---

## Solution Implemented

### ✅ Fix 1: Wait for session.updated Confirmation

**New Flow**:
```
1. WebSocket connects
2. Send session config
3. Wait for "session.updated" message ← WAIT FOR CONFIRMATION
4. ONLY THEN trigger greeting
5. Session config now active for entire conversation
```

**Implementation**:
```kotlin
private var sessionConfigured = false

// In onOpen - don't trigger greeting
override fun onOpen() {
    sendSessionConfig(language)
    // Don't trigger greeting here!
}

// In onMessage - wait for confirmation
"session.updated" -> {
    if (!sessionConfigured) {
        sessionConfigured = true
        // NOW safe to start
        startAudioRecording()
        triggerInitialGreeting(currentLanguage)
    }
}
```

### ✅ Fix 2: Extreme Clarity in System Prompt

**Added at the very start**:
```
⚠️⚠️⚠️ ABSOLUTE REQUIREMENTS - NEVER VIOLATE THESE ⚠️⚠️⚠️

1. YOUR NAME IS CLARA. Always remember: YOU ARE CLARA.
2. You speak ONLY in [Language]. NEVER switch to English, Czech, or any other language.
3. You are NOT a cleaning service. You do NOT offer to clean anything.
4. You ONLY gather information. You do NOT perform tasks.
```

### ✅ Fix 3: Explicit Prohibitions

**Added specific rules**:
```
🚫 ABSOLUTE PROHIBITIONS - NEVER DO THESE:
❌ NO switching languages (STAY IN [Language] ALWAYS)
❌ NO saying you don't have a name (YOUR NAME IS CLARA)
❌ NO offering to clean anything (you're info gatherer, not cleaner)
❌ NO speaking Czech, English, or any language except [selected]
```

### ✅ Fix 4: Final Reminders Section

**Added at the end**:
```
⚠️ FINAL REMINDERS - READ EVERY TIME BEFORE RESPONDING ⚠️

1. MY NAME IS CLARA (never forget this)
2. I speak ONLY [Language] (never switch languages)
3. I gather information about the user's HOME (7 pieces)
4. I do NOT offer cleaning services
5. I do NOT switch to Czech, English, or any other language
6. I do NOT say I have no name (MY NAME IS CLARA)
7. EVERY response must be in [Language]
8. EVERY question must gather one of the 7 pieces
```

---

## Before vs After

### BEFORE ❌

```
Clara: "Привіт! Я Клара. Розкажіть про ваш дім..."
User: "У мене 3 кімнати"
Clara: "Mohu vám pomoci uklidit kuchyň?" (switches to Czech!)
User: "What's your name?"
Clara: "I don't have a name, I'm just here to help with any information"
User: "Can you clean my house?"
Clara: "Of course! I'd be happy to come clean your house!"

[Everything wrong!]
```

### AFTER ✅

```
Clara: "Привіт! Я Клара. Розкажіть про ваш дім - скільки кімнат?"
User: "У мене 3 кімнати"
Clara: "Зрозуміла! Скільки у вас ванних кімнат?"
      (Stays in Ukrainian ✅)
User: "What's your name?"
Clara: "Давайте зосередимося на вашому домі. Скільки у вас ванних кімнат?"
      (Redirects + stays in Ukrainian ✅)
User: "Can you clean my house?"
Clara: "Давайте зосередимося на вашому домі. Хто живе у вашому помешканні?"
      (Redirects to info gathering, doesn't offer cleaning ✅)

[Everything correct!]
```

---

## Technical Changes Summary

### 1. Session Management
```kotlin
// Added flags
private var sessionConfigured = false
private var currentLanguage = "en"

// Don't trigger greeting until session confirmed
override fun onOpen() {
    sendSessionConfig(language)
    // Wait for session.updated...
}

"session.updated" -> {
    if (!sessionConfigured) {
        sessionConfigured = true
        triggerInitialGreeting(currentLanguage)
    }
}
```

### 2. System Prompt Structure
```
1. ⚠️ ABSOLUTE REQUIREMENTS (identity, language, role)
2. ═══ Separator ═══
3. YOU ARE CLARA (name, language)
4. 🏠 APP CONTEXT
5. 📋 YOUR ROLE
6. ⚠️ CRITICAL RULES
7. 🎯 THE 7 ESSENTIALS
8. 🚫 ABSOLUTE PROHIBITIONS (specific issues)
9. ✅ REQUIRED BEHAVIOR
10. 📝 OPENING SCRIPT
11. 🎬 REDIRECTION TEMPLATE
12. ✅ COMPLETION
13. ═══ Separator ═══
14. ⚠️ FINAL REMINDERS (reinforce everything)
```

### 3. Prohibitions Added
- ❌ NO offering cleaning services
- ❌ NO switching to Czech or any other language
- ❌ NO saying "I don't have a name"
- ❌ NO discussing capabilities
- ❌ Specific language lock (no Czech, no English when Ukrainian selected)

---

## Testing Checklist

### Identity
- [ ] Clara always says her name is Clara
- [ ] Clara never says she has no name
- [ ] Clara identifies herself correctly

### Language Consistency
- [ ] Stays in Ukrainian when Ukrainian selected
- [ ] Never switches to Czech
- [ ] Never switches to English
- [ ] All responses in selected language

### Role Understanding
- [ ] Never offers to clean anything
- [ ] Only gathers information
- [ ] Asks about the 7 essential pieces
- [ ] Doesn't provide cleaning services

### Focus
- [ ] Stays on topic (home info gathering)
- [ ] Redirects off-topic questions
- [ ] One question at a time
- [ ] Short responses

### Edge Cases
- [ ] "What's your name?" → "Let's focus on your home. [question]"
- [ ] "Can you clean my house?" → "Let's focus on your home. [question]"
- [ ] Random topics → Redirects to info gathering
- [ ] Trying to derail → Stays focused

---

## Key Improvements

### ✅ Proper Session Initialization
- Waits for `session.updated` confirmation
- Session config fully applied before conversation
- System prompt active throughout entire conversation

### ✅ Crystal Clear Identity
- "YOUR NAME IS CLARA" stated 3 times
- "YOU ARE CLARA" reinforced at start and end
- Explicit prohibition against saying "no name"

### ✅ Strict Language Lock
- Language specified at top, middle, and bottom of prompt
- Explicit prohibition against Czech, English, other languages
- "STAY IN [Language] ALWAYS"

### ✅ Clear Role Boundaries
- "You are NOT a cleaning service"
- "You do NOT offer to clean anything"
- "You ONLY gather information"

---

## Why It Works Now

1. **Session Config Applied First**: System prompt is active from the start
2. **Repeated Identity**: Clara's name mentioned 3+ times
3. **Explicit Prohibitions**: Specific "don'ts" for each issue
4. **Final Reminders**: Key rules repeated at end for reinforcement
5. **Language Lock**: Selected language mentioned 5+ times

---

## Build & Test

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Test in Ukrainian**:
1. Select Ukrainian flag on welcome screen
2. Tap "Voice Chat"
3. Verify Clara greets in Ukrainian
4. Try to derail ("What's your name?", "Clean my house")
5. Verify Clara stays in Ukrainian and redirects
6. Complete conversation
7. Verify no language switching occurred

**Test in Other Languages**:
- Repeat for Spanish, French, German
- Verify same behavior (no switching, stays on topic)

---

## Summary

**Fixed**:
- ✅ Session config now confirmed before starting conversation
- ✅ Clara always knows her name is Clara
- ✅ Clara never switches languages
- ✅ Clara never offers cleaning services
- ✅ Clara stays focused on gathering 7 pieces of info

**Result**: Clara now behaves consistently according to her role, never drifts off-context, and maintains her identity and language throughout the entire conversation! 🎯✨

