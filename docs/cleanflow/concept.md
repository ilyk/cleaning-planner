# 🧹 CleanFlow — Smart Adaptive Cleaning Planner

## 🌟 Overview

**CleanFlow** is a smart, flexible home-cleaning planner designed for people who struggle to stay focused or organized.  
It combines intelligent scheduling, paper-based checklists, and optional family collaboration — all powered by adaptive learning.

You can use it as:
- A simple automatic cleaning plan (for minimalists),
- A detailed home assistant (for those who love structure),
- Or a hybrid paper-digital system (for traditional users or families).

---

## 🏠 Core Features

### 1. Smart Home Setup
During setup, users specify:
- Home size (number of rooms, floors, outdoor areas),
- Pets (cats, dogs, etc.),
- Cleaning devices (robot vacuum, etc.),
- Custom detail level (minimal or full control).

The app then automatically generates a **personalized cleaning plan**.

---

### 2. Adaptive Cleaning Plans
The system produces:
- **Daily Plan** — short routines to maintain cleanliness,
- **Weekly Plan** — deeper, less frequent tasks,
- **Seasonal/Custom Plans** — spring cleaning, holidays, etc.

Plans adapt dynamically based on:
- Home configuration,
- Task completion history,
- Personal habits and preferred order.

---

### 3. Learning Engine (AI Behavior Model)
The app *learns* from your actions:
- Which tasks you complete, delay, or skip,
- Actual cleaning durations,
- Preferred times and energy patterns,
- Typical comments (e.g., “ran out of detergent”).

It then automatically adjusts:
- Task frequency,
- Time estimates,
- Cleaning order,
- Focus level (shorter or longer sessions).

> “You usually clean bathrooms on weekends — should I make that a rule?”

---

### 4. Focus & Energy Modes
- **Focus Mode:** Guided 10–15 minute micro-sessions (Pomodoro style).
- **Full Reset:** Deep-cleaning mode for weekends.
- **Low Energy Mode:** Light, achievable plan when tired.
- **Pet Mode:** Adjusts routines for homes with animals.

---

## 🖨️ Paper & Hybrid Features

### 5. Printable Daily Plan
Users can generate a **printable PDF** version of their daily or weekly plan.

Options:
- Simple or detailed layout,
- With or without QR codes,
- Assignments per person or shared plan.

Each printout includes:
- Tasks grouped by room,
- Checkboxes for “Done / Skipped,”
- Time fields,
- Comment lines,
- Optional **QR codes** for digital tracking.

Example:
```
🧹 DAILY CLEANING — Saturday, Oct 19
ROOM: Kitchen
[ ] Wipe surfaces        ☐ Done ☐ Skipped
Time: ____ min
Comments: ___________________________
QR: ⬜
```

---

### 6. QR-Based Task Feedback
Each printed task has a **QR code** linking to a lightweight mobile web form.

When scanned, it opens:
```
🧽 Kitchen — “Wipe surfaces”
Estimated: 7 min
[✔️ Done] [⏭️ Skip]
⏰ Time: [07:00]
💬 Comments: [ran out of detergent] [heavy stains] [+ Add your own]
```

Data collected from QR submissions updates your stats:
- Task completion,
- Time accuracy,
- Missing items or notes.

This enables effortless syncing from paper → app.

---

### 7. OCR Scan Import
Prefer handwriting? No problem.

After filling out the printed sheet manually:
- Take a photo or scan it.
- The app’s OCR reads:
  - Checkmarks (“done / skipped”),
  - Written time durations,
  - Comments or issues.

AI analyzes the input and updates your learning profile automatically.

---

## 🧒 Single-Room / Kids Focus Mode

### 🎯 Purpose
A mode designed for:
- **Children** — to make cleaning their own room easy and fun.
- **Teenagers or ADHD users** — to focus on one small, achievable area.

### ✨ Features
- “Clean only one room” generator.
- Simple steps with icons and big text.
- Optional timer and visual rewards.
- Printable version with large checkboxes.
- QR codes for marking completion.
- Parents can scan and view progress or notes.

Example:
```
🧒 MY ROOM PLAN
1️⃣ Pick up toys.
2️⃣ Make your bed.
3️⃣ Wipe your desk.
4️⃣ Vacuum the floor.
✅ Done! Great job!
```

Encourages independence and builds consistent habits.

---

## 👨‍👩‍👧‍👦 Family Mode — Shared Home, Shared Plan

### 🎯 Purpose
Enable the entire household to share one home setup and divide tasks smoothly.

### 🧩 Features
- One shared “Home” profile.
- Multiple family members (with names, colors, or avatars).
- Automatic or manual task assignment.
- Personal task views (“My plan for today”).
- Combined family progress tracker (“80% done!”).
- Printable overview with everyone’s duties.

Example:
```
🏡 FAMILY PLAN – Saturday, Oct 19

👩 Mom:
 - Kitchen counters
 - Laundry

🧑 Dad:
 - Trash & recycling
 - Backyard cleanup

👧 Teen:
 - Bedroom tidy-up
 - Vacuum

🧒 Kid:
 - Pick up toys
 - Wipe table
```

Each task can still include a QR code for completion tracking.

The app tracks:
- Individual completion history,
- Workload fairness,
- Rotation (e.g., “bathroom duty switches weekly”).

---

## 🧠 Intelligence & Motivation

### Dynamic Recommendations
The app gently suggests improvements:
- “This task took longer — increase its estimate?”
- “You ran out of detergent twice this week — add to shopping list?”
- “You skipped dusting often — merge it into another session?”

### Progress Motivation
- Visual progress rings per day or family.
- Positive, non-judgmental tone.
- Optional ambient sound or light gamification.

---

## 🎨 Design Philosophy
- Minimalist, calm interface (Notion + Calm style).
- Voice commands (“What should I clean today?”).
- Gentle animations and sound cues.
- Never guilt-driven — purely supportive.

---

## ⚙️ Technical Architecture (Concept)
| Layer | Description |
|-------|--------------|
| **Knowledge Graph** | Structured hierarchy of rooms, tasks, frequencies, and contexts. |
| **AI Engine** | Learns from habits, times, and feedback (QR or OCR). |
| **Paper Bridge** | PDF generator + QR encoder for hybrid users. |
| **OCR Engine** | Reads physical checklists back into digital data. |
| **Family Sync** | Shared home state with user-based filtering and task assignment. |
| **Offline Support** | QR and local cache work without connection. |

---

## 🔮 Future Expansion
- NFC task check-ins (tap instead of scan).
- Smart home integration (robot vacuum, sensors).
- Voice assistant (“Mark kitchen as done”).
- Seasonal or thematic cleaning packs.
- Localized content for different cultures (EN / UA / DE / etc.).

---

## ❤️ Core Philosophy
CleanFlow isn’t just a cleaning app — it’s a **companion for focus and calm**.  
It adapts to your lifestyle, respects your pace, and supports everyone in the home — from kids learning responsibility to adults finding balance and flow.
