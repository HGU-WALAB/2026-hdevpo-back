# 2026 HDevPo Backend

## 협업 워크플로우

이 프로젝트는 GitHub Issues와 Jira가 자동으로 연동됩니다.
이슈를 생성하면 Jira 태스크 생성, 브랜치 생성, 담당자 지정이 자동으로 처리됩니다.

---

### 전체 흐름

```
GitHub 이슈 생성
       │
       ▼
 [자동] 제목 prefix 추가 ([feat], [bug], [task])
 [자동] GitHub Issue Type badge 설정
 [자동] 선택한 Label 적용
 [자동] Jira 태스크 생성 (Bug / Story / Task)
 [자동] main에서 브랜치 생성 (feat/KAN-123/issue-title)
 [자동] 이슈 작성자 Assignee 지정
 [자동] 이슈에 댓글 (브랜치명 + Jira 링크 + git 명령어)
       │
       ▼
  로컬에서 브랜치 체크아웃 후 작업
       │
       ▼
  PR 생성 → 리뷰 → main 병합
       │
       ▼
  이슈 자동 Close / Jira 수동 업데이트
```

---

## 시작하기

### 1. 이슈 생성

1. GitHub 상단 **Issues** 탭 → **New issue** 클릭
2. **Create Issue** 템플릿 선택
3. 아래 항목 작성

| 항목 | 설명 | 필수 |
|---|---|---|
| **Issue Type** | Bug / Feature / Task 중 선택 | ✅ |
| **Labels** | 해당하는 라벨 체크 (복수 선택 가능) | 선택 |
| **Issue Description** | 이슈 상세 설명 | ✅ |
| **Issue Checklist** | 작업 체크리스트 | 선택 |
| **References** | 참고 링크, 이미지 | 선택 |

> **⚠️ 이슈 제목은 영어로 작성하세요.**
> 브랜치명에 이슈 제목이 포함되며, 한글은 특수문자로 처리되어 제거됩니다.

4. **Submit new issue** 클릭

---

### 2. 자동화 확인

이슈 생성 후 약 30초~1분 내에 자동으로 처리됩니다.

**GitHub 이슈에서 확인할 것:**
- 제목이 `[feat] : Add user authentication` 형식으로 변경됨
- 사이드바에 Issue Type badge 표시
- 선택한 Labels 적용됨
- Assignee에 본인 지정됨
- 댓글에 아래 정보가 달림

```
🪾 생성된 브랜치: feat/KAN-123/add-user-authentication
🔗 연결된 Jira Task: https://stonezzz1004.atlassian.net/browse/KAN-123
⌨️ 이슈 작업 간단 명령어:
git checkout main && git pull && git checkout -b feat/KAN-123/add-user-authentication origin/feat/KAN-123/add-user-authentication
```

**Jira에서 확인할 것:**
- 프로젝트에 새 태스크 자동 생성됨
- 제목 형식: `[BE/feat] Add user authentication`

---

### 3. 로컬에서 작업

이슈 댓글의 git 명령어를 복사해서 그대로 실행하세요.

```bash
git checkout main && git pull && git checkout -b feat/KAN-123/add-user-authentication origin/feat/KAN-123/add-user-authentication
```

작업 후 커밋 & 푸시:

```bash
git add .
git commit -m "feat: add user authentication logic"
git push
```

> 커밋 메시지 컨벤션은 아래 **커밋 규칙** 섹션을 참고하세요.

---

### 4. PR 생성 및 병합

1. GitHub에서 **Compare & pull request** 클릭
2. PR 제목과 설명 작성
3. PR 본문에 반드시 아래 문구 포함

```
Closes #이슈번호
```

> `Closes #57` 을 PR 본문에 넣으면 PR이 main에 병합될 때 이슈가 자동으로 Close됩니다.

4. 리뷰어 지정 후 리뷰 요청
5. 승인 후 **Squash and merge** 또는 **Merge pull request**

---

### 5. Jira 업데이트

PR 병합 후 Jira 태스크 상태는 **자동으로** 업데이트 됩니다.

```
할 일 → 진행 중 → 완료
```

---

## 커밋 규칙

```
<type>(<scope>): <subject>
```

| Type | 설명 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `docs` | 문서 변경 |
| `style` | 포맷팅, 세미콜론 등 (로직 변경 없음) |
| `refactor` | 코드 리팩토링 |
| `test` | 테스트 코드 |
| `chore` | 빌드, 설정, 유지보수 |

**예시:**
```bash
feat(auth): add JWT login endpoint
fix(user): resolve null pointer in profile update
docs(readme): update workflow guide
```

---

## 주의사항

**이슈 생성 시**
- Issue Type은 반드시 선택해야 합니다. 선택하지 않으면 브랜치와 Jira 태스크가 생성되지 않습니다.
- 이슈 제목은 영어로 작성하세요. 한글 제목은 브랜치명에서 제거됩니다.
- 이슈 생성 직후 Actions 탭에서 자동화가 정상 동작하는지 확인하세요.

**브랜치 작업 시**
- 반드시 이슈에서 생성된 브랜치를 사용하세요. 직접 브랜치를 만들면 Jira와 연동되지 않습니다.
- main 브랜치에 직접 push하지 마세요.
- 하나의 이슈 = 하나의 브랜치 = 하나의 PR 원칙을 지켜주세요.

**PR 생성 시**
- PR 본문에 `Closes #이슈번호`를 반드시 넣어야 이슈가 자동으로 Close됩니다.
- main에 병합하기 전 최소 1명의 리뷰 승인이 필요합니다.
