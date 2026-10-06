package com.coldguard.incident.identity.application;

import java.time.Duration;

public record LockoutPolicy(int maxAttempts, Duration duration) {}
