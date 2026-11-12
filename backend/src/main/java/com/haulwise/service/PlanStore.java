package com.haulwise.service;

import com.haulwise.dto.plan.PlanResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * In-memory store of computed plans (D-05): keyed by UUID, at most {@value #DEFAULT_CAPACITY} plans, the least
 * recently used one evicted first (a read counts as use). Plans are immutable snapshots and vanish on restart.
 * Thread-safe.
 */
@Component
public class PlanStore {

    static final int DEFAULT_CAPACITY = 50;

    private final Map<UUID, PlanResponse> plans;

    public PlanStore() {
        this(DEFAULT_CAPACITY);
    }

    PlanStore(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1, was " + capacity);
        }
        this.plans = new LinkedHashMap<>(capacity + 1, 1.0f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, PlanResponse> eldest) {
                return size() > capacity;
            }
        };
    }

    public synchronized void save(PlanResponse plan) {
        plans.put(plan.id(), plan);
    }

    /** The plan with this id; empty when unknown, evicted, or the id is not a UUID. */
    public synchronized Optional<PlanResponse> find(String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
        return Optional.ofNullable(plans.get(uuid));
    }

    synchronized int size() {
        return plans.size();
    }
}
