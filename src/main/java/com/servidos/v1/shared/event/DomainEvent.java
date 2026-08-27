package com.servidos.v1.shared.event;

import java.time.Instant;

public interface DomainEvent {
    Instant ocurredAt();
}
