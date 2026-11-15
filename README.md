# 🚗 SoundDrop Driver (사운드드롭 드라이버)

> **현대자동차그룹 플레오스 커넥트(Pleos Connect) 앱 아이디어 공모전 출품작**  
> 주행 중 마주한 특별한 장소와 순간을 음악으로 기록하고 공유하는 **위치 기반 음악 타임캡슐 안드로이드 오토(AAOS) 애플리케이션**입니다.
---

## 📂 🔍 프로젝트 구조 및 핵심 파일 바로보기

저장소(`https://github.com/kimyejin305/CAR`) 내 주요 소스 코드와 모듈 구조입니다. 심사 시 아래 경로를 통해 네이버 지도 연동 및 핵심 구현 로직을 확인하실 수 있습니다.

```text
CAR/
├── 🚗 automotive/                 # 안드로이드 오토 전용 UI 및 메인 화면 모듈
│   └── src/main/java/.../
│       ├── MyCarAppScreen.kt      # [네이버 지도 연동] 메인 지도 렌더링 및 음악 핀 표출 화면
│       └── LocationSearchScreen.kt # 목적지 및 위치 검색 화면 (좌표 연동)
│
└── 📦 shared/                     # 공통 비즈니스 로직 및 데이터 모델 모듈
    └── src/main/java/.../
        ├── MusicPin.kt            # 음악 핀 데이터 구조 모델 (위도, 경도, 제목, 가수, userId)
        └── CollectedPinsScreen.kt # '내가 수집한 음악함' 관리 및 삭제 화면
