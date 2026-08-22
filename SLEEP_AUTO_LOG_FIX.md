# Sleep Auto-Logging Fix

## Problem
Sleep logs were not being created automatically daily if the user didn't manually add them. The automatic fallback mechanism existed but was **reactive** rather than **proactive**:

- Automatic sleep logs were only created when `refreshCurrentPressure()` was called
- This only happened when:
  - User opened the app (MainActivity onCreate/onResume)
  - User opened Settings or Sleep Logs screens
  - User manually added/deleted a sleep log

If the user didn't open the app daily, automatic sleep logs would never be created, even though the auto-fallback logic was in place.

## Root Cause
The `SleepPressureRepository.buildAutoFallbackSleepInterval()` method implements smart automatic sleep log creation based on:
- User's configured sleep/wake hours
- 12-hour delay after wake time
- No overlapping manual logs
- Enabled auto-fallback preference

However, this logic was **only triggered reactively** when the app was used, not proactively on a daily schedule.

## Solution
Created a **daily WorkManager job** (`SleepPressureRefreshWorker`) that:

1. **Runs daily at 7 PM (19:00)** - typically 12+ hours after most users' wake time
2. **Calls `refreshCurrentPressure()`** - which triggers the automatic fallback logic
3. **Retries up to 3 times on failure** - handles transient errors gracefully
4. **Scheduled on app start and after device boot** - ensures consistent execution

### Implementation Details

#### New Worker: `SleepPressureRefreshWorker.kt`
- Extends `CoroutineWorker` with Hilt dependency injection
- Calls `sleepPressureRepository.refreshCurrentPressure()`
- Returns `Result.success()` on completion
- Implements retry logic for transient failures

#### Scheduling Function: `scheduleSleepPressureRefreshWorker()`
- Uses `PeriodicWorkRequestBuilder` for daily execution
- Calculates initial delay to next 7 PM using `delayUntilHour(19)`
- Uses `CANCEL_AND_REENQUEUE` policy for consistent scheduling
- Tagged with `WORK_TAG` for easy identification

#### Integration Points
1. **NeuroFlowApplication.onCreate()** - schedules worker on app start
2. **BootReceiver.onReceive()** - reschedules worker after device reboot

## How It Works

### Automatic Fallback Logic (Existing)
The existing `buildAutoFallbackSleepInterval()` method in `SleepPressureRepository`:

1. Checks if auto-fallback is enabled (`autoFallbackSleepInsertionEnabled`)
2. Waits 12 hours after configured wake hour
3. Creates a sleep log for previous night if:
   - No manual logs exist for that period
   - Sleep window doesn't overlap existing logs
   - Duration is within valid bounds (1 min - 16 hours)

### New Daily Trigger
The new worker ensures this logic is **evaluated daily at 7 PM**, even if the user doesn't open the app.

**Example Timeline:**
- User's wake hour: 7 AM
- 7 PM (12 hours later): Worker runs
- Automatic fallback logic checks conditions
- If no manual log for last night (7 PM yesterday - 7 AM today) exists:
  - Creates automatic sleep log with source "AUTO_DEFAULT"
  - Updates sleep pressure accordingly

## Benefits

1. **Consistent sleep tracking** - logs created daily without user action
2. **Accurate sleep pressure** - pressure calculations reflect actual sleep patterns
3. **User control preserved** - manual logs still take precedence and block auto-creation
4. **Battery efficient** - uses WorkManager's optimized scheduling
5. **Resilient** - survives app kills and device reboots

## User Experience

### Before Fix
- User must manually log sleep daily OR open the app to trigger auto-creation
- Missed days result in gaps in sleep tracking
- Sleep pressure calculations become inaccurate over time

### After Fix
- Sleep logs created automatically at 7 PM daily
- User can still manually log sleep (which takes precedence)
- Sleep pressure stays accurate without user intervention
- Tracking continues even if user doesn't open the app for days

## Testing

The fix can be tested by:

1. **Set sleep/wake hours** in Settings (e.g., 11 PM - 7 AM)
2. **Enable auto-fallback** (default: enabled)
3. **Don't manually log sleep** for a day
4. **Wait until 7 PM the next day**
5. **Check Sleep Logs screen** - automatic log should appear

For immediate testing, you can:
- Trigger the worker manually via WorkManager
- Or open the app (which also calls `refreshCurrentPressure()`)

## Configuration

The worker runs at **7 PM (19:00)** by default. To change this:

Edit `SleepPressureRefreshWorker.kt`:
```kotlin
val targetHour = 19  // Change to desired hour (0-23)
```

Note: The hour should be late enough to ensure the 12-hour post-wake delay has passed for most users.

## Related Files

- `app/src/main/java/com/neuroflow/app/worker/SleepPressureRefreshWorker.kt` - New worker
- `app/src/main/java/com/neuroflow/app/NeuroFlowApplication.kt` - Scheduling on app start
- `app/src/main/java/com/neuroflow/app/receiver/BootReceiver.kt` - Scheduling on boot
- `app/src/main/java/com/neuroflow/app/domain/repository/SleepPressureRepository.kt` - Existing fallback logic
- `app/src/test/java/com/neuroflow/app/worker/SleepPressureRefreshWorkerTest.kt` - Test documentation

## Future Enhancements

Possible improvements:
1. **Dynamic scheduling** - adjust worker time based on user's wake hour + 12 hours
2. **Notification option** - notify user when automatic log is created
3. **ML-based timing** - learn optimal log creation time from user's app usage patterns
4. **Multi-day catch-up** - create logs for multiple missed days if needed
