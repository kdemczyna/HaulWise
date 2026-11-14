package com.haulwise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.dto.plan.PlanResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanStoreTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 14, 12, 0);

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

    @Test
    void emptyStoreListsNothing() {
        assertThat(new PlanStore().listNewestFirst()).isEmpty();
    }

    @Test
    void listsNewestCreatedFirstRegardlessOfSaveOrReadOrder() {
        PlanStore store = new PlanStore();
        PlanResponse oldest = plan(NOON);
        PlanResponse middle = plan(NOON.plusMinutes(5));
        PlanResponse newest = plan(NOON.plusMinutes(10));
        store.save(middle);
        store.save(newest);
        store.save(oldest);

        store.find(oldest.id().toString());
        store.find(middle.id().toString());

        assertThat(store.listNewestFirst()).containsExactly(newest, middle, oldest);
    }

    @Test
    void plansCreatedInTheSameSecondListNewestSavedFirst() {
        PlanStore store = new PlanStore();
        PlanResponse first = plan(NOON);
        PlanResponse second = plan(NOON);
        store.save(first);
        store.save(second);

        store.find(first.id().toString());

        assertThat(store.listNewestFirst()).containsExactly(second, first);
    }

    @Test
    void dispatchedPlanKeepsItsPlaceAndShowsItsDispatchTime() {
        PlanStore store = new PlanStore();
        PlanResponse first = plan(NOON);
        PlanResponse second = plan(NOON);
        store.save(first);
        store.save(second);

        store.markDispatched(first.id().toString(), NOON.plusHours(1));

        List<PlanResponse> listed = store.listNewestFirst();
        assertThat(listed).extracting(PlanResponse::id).containsExactly(second.id(), first.id());
        assertThat(listed.get(1).dispatchedAt()).isEqualTo(NOON.plusHours(1));
        assertThat(listed.get(0).dispatchedAt()).isNull();
    }

    @Test
    void listingDoesNotCountAsUse() {
        PlanStore store = new PlanStore(2);
        PlanResponse first = plan(NOON);
        PlanResponse second = plan(NOON.plusMinutes(1));
        store.save(first);
        store.save(second);

        store.listNewestFirst();
        store.save(plan(NOON.plusMinutes(2)));

        assertThat(store.find(first.id().toString())).isEmpty();
        assertThat(store.find(second.id().toString())).isPresent();
    }

    private static PlanResponse plan() {
        return plan(null);
    }

    private static PlanResponse plan(LocalDateTime createdAt) {
        return new PlanResponse(UUID.randomUUID(), createdAt, null, null, null, null, null, List.of(), List.of(),
                null);
    }
}
