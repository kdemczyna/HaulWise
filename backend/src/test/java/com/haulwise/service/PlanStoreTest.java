package com.haulwise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.dto.plan.PlanResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanStoreTest {

    @Test
    void findsSavedPlanById() {
        PlanStore store = new PlanStore();
        PlanResponse plan = plan();

        store.save(plan);

        assertThat(store.find(plan.id().toString())).containsSame(plan);
    }

    @Test
    void unknownOrMalformedIdIsEmpty() {
        PlanStore store = new PlanStore();

        assertThat(store.find(UUID.randomUUID().toString())).isEmpty();
        assertThat(store.find("not-a-uuid")).isEmpty();
    }

    @Test
    void evictsTheLeastRecentlyUsedPlanAboveCapacity() {
        PlanStore store = new PlanStore(2);
        PlanResponse first = plan();
        PlanResponse second = plan();
        PlanResponse third = plan();
        store.save(first);
        store.save(second);

        store.find(first.id().toString());
        store.save(third);

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.find(first.id().toString())).isPresent();
        assertThat(store.find(second.id().toString())).isEmpty();
        assertThat(store.find(third.id().toString())).isPresent();
    }

    @Test
    void defaultCapacityIsFifty() {
        PlanStore store = new PlanStore();
        PlanResponse oldest = plan();
        store.save(oldest);

        for (int i = 0; i < PlanStore.DEFAULT_CAPACITY; i++) {
            store.save(plan());
        }

        assertThat(store.size()).isEqualTo(50);
        assertThat(store.find(oldest.id().toString())).isEmpty();
    }

    @Test
    void capacityMustBePositive() {
        assertThatThrownBy(() -> new PlanStore(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static PlanResponse plan() {
        return new PlanResponse(UUID.randomUUID(), null, null, null, null, null, null, List.of(), List.of());
    }
}
