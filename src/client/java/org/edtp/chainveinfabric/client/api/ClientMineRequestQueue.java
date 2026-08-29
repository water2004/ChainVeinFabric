package org.edtp.chainveinfabric.client.api;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * Keeps one active client-side mining request and at most one replacement.
 *
 * <p>A replacement never interrupts the block currently being mined. Once
 * that block finishes, the unstarted remainder of the old request is dropped
 * and only the latest replacement is retained.</p>
 */
final class ClientMineRequestQueue<T> {
    private Request<T> activeRequest;
    private Request<T> pendingReplacement;
    private T activeItem;

    int submit(Collection<T> items) {
        Request<T> request = Request.copyOf(items);
        if (request == null) return 0;

        if (this.activeItem != null) {
            this.pendingReplacement = request;
        } else {
            this.activeRequest = request;
            this.pendingReplacement = null;
        }
        return request.total;
    }

    T startNext() {
        if (this.activeItem != null || this.activeRequest == null) {
            return this.activeItem;
        }

        this.activeItem = this.activeRequest.remaining.pollFirst();
        if (this.activeItem == null) {
            this.activeRequest = null;
        }
        return this.activeItem;
    }

    void completeActive() {
        if (this.activeItem == null || this.activeRequest == null) return;

        this.activeRequest.completed++;
        this.activeItem = null;

        if (this.pendingReplacement != null) {
            this.activeRequest = this.pendingReplacement;
            this.pendingReplacement = null;
        } else if (this.activeRequest.remaining.isEmpty()) {
            this.activeRequest = null;
        }
    }

    void clear() {
        this.activeRequest = null;
        this.pendingReplacement = null;
        this.activeItem = null;
    }

    boolean hasActiveItem() {
        return this.activeItem != null;
    }

    T activeItem() {
        return this.activeItem;
    }

    boolean hasPendingWork() {
        return this.activeItem != null
                || this.activeRequest != null
                || this.pendingReplacement != null;
    }

    int pendingCount() {
        if (this.activeItem != null) {
            Request<T> future = this.pendingReplacement != null
                    ? this.pendingReplacement
                    : this.activeRequest;
            return 1 + (future != null ? future.remaining.size() : 0);
        }
        return this.activeRequest != null ? this.activeRequest.remaining.size() : 0;
    }

    Snapshot snapshot() {
        if (this.activeRequest == null) return null;

        int current = this.activeRequest.completed + (this.activeItem != null ? 1 : 0);
        return new Snapshot(current, this.activeRequest.total, this.activeItem != null);
    }

    record Snapshot(int current, int total, boolean activelyMining) {
    }

    private static final class Request<T> {
        private final ArrayDeque<T> remaining;
        private final int total;
        private int completed;

        private Request(ArrayDeque<T> remaining) {
            this.remaining = remaining;
            this.total = remaining.size();
        }

        private static <T> Request<T> copyOf(Collection<T> items) {
            if (items == null || items.isEmpty()) return null;

            ArrayDeque<T> copy = new ArrayDeque<>(items.size());
            for (T item : items) {
                if (item != null) copy.addLast(item);
            }
            return copy.isEmpty() ? null : new Request<>(copy);
        }
    }
}
