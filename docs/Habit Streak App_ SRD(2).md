# Habit Streak App: Software Requirements Document

**Version:** 1.5 | **Author:** Noro | **Platform:** Android (Kotlin)

---

## 1. Overview

### 1.1 Purpose

A mobile app that helps me build good habits and break bad ones by tracking streaks. It sends a reminder for each habit, lets me confirm in one tap, and saves my streak so I can look back at it.

### 1.2 Scope

- Personal app first. If it ends up polished enough, I may publish it on the Play Store.
- Tracks two kinds of habits: **positive** (things I do, like reading or working out) and **negative** (things I avoid, counted in days without doing them).
- Phone-only. No accounts, no cloud, no backend.

### 1.3 Definitions

| Term | Meaning |
| --- | --- |
| Positive habit | Something I want to do on its active days (e.g., reading) |
| Negative habit | Something I want to avoid; the streak counts days without it |
| Streak | Number of consecutive days a habit was kept |
| Freeze | A saved "free pass" that covers one missed or slipped day **of one habit** so that habit's streak doesn't reset |
| Active days | The days of the week a habit applies to |
| Habit-day | One active day of one non-paused habit (e.g., "reading on Tuesday") |
| Completion rate | For one habit in one week: completed active days divided by that habit's active days that week |
| Week | Monday to Sunday, in the phone's local time |
| Overall daily streak | Consecutive days on which I completed at least one positive habit, separate from per-habit streaks |

---

## 2. Functional Requirements

### 2.1 Habit management

- **FR-1:** I can create a habit with a **name**, **type** (positive or negative), **reminder time**, **active days**, and a **freeze threshold** (default 80%, range 50% to 100%).
- **FR-2:** I can edit a habit.
- **FR-3:** I can pause a habit. A paused habit keeps its streak and doesn't earn or spend freezes.
- **FR-4:** I can delete a habit. Deleting **permanently wipes** the habit and all its history (no archive). The app asks for a confirmation first so a mis-tap doesn't cost me a streak. *(confirmation prompt suggested)*
- **FR-32:** Editing a habit's active days **takes effect from today**. Days before the edit are judged under the rules that were in force then; the edit never rewrites or re-judges pending past days.

### 2.2 Checking in

- **FR-5:** Completing a positive habit is a simple **yes/no**. No quantities, no minimum targets.
- **FR-6:** For a negative habit, the counter keeps running on its own. Each day the app asks "Did you slip?" and answering **No** moves the streak up.
- **FR-7:** The day boundary is **midnight**. Anything logged after midnight counts for the new day.
- **FR-31:** I can log a positive habit as done even on a day it isn't an active day (a **bonus** day). It counts toward streaks but is not counted in the weekly completion rate. Negative habits have no bonus days.

### 2.3 Streak rules

- **FR-8:** If I miss a positive habit, or slip on a negative one, the streak **resets to zero**, unless a freeze covers it.
- **FR-9:** Days that aren't active days for a habit don't break the streak and don't use a freeze.
- **FR-10:** A paused habit keeps its streak while paused.

### 2.4 Freezes

- **FR-11:** **Each habit earns its own freezes.** A habit earns **1 freeze** for each week in which its own **completion rate** meets or beats **that habit's own freeze threshold**. Weeks run Monday to Sunday. A completed day is a positive habit done, or a negative habit with no slip. Missed days, slips, and days covered by a freeze all count as not completed.
- **FR-12:** Each habit holds a **maximum of 3** freezes in its **own pool**. No more are earned once that habit is at the cap. A new habit starts with 0. *(starting value suggested)*
- **FR-13:** Freezes are used **automatically**. If I miss a day (or slip) on a habit that has a freeze, one of that habit's freezes is spent and its streak survives. If that habit has none, its streak resets. One habit's freezes are never spent on another habit.
- **FR-14:** Frozen days should look different from completed days on the calendar. *(suggested, easy to change)*

### 2.5 Notifications

- **FR-15:** Every habit has its **own notification**, at a time I set myself.
- **FR-16:** For positive habits, the notification has a **Done** button, so I can log it without opening the app.
- **FR-17:** For negative habits, the notification asks "Did you slip?" with answers I can tap directly (No keeps the streak going; Yes triggers the freeze/reset logic).
- **FR-18:** If I ignore a notification, the app **reminds me again later** in the day.
- **FR-19:** I can open the app afterward and **fix yesterday** if I forgot to log it. Older days can't be edited, so a day is locked once the following day is over.

### 2.6 Screens and views

- **FR-20:** Each habit has its **own streak calendar**: a month grid like GitHub's contribution graph, with completed days filled in.
- **FR-21:** Stats are kept simple: **current streak** and **longest streak** per habit.
- **FR-21a:** Per-habit streaks stay strict. A habit's streak is never kept alive by activity in a different habit.
- **FR-22:** Each habit shows its own **freezes available** on its calendar screen. There is no app-wide freeze counter.

### 2.7 Home-screen widget

- **FR-23:** A widget shows **all my habits** with their current streaks, compact enough to read at a glance.
- **FR-24:** The widget is **display only**; it can't mark habits as done. Tapping it **opens the app**.

### 2.8 Overall daily streak

- **FR-26:** The app tracks an **overall daily streak**, separate from the per-habit streaks. A day counts if I completed **at least one positive habit** that day.
- **FR-27:** The overall daily streak shows its own current and longest values, like the per-habit stats. It **never uses freezes**: a day with no positive habit done always resets it.

### 2.9 Freeze threshold and partial weeks

- **FR-28:** The **freeze threshold** is set **per habit**, in its create/edit form. It's a percentage from **50% to 100%**, default 80%. There is no app-wide threshold.
- **FR-29:** Changing the threshold applies from then on and doesn't recalculate weeks already judged. *(suggested)*
- **FR-30:** If a habit is **created, paused, or resumed in the middle of a week**, that week is **skipped for freeze earning**. The habit's streak is unaffected and carries on as normal.

### 2.10 Milestones

- **FR-25:** When a streak hits **50, 100, or 150 days**, the app shows a congratulation message ("You did it!", "Great job!", etc.).

---

## 3. Non-Functional Requirements

- **NFR-1 Platform:** Native Android, written in Kotlin.
- **NFR-2 Offline and local:** The app works fully offline. All data is stored on the phone (a local database such as Room works well).
- **NFR-3 Privacy:** No accounts, no servers, no data leaves the phone.
- **NFR-4 Reminder reliability:** Reminders must fire at the time I set, and must **survive a phone reboot** and the system killing the app in the background. If notifications silently stop, the app is useless.
- **NFR-5 Speed and simplicity:** Logging a habit should take **one tap**, from the notification, the app, or (later) the widget.
- **NFR-6 Correct streak math:** Streak, freeze, pause, and inactive-day logic must be consistent and predictable. This is the core of the app, so it should be unit tested.
- **NFR-7 Data safety:** Since data is phone-only, a lost or reset phone means lost streaks. That's accepted for v1, but the design shouldn't block adding export/backup later.
- **NFR-8 Growth-ready:** If I publish on the Play Store, the app should be polished enough for public users without a redesign.

---

## 4. Constraints and Assumptions

- Built solo, in Kotlin, for Android only.
- One day = midnight to midnight, and one week = Monday to Sunday, both in the phone's local time.
- Streaks and freezes are both calculated per habit. Only the overall daily streak looks across habits.

---

## 5. Decisions Log

**Settled in v1.0:** delete wipes history, only yesterday is editable, the widget is display only (tap opens the app), inactive days don't break streaks, and paused habits keep their streaks.

**Changed in v1.1:** the strict "every habit, every day" freeze rule was dropped because it becomes almost unreachable as habits are added. Freezes are earned from a weekly completion rate against an adjustable threshold. Per-habit streaks stay strict, and a separate overall daily streak covers the "at least one good habit a day" idea.

**Changed in v1.2:** freezes are now **per habit**. Since each habit's streak is independent, a shared pool let one habit drain the protection of another. Each habit earns, holds (max 3), and spends its own freezes, and the nav bar counter is gone. The overall daily streak has no freezes. Pausing is the way to stop an unused habit from spending freezes.

**Changed in v1.3:** weeks start on Monday, and the freeze threshold is set per habit (50% to 100%, default 80%) instead of one global setting, so no separate Settings screen is needed for it.

**Changed in v1.4:** partial weeks (a habit created, paused, or resumed mid-week) are skipped for freeze earning, but streaks continue (FR-30).

**Changed in v1.5:** bonus days (FR-31) — a positive habit can be logged as done on a non-scheduled day; and active-day edits take effect from today only (FR-32). Freezes require an *answered* confirmation on negative habits; silent days keep the streak but don't count toward the weekly rate (data model 4.7).

**Still to confirm:** nothing. All open decisions are settled, and the requirements stage is complete.

---

## 6. Parked Ideas (not in v1)

- Cloud sync or manual backup/export
- Early streak nudges before the 50-day milestone