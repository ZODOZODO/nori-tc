package com.nori.tc.comm.gateway.kafka;

import java.nio.charset.StandardCharsets;

/**
 * Kafka 기본 파티셔너와 동일한 해시 규칙(murmur2 + toPositive)을 제공하는 유틸리티입니다.
 *
 * <p>설계 의도:</p>
 * <p>1) 게이트웨이 코어에서 샤드 계산 결과를 안정적으로 재현합니다.</p>
 * <p>2) Kafka SDK에 직접 의존하지 않고도 동일한 파티션 계산 결과를 보장합니다.</p>
 * <p>3) PASSIVE 바인딩 검증/라우팅 판단이 브로커 클라이언트 라이브러리 유무와 무관하게 동작하도록 합니다.</p>
 */
public final class KafkaPartitioner {

    /**
     * Kafka murmur2 구현에서 사용하는 상수(seed)입니다.
     */
    private static final int MURMUR2_SEED = 0x9747b28c;

    /**
     * Kafka murmur2 구현에서 사용하는 상수(mixing constant)입니다.
     */
    private static final int MURMUR2_M = 0x5bd1e995;

    /**
     * Kafka murmur2 구현에서 사용하는 비트 시프트 상수입니다.
     */
    private static final int MURMUR2_R = 24;

    /**
     * 유틸리티 클래스 인스턴스화를 방지합니다.
     */
    private KafkaPartitioner() {
    }

    /**
     * 입력 키를 Kafka 기본 파티셔너와 동일한 방식으로 파티션 번호에 매핑합니다.
     *
     * <p>계산 절차:</p>
     * <p>1) UTF-8 바이트 배열 변환</p>
     * <p>2) Kafka 호환 murmur2 해시 계산</p>
     * <p>3) 양수 정규화(toPositive)</p>
     * <p>4) 파티션 개수로 나머지 연산</p>
     *
     * @param key 파티셔닝 대상 키(예: eqpId)
     * @param partitionCount 전체 파티션 개수(1 이상)
     * @return 계산된 파티션 번호(0 이상)
     */
    public static int partitionForKey(final String key, final int partitionCount) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (partitionCount <= 0) {
            throw new IllegalArgumentException("partitionCount must be > 0");
        }

        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return toPositive(murmur2(bytes)) % partitionCount;
    }

    /**
     * Kafka 클라이언트의 {@code Utils.toPositive(int)}와 동일한 동작을 수행합니다.
     *
     * <p>최상위 부호 비트를 제거하여 음수 해시 값을 0 이상의 정수로 정규화합니다.</p>
     *
     * @param number 정규화 대상 정수
     * @return 양수 정규화 결과
     */
    static int toPositive(final int number) {
        return number & 0x7fffffff;
    }

    /**
     * Kafka 클라이언트의 murmur2 구현과 동일한 해시 값을 계산합니다.
     *
     * <p>주의:</p>
     * <p>- 이 구현은 Kafka 호환성을 위해 알고리즘 구조를 그대로 유지합니다.</p>
     * <p>- 일반적인 MurmurHash2 변형과 상수/마무리 단계가 다를 수 있으므로 임의 변경하면 안 됩니다.</p>
     *
     * @param data 해시 대상 바이트 배열
     * @return Kafka 호환 murmur2 해시 값
     */
    static int murmur2(final byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data is null");
        }

        int length = data.length;
        int hash = MURMUR2_SEED ^ length;
        int index = 0;

        /*
         * 4바이트 단위로 블록을 읽어 혼합(mix)합니다.
         * Kafka 호환성을 위해 little-endian 조합 순서를 그대로 유지합니다.
         */
        while (length >= 4) {
            int k = (data[index] & 0xff)
                    | ((data[index + 1] & 0xff) << 8)
                    | ((data[index + 2] & 0xff) << 16)
                    | ((data[index + 3] & 0xff) << 24);

            k *= MURMUR2_M;
            k ^= k >>> MURMUR2_R;
            k *= MURMUR2_M;

            hash *= MURMUR2_M;
            hash ^= k;

            index += 4;
            length -= 4;
        }

        /*
         * 남은 1~3바이트 꼬리(tail)를 해시에 반영합니다.
         * switch fall-through는 Kafka 원본 구현과 동일한 순서를 의도합니다.
         */
        switch (length) {
            case 3:
                hash ^= (data[index + 2] & 0xff) << 16;
            case 2:
                hash ^= (data[index + 1] & 0xff) << 8;
            case 1:
                hash ^= (data[index] & 0xff);
                hash *= MURMUR2_M;
                break;
            default:
                // 남은 바이트가 없으면 추가 처리하지 않습니다.
                break;
        }

        /*
         * 최종 avalanche 단계입니다.
         * 입력 비트 분포를 고르게 퍼뜨려 파티션 편향을 줄입니다.
         */
        hash ^= hash >>> 13;
        hash *= MURMUR2_M;
        hash ^= hash >>> 15;

        return hash;
    }
}
