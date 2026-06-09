# Feature Specification: Noise Shield MVP

**Feature Branch**: `001-noise-shield-mvp`

**Created**: 2026-06-09

**Status**: Draft

**Input**: User description: "Build Noise Shield, a mobile-first app for iOS and Android that reduces perceived unwanted environmental noise as much as technically possible on consumer smartphones. MVP centers on adaptive noise masking with flexible architecture for future real-time audio processing, ML-assisted optimization, and experimental active noise reduction."

## Clarifications

### Session 2026-06-09

- Q: Must users sign in before their first masking session? → A: Sign-in required before first masking session. *(Superseded 2026-06-09 — see Session 2026-06-09 revision below.)*

### Session 2026-06-09 (revision — optional auth & offline-first)

- Q: Must users sign in to use the app? → A: **No.** Sign-in is optional. Users can use core masking, on-device analysis, personalization, and favorites without an account.
- Q: Is server communication required? → A: **No.** Backend communication is optional and only needed for cross-device sync, remote configuration delivery, and opt-in anonymized feature upload when the user is signed in and online.
- Q: What must work offline? → A: All core phone capabilities: masking playback, volume/timer controls, on-device noise analysis, manual and automatic profile suggestions, favorites, theme/language preferences, local session feedback, and limited/manual mode — without network or sign-in.
- Q: Should masking playback continue when the app is backgrounded or the screen is locked? → A: Background playback like Spotify/YouTube Premium — masking continues when app is backgrounded or screen is locked.
- Q: When should users be asked to opt in to anonymized acoustic feature collection? → A: Separate opt-in prompt after onboarding, before any anonymized data is collected.
- Q: How should noise analysis refresh suggestions during an active session? → A: Continuous refresh with automatic application of new suggestions without user confirmation.
- Q: How should preference sync conflicts be resolved when the same setting is changed on multiple devices? → A: Last write wins using server receipt timestamp.

### Session 2026-06-09 (clarify)

- Q: Is first-launch onboarding mandatory before the first masking session? → A: Onboarding is skippable; users can start masking immediately and revisit onboarding content from Settings.
- Q: Should unsigned users see the anonymized data-collection consent prompt? → A: No. The consent prompt is shown only to signed-in users; unsigned users are never prompted because no server upload occurs without an account.
- Q: What analysis thresholds apply when the user is unsigned or offline? → A: Bundled in-app defaults apply; remote configuration overrides those defaults only when the user is signed in and online.
- Q: How should optional sign-in be surfaced to users? → A: Sign-in is available in Settings only; the app does not prompt for sign-in during normal use.
- Q: What happens to local favorites and preferences when a user signs in for the first time? → A: Local data is discarded; the account starts fresh from server state (empty for new accounts).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Core Masking Session (Priority: P1)

A user wants to reduce perceived environmental noise during work, travel, or sleep by playing a masking sound at a comfortable volume for a defined period, without needing sign-in or an active internet connection.

**Why this priority**: This is the primary product value. Masking must work offline with manual controls and must not be blocked by connectivity or authentication.

**Independent Test**: Can be fully tested by launching the app without signing in, skipping onboarding, selecting a masking sound, adjusting volume, setting a timer, and stopping the session — with network disabled from first launch.

**Acceptance Scenarios**:

1. **Given** the app is installed, **When** the user starts a noise reduction session and selects white noise at 50% volume, **Then** masking audio plays continuously until stopped or the timer expires — with or without sign-in.
2. **Given** the app is installed and the user has not signed in, **When** the user starts a noise reduction session, **Then** masking starts immediately without routing to sign-in or mandatory onboarding.
8. **Given** a first-time user on the onboarding flow, **When** they choose to skip onboarding, **Then** they reach the main session experience immediately and can reopen onboarding later from Settings.
3. **Given** an active masking session, **When** the user changes the sound from pink noise to ocean waves, **Then** playback switches to the new sound without requiring app restart.
4. **Given** the device has no network connectivity and the user is not signed in, **When** the user starts and completes a masking session, **Then** core masking functionality works without errors or degraded playback.
5. **Given** an active session, **When** the user sets a 30-minute timer, **Then** masking stops automatically when the timer ends and the user sees clear session-ended feedback.
6. **Given** an active masking session, **When** the user locks the screen or switches to another app, **Then** masking playback continues in the background with platform-appropriate media-style behavior comparable to Spotify or YouTube Premium.
7. **Given** an active background masking session, **When** the user returns to Noise Shield, **Then** session state including sound, volume, timer, and playback status is visible and controllable without restarting the session.

---

### User Story 2 - Onboarding, Permissions, and Privacy Communication (Priority: P2)

A first-time user needs to understand what Noise Shield does, why microphone access is requested, how privacy is protected, and what limited functionality remains if permission is denied.

**Why this priority**: Trust and clarity are essential for a microphone-based wellness app. Users must not believe the app provides medical-grade or perfect active noise cancellation.

**Independent Test**: Can be fully tested by completing first-launch onboarding, granting or denying microphone permission, and verifying that messaging and limited/manual mode behave as described.

**Acceptance Scenarios**:

1. **Given** a first-time user opens the app, **When** onboarding is shown, **Then** the app explains adaptive noise masking, microphone permission purpose, local-first processing, and explicitly states it does not guarantee perfect active noise cancellation or medical outcomes; the user may skip and start masking immediately.
2. **Given** onboarding is complete, **When** the app requests microphone permission, **Then** the user sees a clear explanation that raw microphone audio is not uploaded by default.
3. **Given** the user grants microphone permission, **When** they enter the main experience, **Then** noise analysis features become available alongside manual masking.
4. **Given** the user denies microphone permission, **When** they continue using the app, **Then** manual masking remains available in a clearly labeled limited mode without blocking core playback controls.
5. **Given** a signed-in user completes onboarding, **When** the separate data-collection consent prompt is shown, **Then** it is distinct from the microphone permission request and explains what anonymized data may be collected, why, and that raw audio is not uploaded.
6. **Given** a signed-in user declines data-collection consent, **When** they continue using the app, **Then** no anonymized acoustic features are transmitted and all core masking functionality remains available.
7. **Given** a user who has not signed in, **When** they use the app, **Then** no data-collection consent prompt is shown and no anonymized features are transmitted to the server.

---

### User Story 3 - Noise Analysis and Adaptive Suggestions (Priority: P3)

A user with microphone permission wants the app to estimate ambient noise and suggest an appropriate masking profile so they spend less time choosing sounds manually.

**Why this priority**: Adaptive suggestions differentiate Noise Shield from a simple sound player and create the foundation for future ML improvements.

**Independent Test**: Can be fully tested by exposing the device to different ambient noise conditions, starting a session, and verifying that estimated noise level and suggested profile update locally without network dependency.

**Acceptance Scenarios**:

1. **Given** microphone permission is granted, **When** the user starts a noise reduction session in a noisy environment, **Then** the app displays an estimated ambient noise level derived from on-device analysis.
2. **Given** ambient noise resembles steady mechanical airflow, **When** analysis completes, **Then** the app suggests a relevant broad profile such as fan or air conditioner.
3. **Given** ambient noise resembles traffic or cafe chatter, **When** analysis completes, **Then** the app suggests a matching broad profile from the supported classification set.
4. **Given** a noise reduction session is active, **When** on-device analysis detects a better-matching broad profile, **Then** the app automatically applies the updated masking suggestion without requiring user confirmation.
5. **Given** an automatically applied suggestion changes during a session, **When** playback transitions to the new profile, **Then** the transition is smooth enough to avoid jarring interruption and session continuity is preserved.
6. **Given** a suggested profile is active, **When** the user manually selects a different masking sound, **Then** manual selection takes precedence until analysis detects a substantially different ambient profile warranting a new automatic suggestion.

---

### User Story 4 - Optional Sign-In and Cross-Device Sync (Priority: P4)

A user who wants continuity across devices may optionally sign in with a familiar provider to sync favorites, theme choice, language preference, and other settings when online.

**Why this priority**: Authentication enhances multi-device continuity but is not required for core value. Users who never sign in retain full local functionality.

**Independent Test**: Can be fully tested by using the app unsigned for several sessions, then optionally signing in with a supported provider, saving favorites and preferences, and confirming synced preferences on a second device when online.

**Acceptance Scenarios**:

1. **Given** a user who has not signed in, **When** they use masking, analysis, favorites, and settings, **Then** all core features work locally without any sign-in prompts during normal use.
2. **Given** a user who wants to sign in, **When** they open Settings, **Then** they can initiate optional sign-in from there.
3. **Given** a user who chooses optional sign-in from Settings, **When** they complete Google, Apple, or Facebook authentication, **Then** they are signed into Noise Shield without creating a custom password account; any prior local favorites and preferences are discarded and replaced by server state (empty for a new account).
4. **Given** a signed-in user saves favorite masking profiles, **When** they later use the app on another device while online, **Then** favorites and non-sensitive preferences are restored after sign-in.
5. **Given** a signed-in user is offline, **When** they change preferences or favorites, **Then** changes are stored locally and synced automatically when connectivity returns.
6. **Given** the same preference is changed on two devices before sync completes, **When** both changes reach the server, **Then** the version with the later server receipt timestamp wins and becomes the synced state on subsequent syncs.
7. **Given** a signed-in or unsigned user, **When** they use core masking offline, **Then** the session does not depend on backend availability.

---

### User Story 5 - Personalization, Feedback, and Habit Building (Priority: P5)

A user wants the app to feel familiar in their language and visual preference, save frequently used sounds, and provide feedback that helps future automatic suggestions improve.

**Why this priority**: These features increase comfort, repeat usage, and create a feedback loop for adaptive quality without being required for the first successful session.

**Independent Test**: Can be fully tested by switching language and theme, saving favorites, completing a session, submitting helpfulness feedback, and confirming preferences persist locally and sync when signed in and online.

**Acceptance Scenarios**:

1. **Given** a user opens settings, **When** they switch app language to English or Portuguese, **Then** user-facing text updates accordingly without requiring reinstall.
2. **Given** a user opens appearance settings, **When** they choose light, dark, or system theme, **Then** the app applies the selected theme immediately and remembers the choice.
3. **Given** an active or recently ended session, **When** the user marks whether the selected masking profile helped, **Then** the feedback is stored for future suggestion improvement.
4. **Given** a user finds a preferred masking sound, **When** they save it as a favorite, **Then** it appears in a favorites list for quick reuse in later sessions.

---

### Edge Cases

- What happens when microphone permission is revoked mid-session? Analysis and auto-suggestion stop; manual masking continues with a clear notice that analysis is unavailable.
- How does the app behave in very quiet environments? Noise level displays a low-activity state and suggestions default to gentle profiles without falsely reporting high noise.
- What happens when ambient noise changes significantly during a session? Analysis refreshes continuously and automatically applies an updated suggestion with smooth playback transition when a substantially different broad profile is detected.
- What happens when the user manually overrides an auto-applied suggestion? Manual selection remains active until ambient conditions change enough to trigger a new automatic suggestion.
- How does the app handle audio interruptions such as phone calls or other apps taking audio focus? Masking pauses or ducks appropriately and resumes based on platform-expected media-player behavior, with session state preserved when possible.
- What happens when a session runs in the background and the timer expires? Playback stops and the user receives platform-appropriate session-ended feedback (e.g., notification or status on return) without requiring the app to remain in the foreground.
- What happens when the operating system suspends or terminates the app during background playback? The app restores session state on next launch when possible and clearly communicates if the session ended unexpectedly.
- What happens when the user is signed in but the backend is unreachable? Local preferences and masking continue; sync retries in the background without blocking sessions.
- What happens when offline preference changes conflict with changes made on another device? The server applies last-write-wins using server receipt timestamp; the winning value propagates on next successful sync without user intervention.
- What happens when a user denies optional data-collection consent? Core masking, manual selection, favorites, and local feedback storage continue; only opt-in anonymized collection remains disabled.
- What happens if a signed-in user dismisses the post-onboarding consent prompt without choosing? No anonymized data is collected until the user explicitly opts in via the prompt or Settings.
- What happens when an unsigned user uses the app? No data-collection consent prompt is shown; no anonymized features are uploaded; all local functionality remains available; no sign-in prompts appear outside Settings.
- Can the user change data-collection consent later? Yes; consent can be enabled or revoked in Settings at any time, with changes applied to subsequent sessions only.
- How does limited/manual mode present itself? The UI clearly labels reduced capability and never implies that noise analysis is active without microphone access.
- What happens on first launch without network? Onboarding and full core functionality are available; the user can skip onboarding, start masking, use on-device analysis, and adjust preferences without network or sign-in.
- What happens if a user skips onboarding? Core masking and settings remain available; microphone permission and data-consent prompts appear at their natural trigger points (session start or Settings), and onboarding content stays accessible from Settings.
- What happens when a user chooses optional sign-in but network is unavailable? Sign-in is deferred with a clear message; all local features continue without blocking sessions.
- What happens when a previously signed-in user's session token expires while offline? Masking sessions and local features remain available; sync and sign-in refresh resume when connectivity returns.
- What happens when remote configuration is unavailable? The app uses bundled default tuning parameters; signed-in online users receive server overrides when fetch succeeds.
- What happens to local favorites and preferences on first sign-in? Local data is discarded after a clear warning; the account adopts server state (empty for new accounts, or restored values for returning accounts).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a mobile experience for iOS and Android focused on reducing perceived unwanted environmental noise through adaptive masking.
- **FR-002**: System MUST support first-launch onboarding that explains adaptive masking, microphone permission, privacy-first local processing, and the limits of consumer-grade noise reduction; onboarding MUST be skippable so users can start masking immediately and revisit onboarding content from Settings.
- **FR-003**: System MUST avoid medical claims and MUST NOT promise perfect active noise cancellation in user-facing messaging.
- **FR-004**: System MUST support optional sign-in using Google, Apple, and Facebook identity providers, accessible from Settings only, without prompting during normal use.
- **FR-005**: System MUST NOT require custom password-based authentication for MVP access.
- **FR-024**: System MUST NOT require sign-in to start, run, or complete a noise reduction session; sign-in is optional and enables cross-device sync and server-backed features only.
- **FR-032**: System MUST provide full core masking, on-device analysis, favorites, theme/language preferences, and local session feedback without sign-in or network connectivity.
- **FR-033**: System MUST treat all server communication (sync, remote configuration, opt-in feature upload, feedback upload) as optional enhancements that never block core session functionality.
- **FR-034**: System MUST ship bundled default analysis and masking tuning parameters in the app; remote configuration MAY override those defaults only when the user is signed in and online.
- **FR-025**: System MUST continue masking playback when the app is backgrounded or the screen is locked, using platform-appropriate background audio behavior comparable to mainstream media apps such as Spotify and YouTube Premium.
- **FR-026**: System MUST expose session playback state and primary controls when the user returns from background, without requiring session restart.
- **FR-006**: System MUST request microphone permission with a clear explanation of why it is needed and that raw microphone audio is not uploaded by default.
- **FR-007**: System MUST continue to offer manual masking in a limited mode when microphone permission is denied or revoked.
- **FR-008**: System MUST estimate ambient noise level from the device microphone during permitted sessions using on-device analysis.
- **FR-009**: System MUST classify or suggest broad noise profiles locally when feasible, including at minimum fan, traffic, cafe, rain, air conditioner, general white noise, and comparable broad categories.
- **FR-029**: System MUST continuously refresh on-device noise analysis during active sessions and automatically apply updated masking suggestions without requiring user confirmation.
- **FR-030**: System MUST transition between automatically applied masking profiles smoothly enough to avoid jarring playback interruption.
- **FR-010**: System MUST provide masking sounds including white noise, pink noise, brown noise, ocean waves, rain, fan, air conditioner, and cafe ambience.
- **FR-011**: Users MUST be able to manually select masking sounds, accept or override automatic suggestions, control playback volume, set session timers, and save favorite masking profiles.
- **FR-012**: System MUST perform core noise analysis and masking without requiring network connectivity.
- **FR-013**: System MUST NOT require backend availability to start, run, or complete a basic noise reduction session.
- **FR-014**: System MAY provide an optional backend capable of authentication integration, user preference sync, remote configuration, model versioning, A/B testing, user feedback collection, consent-governed anonymized acoustic feature collection, future model training/evaluation support, and serving lightweight models to the app; the app MUST remain fully functional when the backend is unavailable or unused.
- **FR-015**: System MUST NOT upload raw microphone audio by default.
- **FR-016**: Any optional data collection MUST be opt-in, transparent, minimized, and anonymized where possible.
- **FR-027**: System MUST present a separate data-collection consent prompt to signed-in users after onboarding and before transmitting any anonymized acoustic features; this prompt MUST NOT be combined with the microphone permission request and MUST NOT be shown to unsigned users.
- **FR-028**: System MUST NOT transmit anonymized acoustic features unless the user is signed in and has explicitly opted in via the post-onboarding consent prompt or a later change in Settings.
- **FR-017**: System MUST support multiple user-facing languages, with English and Portuguese available in MVP and an architecture that allows adding more languages later.
- **FR-018**: System MUST support light theme, dark theme, system-default theme, and manual theme override.
- **FR-019**: Users MUST be able to rate during or after a session whether the selected masking profile helped reduce perceived noise annoyance.
- **FR-020**: System MUST store user preferences locally while unsigned; upon first sign-in, local favorites and preferences are discarded and replaced by server state.
- **FR-035**: System MUST warn the user before first sign-in that local favorites and preferences will be discarded in favor of server state.
- **FR-031**: System MUST resolve preference sync conflicts using last-write-wins based on server receipt timestamp, without requiring user intervention in MVP.
- **FR-021**: System MUST preserve an architecture flexible enough for future native low-latency audio modules, real-time processing enhancements, ML-assisted optimization, and experimental active noise reduction techniques.
- **FR-022**: System MUST NOT require always-on background microphone listening for MVP core functionality.
- **FR-023**: System MUST NOT depend on cloud processing for the core masking session.

### Key Entities

- **User Account** *(optional)*: Represents an authenticated person identified through an external provider when the user opts in to sign-in; linked to synced preferences, favorites, consent choices, and server-side feedback history. Unsigned users operate with local-only data.
- **Noise Reduction Session**: A time-bounded user activity that may include ambient analysis, selected or suggested masking profile, volume, timer state, and session outcome.
- **Masking Profile**: A user-facing sound choice combining a supported masking sound, optional classification label, volume preference, and favorite status.
- **Noise Estimate**: On-device interpretation of current ambient conditions, including level and broad profile classification used for suggestions.
- **User Preferences**: Local settings while unsigned; after first sign-in, preferences and favorites come from server state. Multi-device conflicts among signed-in sessions resolve by last-write-wins using server receipt timestamp.
- **Session Feedback**: A user's helpfulness rating tied to a session and masking profile, used to improve future suggestions.
- **Consent Record**: Explicit user choices governing optional anonymized acoustic feature collection for signed-in users; captured via post-onboarding opt-in prompt or Settings, independent of microphone permission. Unsigned users have no server-side consent record.
- **Remote Configuration**: Server-managed settings such as feature flags, experiment assignments, model versions, and suggestion tuning parameters delivered when signed in and online; bundled in-app defaults apply for unsigned or offline users.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of test participants can complete the full primary journey — open the app without signing in, grant microphone permission, start a session, receive a suggested masking profile, adjust volume, switch sounds, save a favorite, set a timer, and stop the session — in one uninterrupted attempt during moderated usability testing.
- **SC-002**: Core masking sessions remain fully usable with airplane mode enabled from first launch, with zero session-blocking errors attributable to network absence or lack of sign-in.
- **SC-012**: At least 95% of unsigned, offline test runs complete masking, analysis, favorites, and preference changes without any prompt to sign in or connect to a server.
- **SC-009**: In background playback tests, masking continues for at least 30 minutes with screen locked and app backgrounded in 95% of runs on supported iOS and Android devices, matching mainstream media-app expectations.
- **SC-003**: At least 95% of preference changes made offline are successfully synced within 5 minutes of connectivity restoration during signed-in usage tests.
- **SC-011**: In multi-device conflict tests, 100% of resolved preference values match the change with the later server receipt timestamp.
- **SC-004**: 100% of first-launch onboarding test participants can correctly state that raw microphone audio is not uploaded by default, that anonymized data collection is a separate opt-in choice, and that the app does not guarantee perfect active noise cancellation.
- **SC-005**: In controlled ambient test scenarios representing at least six broad noise categories, automatic suggestions match the intended broad profile at least 70% of the time without manual correction.
- **SC-010**: When ambient noise shifts between two distinct broad profiles during a single session, the app auto-applies an updated suggestion within 30 seconds in at least 85% of controlled test runs, without user confirmation.
- **SC-006**: At least 80% of session feedback submissions are associated with the correct masking profile and session context in end-to-end validation.
- **SC-007**: Language switching between English and Portuguese updates all primary user-facing flows without app reinstall in acceptance testing.
- **SC-008**: Future-capability readiness is demonstrated by documented extension points for native audio modules, model versioning, and remote configuration without requiring redesign of the core session model.

## Assumptions

- Target users are consumers seeking improved comfort from environmental noise on smartphones, not clinical hearing treatment.
- MVP sessions are user-initiated; masking playback may continue in the background during an active session, but continuous background microphone listening outside an active session is out of scope.
- Supported masking sounds ship as bundled or locally available assets suitable for offline playback.
- Broad noise classification is heuristic in MVP and may improve over time through feedback and optional ML enhancements.
- Analysis and masking tuning parameters ship as bundled in-app defaults; remote configuration overrides apply only for signed-in online users.
- Sign-in is optional; users who never authenticate retain full local functionality including masking, analysis, favorites, and preferences.
- Cross-device sync requires optional sign-in via a supported identity provider and is not required for MVP core value.
- First sign-in discards prior local favorites and preferences; users are warned before confirming sign-in.
- English is the default language on first launch when device language is unsupported.
- Backend services support optional authentication, sync, configuration, and future ML workflows; the app MUST NOT depend on backend availability for core sessions.
- Development artifacts including specifications, plans, tasks, commit messages, and code comments will be written in English per project standards.
- Implementation will use a cross-platform mobile approach for iOS and Android and a JavaScript/TypeScript-compatible backend, with detailed technology choices captured in the implementation plan rather than in user-facing requirements.

## Out of Scope (MVP)

- Guaranteed perfect active noise cancellation
- Medical, therapeutic, or hearing-loss treatment claims
- Default upload of raw microphone audio
- Always-on background microphone monitoring
- Cloud dependency for core masking playback
- Custom password registration and credential management
- Full clinical validation of noise reduction effectiveness
