# SocketType Template

이 디렉터리는 신규 `socketType`을 빠르게 추가하기 위한 템플릿입니다.

현재 템플릿은 **1개 파일 구조**를 기준으로 합니다.

- `TemplateSocketTypeHandler.java`
  - 프레임 추출(`tryExtractOne`)
  - 디코드(`decode`)
  - 인코드(`encode`)

## 적용 절차

1. `types/template` 디렉터리를 신규 타입명 디렉터리로 복사합니다.
2. `TemplateSocketTypeHandler` 클래스명과 `SOCKET_TYPE` 상수를 실제 타입값으로 변경합니다.
3. `tryExtractOne`/`decode`/`encode` 로직을 장비 프로토콜 규격에 맞게 수정합니다.
4. `GatewayCommConfiguration`의 `SocketTypeRegistry` 등록 코드에 신규 핸들러를 추가합니다.

## 체크리스트

- `SOCKET_TYPE` 값이 `tc_eqp_socket.socket_protocol_type`와 정확히 일치하는지 확인
- `maxFrameBytes` 방어 로직이 누락되지 않았는지 확인
- decode/encode 실패 메시지가 운영 로그에서 원인 추적 가능한지 확인
- 문자셋 규칙(UTF-8/ASCII/장비 지정 문자셋)을 명확히 했는지 확인