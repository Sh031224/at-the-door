# 시설 데이터

차량 앱(`car-app/data`가 assets로 포함)과 휴대폰 안내 페이지(`web`이 `/photos`로 제공)가 함께 쓰는 시설 데이터입니다.

- `venues/<venueId>.json`: 시설 하나당 파일 하나. 형식은 기획서 ‘시설 데이터 형식’을 따릅니다.
- `venues/photos/<venueId>/...`: 입구 사진. JSON의 `photos`에는 `photos/` 아래 상대 경로를 적습니다.

## 규칙

- 모든 사실 항목(`stepFree`, `stairs`, `distanceM`, `wheelchair`)은 `{ "value", "evidence" }` 형태이고, `evidence`는 같은 파일의 `evidence[].id`를 가리킵니다.
- 값이나 근거가 없으면 `null`로 둡니다. 규칙 엔진은 이 항목을 ‘확인 필요’로 처리합니다. 추정값을 넣지 않습니다.
- `distanceM`에는 실제 보행 경로로 잰 거리만 넣습니다. 지도상 직선거리를 넣지 않습니다.
- 시연용 데이터는 `"demo": true`와 `"status": "demo"`로 표시해 운영 데이터와 섞이지 않게 합니다. 좌표와 연락처는 가상 값입니다.
- `hours`는 `"HH:mm-HH:mm"` 형식입니다. 없으면 운영 시간 제한이 없다는 뜻이 아니라 ‘정보 없음’입니다.

## 사진

`venues/photos/<venueId>/*.png`는 시연용 일러스트입니다(`data/tools/gen_illustrations.py`로 생성, 이미지 안에 ‘데모 일러스트 · 실제 사진 아님’ 표시). JSON에서는 `"photoKind": "illustration"`으로 표시합니다. 실제 시설을 조사하면 직접 찍은 사진으로 바꾸고 `photoKind`를 `photo`로 둡니다.

## 그 밖의 필드

- `candidates[].lotId`: 같은 주차장에서 다른 입구로 걷는 후보끼리 같은 값. 만차 제보와 걷는 중 대안 선택에 씁니다.
- `candidates[].floors`: 주차 메모 층 선택지.
- `walk.returnSteps`: 돌아가는 순서. 없으면 올 때 순서를 거꾸로 보여 줍니다.
- `walk.wheelchair`: 휠체어 통행(폭·경사·문턱) 확인 결과. 모르면 휠체어 조건에서 ‘확인 필요’입니다.
