package com.nori.tc.comm.gateway.comm;

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

    
    /**
     * 게이트웨이 코어 모듈 구성 요소를 초기화합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param workerThreads 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public PerEquipmentExecutor(final int workerThreads) {
        if (workerThreads <= 0) {
            throw new IllegalArgumentException("workerThreads must be > 0");
        }
        this.workerPool = Executors.newFixedThreadPool(workerThreads);
    }

    
    /**
     * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     * @param equipmentId 설비 식별 정보
     * @param task 게이트웨이 코어 모듈 처리에 사용하는 입력 값
     */
    public void execute(final String equipmentId, final Runnable task) {
        Objects.requireNonNull(equipmentId, "equipmentId is null");
        Objects.requireNonNull(task, "task is null");

        executors.computeIfAbsent(equipmentId, key -> new SerialExecutor(workerPool))
                .execute(task);
    }

    
    /**
     * 게이트웨이 코어 모듈 리소스를 정리하고 종료합니다.
     *
     * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
     */
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

        
        /**
         * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         * @param command 처리할 요청/명령 정보
         */
        @Override
        public void execute(final Runnable command) {
            tasks.add(command);
            schedule();
        }

        
        /**
         * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         */
        private void schedule() {
            // 연결 제어 단계: 상태 전이와 예외 케이스를 함께 관리합니다.
            if (!running.compareAndSet(false, true)) {
                return;
            }
            backend.execute(this::runTasks);
        }

        
        /**
         * 게이트웨이 코어 모듈 도메인 처리 로직을 수행합니다.
         *
         * <p>게이트웨이 공통 설정, 런타임 정책, 계측 규칙을 기준으로 처리합니다.</p>
         */
        private void runTasks() {
            // 처리 단계: 분기 조건에 따라 흐름을 제어하고 후속 작업을 호출합니다.
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
