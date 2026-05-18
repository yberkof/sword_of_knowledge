package com.sok.backend.realtime.actor;

import com.sok.backend.domain.game.ClaimingPhaseService;
import com.sok.backend.domain.game.QuestionEngineService;
import com.sok.backend.realtime.RoomSnapshotFactory;
import com.sok.backend.service.config.RuntimeGameConfigService;

/**
 * Shared services injected into room phase handlers.
 */
public record RoomDeps(
    QuestionEngineService questionEngine,
    RuntimeGameConfigService config,
    ClaimingPhaseService claimingService,
    RoomSnapshotFactory snapshotFactory
) {}
