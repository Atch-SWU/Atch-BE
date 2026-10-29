package com.swu.atch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI atchOpenAPI() {
        return new OpenAPI()
                // Swagger UI에 표시할 API 기본 정보
                .info(new Info()
                        .title("Atch! API")
                        .version("v1")
                        .description("""
                                ADHD 사용자를 위한 AI 기반 스케줄러입니다. **투두 → 작업 세션 → 통계** 순서로 연동합니다.

                                ## 도메인별 API 읽는 법

                                인증·내 계정·투두·작업 세션/타이머·통계/기록·AI/개인화·반복 일정의 7개 그룹입니다.
                                같은 도메인의 API는 컨트롤러 파일이 달라도 한 그룹에 모았습니다.
                                AI 그룹은 각 API 제목의 **[프론트]·[선택 조회]·[점검용]**으로 호출 목적을 구분합니다.

                                | 구분 | 프론트에서 할 일 |
                                |---|---|
                                | **[프론트]** | 해당 화면·사용자 동작에 맞는 API를 호출합니다. 모든 API를 순서대로 호출할 필요는 없습니다. |
                                | **[선택 조회]** | 프로필·유사 작업을 별도 화면에 표시할 때만 호출합니다. 시간 예측의 필수 선행 단계가 아닙니다. |
                                | **[점검용]** | 프로필을 직접 재계산해 확인할 때만 호출합니다. 인증된 본인 대상이며 서버 전용 경로는 아닙니다. |
                                | **서버 내부 처리** | 아래 표의 처리는 서버가 수행합니다. 프론트가 직접 호출할 별도 HTTP API는 없습니다. |

                                | 서버 내부 처리 | 연결되는 프론트 API |
                                |---|---|
                                | 개인화 프로필 재계산·기본 보정시간(Baseline) 계산 | AI 시간 예측 |
                                | Voyage 임베딩 생성·갱신·유사 작업 검색 | AI 시간 예측 또는 선택적 유사 작업 검색 |
                                | Claude 시간 보정·세부 작업 제안 | AI 시간 예측·AI 세부 분량 나누기 |
                                | 반복 패턴 감지·추천 갱신 | 프론트가 반복 추천 목록을 조회할 때 처리 |
                                | 활성 Rule의 당일 반복 Todo 생성 | 서버 주기 처리 및 Todo 목록·캘린더 조회 준비 |
                                | 일별 기록 확정(Snapshot)·미완료 작업 이월 | 주기 처리 및 관련 조회 시 서버가 처리 |

                                프론트는 Claude·Voyage를 직접 호출하지 않으며 제공자 API 키도 보내지 않습니다.
                                **도메인 그룹과 호출 용도 표시는 API의 인증·접근 권한을 바꾸지 않습니다.**

                                ## 화면별 호출 흐름

                                | 사용자 동작 | 호출 흐름 |
                                |---|---|
                                | 일반 투두 만들기 | 투두 저장 완료 표시·목록 갱신 → 추천 API 호출 → 추천이 있으면 안내 |
                                | AI 시간 제안받기 | AI 초안 발급 → 직접 예상시간 입력 → 단건 시간 예측 → 선택 후 투두 생성·수정 |
                                | AI 세부 분량 나누기 | AI 초안 발급 → 세부 작업 제안 → 편집·개별 시간 입력 → 필요하면 묶음 시간 예측 → 최종 세부 투두 저장 |
                                | 타이머 화면 복원 | 현재 타이머 조회 → 응답 상태로 화면 복원 |
                                | 다른 작업으로 전환 | 전환 요청 → WAITING이면 readyAt까지 대기 → 전환 확정. STARTED이면 즉시 반영 |
                                | 반복 일정 등록 | 추천 목록 조회 → 사용자가 등록 선택 → Rule 등록 → 이후 해당 날짜에 서버가 Todo 생성 |
                                | 작업 완료 | 투두 완료 → 완료 결과·다음 작업 후보 조회 |

                                ## 투두 저장 후 반복 추천 연동

                                1. `POST /api/v1/todos` 성공 응답을 받으면 **먼저 저장 완료를 표시하고 목록을 갱신**합니다.
                                2. 저장 완료 화면 처리를 추천 응답 때문에 기다리게 하지 말고, `GET /api/v1/recurrence-recommendations`를 별도로 호출합니다.
                                3. `enabled=true`이고 `items`가 있으면 카드·팝업 등으로 반복 등록을 안내합니다. `enabled=false` 또는 빈 목록이면 안내를 표시하지 않습니다. 별도 추천 화면은 필수가 아닙니다.
                                4. 사용자가 등록을 선택하면 `items`의 `patternId`로 반복 규칙 등록 API를 호출합니다. 추천을 조회한 것만으로 규칙이 만들어지지는 않습니다.

                                - 추천 API는 **본인의 현재 추천 목록**을 반환합니다. 방금 저장한 투두에 대한 추천만 반환하는 API는 아닙니다.
                                - 추천 조회가 실패해도 이미 성공한 투두 저장은 유지합니다. 추천 오류를 별도로 처리하고, 이 오류 때문에 투두 생성 요청을 다시 보내지 않습니다.
                                - 투두 생성 응답에는 추천이 포함되지 않습니다. 서버가 생성 후 자동으로 추천을 계산·전송하는 비동기 알림도 없습니다.
                                - 미래 예정 일정은 현재 감지에서 제외됩니다. 투두를 저장할 때마다 반드시 추천이 생기는 것은 아닙니다.

                                ## 요청 필드 읽는 법

                                - **직접 입력**: 태스크명(`title`), 메모(`memo`), 일정, 예상 작업 소요시간 등 화면에서 입력받는 값입니다.
                                - **이전 응답에서 복사**: 투두·세션 ID, AI 초안 `draftId`, 제안 `predictionToken` 등입니다. 사용자가 입력할 항목이 아닙니다.
                                - **프론트가 생성**: 저장 중복 방지용 `creationRequestId`입니다. 같은 저장을 재전송할 때는 같은 UUID를 유지합니다.
                                - 필수 표시는 기본 필수값입니다. 시간·AI 선택 같은 **조건부 필수 규칙은 각 필드 설명**도 확인합니다.
                                - `Request body`의 Schema를 펼치면 한국어 의미·단위·입력 조건을 볼 수 있습니다. `Responses`는 서버가 반환하는 값입니다.

                                ## 공통 규칙

                                | 항목 | 연동 기준 |
                                |---|---|
                                | 인증 | 자물쇠가 있는 API는 액세스 토큰이 필요합니다. Authorize에는 토큰 값만 입력합니다. 실제 요청 헤더는 Authorization: Bearer {accessToken}입니다. |
                                | 본인 데이터 | userId는 입력하지 않습니다. 서버가 로그인 토큰에서 사용자를 확인합니다. |
                                | 날짜·시각 | 입력 날짜·시각은 서울(Asia/Seoul) 기준입니다. 응답의 Z로 끝나는 일시는 UTC이므로 화면에서 서울 시각으로 변환합니다. |
                                | 시간 단위 | 예상시간은 **분**, 실제 집중시간·오차는 **초**입니다. 필드명의 Minutes/Seconds를 확인합니다. |
                                | 하루종일 | allDay=true, 같은 시작·마감 날짜, startTime/dueTime=null을 보냅니다. |
                                | 페이지 | page=0이 첫 페이지이며 size 기본 20·최대 100입니다. |
                                | 진행 상태 | PENDING=진행 전, IN_PROGRESS=진행 중, PAUSED=일시정지, COMPLETED=완료입니다. |
                                | 오류 | HTTP 상태와 code를 함께 확인합니다. errors가 있으면 해당 입력 필드에 reason을 표시합니다. |
                                | null | 값 없음·미확정일 수 있습니다. 통계의 null을 임의로 0으로 바꾸지 않습니다. |

                                이메일 인증·회원가입·로그인·비밀번호 재설정은 로그인 없이 호출합니다.
                                토큰 갱신·로그아웃은 액세스 토큰 대신 요청 본문의 refreshToken을 사용합니다.
                                """))

                // 컨트롤러의 동일 도메인은 같은 태그를 사용하고, 그룹 설명은 여기서 관리한다.
                .addTagsItem(new Tag().name("01. [프론트] 인증")
                        .description("회원가입 이메일 인증·회원가입·로그인·토큰 갱신·로그아웃·비밀번호 재설정입니다. 이메일 인증·가입·로그인·재설정은 로그인 없이 호출하며, 갱신·로그아웃은 요청 본문의 refreshToken을 사용합니다."))
                .addTagsItem(new Tag().name("02. [프론트] 내 계정")
                        .description("로그인한 본인의 비밀번호·닉네임 변경과 회원탈퇴입니다."))
                .addTagsItem(new Tag().name("03. [프론트] 투두")
                        .description("투두 생성·목록·캘린더·편집·삭제·완료·완료 취소입니다. 저장 성공을 먼저 화면에 반영한 뒤 반복 추천 목록 API를 별도로 호출합니다."))
                .addTagsItem(new Tag().name("04. [프론트] 작업 세션 · 타이머")
                        .description("타이머 시작·일시정지·재개·작업 전환·완료 결과와 작업 전환 제한/대기 시간 설정입니다."))
                .addTagsItem(new Tag().name("05. [프론트] 통계 · 기록")
                        .description("완료율·집중시간·일별 기록·예상시간 비교를 조회합니다."))
                .addTagsItem(new Tag().name("06. AI · 개인화")
                        .description("[프론트] 초안 발급·시간 예측·세부 분량 나누기·최종 저장, [선택 조회] 프로필·유사 투두, [점검용] 프로필 재계산을 모았습니다. 선택 조회·점검용은 일반 AI 요청의 필수 선행 호출이 아닙니다. 기본 보정시간(Baseline)·Claude·Voyage 처리는 서버가 수행합니다."))
                .addTagsItem(new Tag().name("07. [프론트] 반복 일정")
                        .description("반복 추천 조회·선택, 규칙 등록·조회·수정·활성·삭제, 전체 추천 설정입니다. 패턴 감지는 추천 API 호출 시 처리합니다. 등록한 규칙의 당일 투두 생성은 서버 주기 처리이므로 별도 생성 API를 호출하지 않습니다. 추천 설정과 개별 규칙의 활성 여부는 독립입니다."))

                // Swagger UI에서 JWT 토큰을 입력할 수 있도록
                // HTTP Bearer 인증 방식을 정의
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}
