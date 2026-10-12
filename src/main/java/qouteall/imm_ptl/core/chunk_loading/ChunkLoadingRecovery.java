package qouteall.imm_ptl.core.chunk_loading;

import net.minecraft.server.level.ChunkResult;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Retry failed vanilla promotion results without taking ownership of another chunk scheduler. */
public final class ChunkLoadingRecovery {
    private ChunkLoadingRecovery() {}

    /**
     * C2ME's rewritten holders deliberately clear the vanilla future fields and
     * supply their own futures through overridden getters. An absent future is
     * not a failed promotion and must not start vanilla preparation work.
     * Exceptional I/O remains visible rather than being converted into a retry.
     */
    public static <T> ChunkResult<T> peek(CompletableFuture<ChunkResult<T>> future) {
        return future == null ? null : future.getNow(null);
    }

    public static <T> boolean failed(CompletableFuture<ChunkResult<T>> future) {
        ChunkResult<T> result = peek(future);
        return result != null && !result.isSuccess();
    }

    public static <T> CompletableFuture<ChunkResult<T>> retry(
        CompletableFuture<ChunkResult<T>> future, Supplier<CompletableFuture<ChunkResult<T>>> prepare
    ) {
        return failed(future) ? prepare.get() : future;
    }

    /**
     * Observe an already-delivered chunk's newly created entity-ticking future.
     * Unlike pending chunk delivery, retries here never request packet resends.
     * A failed neighbor-range result is retried after the usual 20-tick cooldown,
     * even when the vanilla ticket level remains unchanged.
     */
    public static final class DeliveredEntityPromotion {
        private final Retry retry = new Retry();

        /**
         * @return true only after the entity future completed successfully;
         *         false while pending, waiting for its cooldown, or retrying.
         */
        public <T> boolean tick(
            CompletableFuture<ChunkResult<T>> entityFuture, long gameTime, Runnable retryFailedPromotion
        ) {
            ChunkResult<T> result = peek(entityFuture);
            if (result == null) return false;
            if (result.isSuccess()) return true;
            if (!retry.scheduled()) {
                retry.schedule(gameTime);
            }
            else if (retry.ready(gameTime)) {
                retryFailedPromotion.run();
                retry.started();
            }
            return false;
        }
    }

    public static final class Retry {
        private boolean scheduled;
        private long notBefore;

        public boolean schedule(long gameTime) {
            if (scheduled) return false;
            scheduled = true;
            notBefore = gameTime + 20;
            return true;
        }

        public boolean ready(long gameTime) { return !scheduled || gameTime >= notBefore; }
        public boolean scheduled() { return scheduled; }
        public void started() { scheduled = false; }
    }
}
