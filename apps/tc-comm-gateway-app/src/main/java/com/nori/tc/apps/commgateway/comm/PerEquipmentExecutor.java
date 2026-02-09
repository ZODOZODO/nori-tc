package com.nori.tc.apps.commgateway.comm;

import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * eqp별 순차 실행을 보장하는 실행기
 *
 * 설계 포인트
 * - eqpId마다 전용 스레드를 만들지 않고, 공유 스레드 풀을 사용합니다.
 * - eqpId 단위로 "SerialExecutor"를 만들어 순서를 보장합니다.
 * - 결과적으로 "설비별 격리"와 "낮은 지연"을 동시에 달성합니다.
 */
public final class PerEquipmentExecutor {

    private final ExecutorService workerPool;
    private final Map<String, SerialExecutor> executors = new ConcurrentHashMap<>();

    public PerEquipmentExecutor(final int workerThreads) {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be > 0");
        }
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
    }

    public void execute(final String equipmentId, final Runnable task) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(task, "task is null");

        executors.computeIfAbsent(equipmentId, key -> new SerialExecutor(workerPool))
                .execute(task);
    }

    public void shutdown() {
        workerPool.shutdown();
    }

    /**
     * eqpId 단위 순차 실행을 보장하는 내부 실행기
     */
    private static final class SerialExecutor implements java.util.concurrent.Executor {
        private final ExecutorService backend;
        private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
        private final AtomicBoolean running = new AtomicBoolean(false);

        private SerialExecutor(final ExecutorService backend) {
            this.backend = backend;
        }

        @Override
        public void execute(final Runnable command) {
            tasks.add(command);
            schedule();
        }

        private void schedule() {
            if (!running.compareAndSet(false, true)) {
                return;
            }
            backend.execute(this::runTasks);
        }

        private void runTasks() {
            try {
                while (true) {
                    final Runnable task = tasks.poll();
                    if (task == null) {
                        return;
                    }
                    task.run();
                }
            } finally {
                running.set(false);
                if (!tasks.isEmpty()) {
                    schedule();
                }
            }
        }
    }
}
