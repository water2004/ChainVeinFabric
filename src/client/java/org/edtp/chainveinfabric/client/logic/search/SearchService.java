package org.edtp.chainveinfabric.client.logic.search;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;

/**
 * One prioritized worker for every client-side search. All requests are captured
 * and submitted on the client thread; only immutable request data reaches the worker.
 */
public final class SearchService implements Runnable {
    public enum Priority {
        ACTION(0),
        PREVIEW(1);

        private final int order;

        Priority(int order) {
            this.order = order;
        }
    }

    private final Minecraft client;
    private final PriorityBlockingQueue<Task> tasks = new PriorityBlockingQueue<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicInteger latestPreview = new AtomicInteger();
    private final Thread thread;

    public SearchService(Minecraft client) {
        this.client = client;
        this.thread = new Thread(this, "ChainVeinFabric-SearchWorker");
        this.thread.setDaemon(true);
    }

    public void start() {
        this.thread.start();
    }

    public int nextPreviewGeneration() {
        return this.latestPreview.incrementAndGet();
    }

    public <T> void submit(SearchRequest request, Priority priority,
                           Function<SearchResult, T> workerMapper,
                           Consumer<T> clientCallback) {
        submit(request, priority, -1, workerMapper, clientCallback);
    }

    public <T> void submitPreview(SearchRequest request, int generation,
                                  Function<SearchResult, T> workerMapper,
                                  Consumer<T> clientCallback) {
        this.latestPreview.accumulateAndGet(generation, Math::max);
        submit(request, Priority.PREVIEW, generation, workerMapper, clientCallback);
    }

    @SuppressWarnings("unchecked")
    private <T> void submit(SearchRequest request, Priority priority, int previewGeneration,
                            Function<SearchResult, T> workerMapper,
                            Consumer<T> clientCallback) {
        Consumer<Object> callback = value -> clientCallback.accept((T) value);
        this.tasks.add(new Task(priority, this.sequence.getAndIncrement(), previewGeneration,
                request, result -> workerMapper.apply(result), callback));
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Task task = this.tasks.take();
                if (task.isStalePreview(this.latestPreview.get())) continue;

                SearchResult result;
                try {
                    result = SearchEngine.search(task.request());
                } catch (RuntimeException ignored) {
                    result = SearchResult.empty(task.request());
                }
                if (task.isStalePreview(this.latestPreview.get())) continue;

                Object mapped = task.mapper().apply(result);
                this.client.execute(() -> {
                    if (this.client.level == task.request().level()
                            && !task.isStalePreview(this.latestPreview.get())) {
                        task.callback().accept(mapped);
                    }
                });
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
                // A failed task must not terminate searches for the rest of the session.
            }
        }
    }

    private record Task(Priority priority, long sequence, int previewGeneration,
                        SearchRequest request, Function<SearchResult, Object> mapper,
                        Consumer<Object> callback) implements Comparable<Task> {
        @Override
        public int compareTo(Task other) {
            int byPriority = Integer.compare(this.priority.order, other.priority.order);
            return byPriority != 0 ? byPriority : Long.compare(this.sequence, other.sequence);
        }

        private boolean isStalePreview(int latestGeneration) {
            return this.priority == Priority.PREVIEW && this.previewGeneration != latestGeneration;
        }
    }
}
