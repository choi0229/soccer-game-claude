# 고교 축구 육성 시뮬레이션 (프로토타입)

고1 축구부 공격수(파워형)가 되어 3월 1주부터 다음 해 2월 4주까지 48주를 보내는 육성 시뮬레이션입니다.
목적은 하루 일과와 경기 판정이 1년 끝까지 돌아가는지, 수치가 말이 되는지 확인하는 것입니다.

## 실행

```sh
docker compose up --build
```

- 게임: http://localhost:5173
- API: http://localhost:8080/api/runs

수치를 바꾸려면 `config/*.yaml` 을 고친 뒤 `docker compose restart api` 를 실행합니다(설정 디렉터리는 컨테이너에 마운트되어 있습니다).

## 밸런스 시뮬레이터

```sh
docker compose run --rm simulator --runs 1000 --out /out/simulation-report.md
```

전략 3종(무작위, 훈련 위주, 균형)을 각각 1,000판씩 돌려 결과 표를 출력하고, 저장소 루트의 `simulation-report.md` 에도 저장합니다.
인자: `--runs N`, `--seed 시작시드`, `--out 파일`.

## 테스트

JDK 21과 Maven이 없어도 Docker로 돌릴 수 있습니다.

```sh
./backend/test.sh               # 백엔드 전체 테스트 (domain, simulator, api)
./backend/test.sh -pl domain test
cd frontend && npm install && npm run typecheck
node scripts/replay-check.mjs   # 실행 중인 서버로 1년을 두 번 진행해 결과가 같은지 확인
```

## 구조

```
config/                 수치와 표 (게임 규칙, 훈련 메뉴, 장면표, 학교, 이벤트 60편, 승부처 10건)
backend/domain          게임 규칙. Spring 에 의존하지 않는 순수 Java 모듈 + 단위 테스트
backend/simulator       화면 없이 1년을 대량으로 돌리는 명령
backend/api             Spring Boot API + PostgreSQL (판, 추가 전용 행동 기록)
frontend                React + Vite + TanStack Query(서버 값) + Zustand(화면 상태)
ASSUMPTIONS.md          명세에 없어서 정한 규칙
```

### 결정성과 재현

- 시드 하나에서 난수 흐름 두 개를 만듭니다: 게임 판정용(`RandomStreams.gameStream`)과 시뮬레이터 선택용(`choiceStream`). 게임 판정은 선택용 흐름을 쓰지 않습니다.
- 플레이어의 선택은 `run_actions` 테이블에 판마다 1부터 1씩 늘어나는 순번으로 추가만 됩니다. UPDATE, DELETE, TRUNCATE 는 DB 트리거가 막습니다.
- 서버는 상태를 저장하지 않고, 요청마다 생성 기록(시드, 고른 학교)과 행동 기록을 재생해 상태를 만든 뒤 새 행동을 적용합니다. 화면 상단의 '지문'은 상태 해시라서, 같은 시드와 같은 선택 순서면 같은 값이 나옵니다.

### API

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/schools?seed=42` | 그 시드의 학교 32개교(유형별)와 기본 학교. 시드를 빼면 무작위 시드를 정해 함께 돌려준다 |
| POST | `/api/runs` | 새 판. 본문 `{ "seed": 42, "schoolId": 7 }` (seed 생략 시 무작위, schoolId 생략 시 기본 학교) |
| GET | `/api/runs/{id}` | 현재 화면 상태 |
| POST | `/api/runs/{id}/actions` | 행동 1개. `DAY` / `SUNDAY` / `EVENT_CHOICE` / `CLUTCH_CHOICE` |
| GET | `/api/runs/{id}/actions` | 행동 기록 (순번, 종류, 요청 JSON) |

행동 예시:

```json
{ "type": "DAY", "dawn": "EXERCISE", "classAttitude": "QUESTION", "menus": { "AFTERNOON": "power" } }
{ "type": "SUNDAY", "activity": "MEET", "meetTarget": "coach" }
{ "type": "EVENT_CHOICE", "eventId": "coach_004", "choiceIndex": 1 }
{ "type": "CLUTCH_CHOICE", "momentId": "clutch_05", "choiceIndex": 0 }
```

경기 중 승부처가 나오면 서버는 그 직전까지만 계산하고 멈춥니다(`phase: MATCH`, `liveMatch` 에 지금까지의 중계와 선택지).
`CLUTCH_CHOICE` 를 보내면 나머지 경기와 그날의 남은 일과를 계산합니다.

`DAY` 에는 그날 바꾼 선택만 담습니다. 비어 있으면 전날 선택을 그대로 씁니다.
