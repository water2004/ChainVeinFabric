package org.edtp.chainveinfabric.client.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientMineRequestQueueTest {
    @Test
    void replacesAnUnstartedRequestImmediately() {
        ClientMineRequestQueue<String> queue = new ClientMineRequestQueue<>();

        queue.submit(List.of("old-a", "old-b"));
        queue.submit(List.of("new-a", "new-b"));

        assertEquals(2, queue.pendingCount());
        assertEquals("new-a", queue.nextItem());
        assertEquals("new-a", queue.startNext());
    }

    @Test
    void finishesTheActiveItemThenUsesOnlyTheLatestReplacement() {
        ClientMineRequestQueue<String> queue = new ClientMineRequestQueue<>();

        queue.submit(List.of("old-active", "old-discarded"));
        assertEquals("old-active", queue.startNext());
        queue.submit(List.of("superseded"));
        queue.submit(List.of("latest-a", "latest-b"));

        assertEquals(3, queue.pendingCount());
        assertEquals("old-active", queue.activeItem());

        queue.completeActive();

        assertEquals(2, queue.pendingCount());
        assertEquals("latest-a", queue.startNext());
        queue.completeActive();
        assertEquals("latest-b", queue.startNext());
        queue.completeActive();
        assertFalse(queue.hasPendingWork());
    }

    @Test
    void reportsRequestProgressWithoutCountingDiscardedWork() {
        ClientMineRequestQueue<String> queue = new ClientMineRequestQueue<>();
        queue.submit(List.of("a", "b", "c"));

        assertEquals(new ClientMineRequestQueue.Snapshot(0, 3, false), queue.snapshot());
        queue.startNext();
        assertEquals(new ClientMineRequestQueue.Snapshot(1, 3, true), queue.snapshot());
        queue.completeActive();
        assertEquals(new ClientMineRequestQueue.Snapshot(1, 3, false), queue.snapshot());

        queue.submit(List.of("replacement"));
        assertEquals(new ClientMineRequestQueue.Snapshot(0, 1, false), queue.snapshot());
    }

    @Test
    void clearDropsTheActiveItemAndAllQueuedWork() {
        ClientMineRequestQueue<String> queue = new ClientMineRequestQueue<>();
        queue.submit(List.of("active", "queued"));
        queue.startNext();
        queue.submit(List.of("replacement"));

        queue.clear();

        assertFalse(queue.hasPendingWork());
        assertEquals(0, queue.pendingCount());
        assertNull(queue.activeItem());
        assertNull(queue.snapshot());
    }
}
