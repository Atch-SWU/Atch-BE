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

                                ## 어떤 API를 호출하나요?

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
                                | 일별 기록 확정(Snapshot)·미완료 작업 이월 | 주기 처리 및 관련 조회 시 서버가 처리 |

                                프론트는 Claude·Voyage를 직접 호출하지 않으며 제공자 API 키도 보내지 않습니다.
                                **태그는 연동 용도 안내입니다. API의 인증·접근 권한을 바꾸는 구분이 아닙니다.**

                                ## 화면별 호출 흐름

                                | 사용자 동작 | 호출 흐름 |
                                |---|---|
                                | 일반 투두 만들기 | 투두 생성 → 목록·상세 갱신 |
                                | AI 시간 제안받기 | AI 초안 발급 → 직접 예상시간 입력 → 단건 시간 예측 → 선택 후 투두 생성·수정 |
                                | AI 세부 분량 나누기 | AI 초안 발급 → 세부 작업 제안 → 편집·개별 시간 입력 → 필요하면 묶음 시간 예측 → 최종 세부 투두 저장 |
                                | 타이머 화면 복원 | 현재 타이머 조회 → 응답 상태로 화면 복원 |
                                | 다른 작업으로 전환 | 전환 요청 → WAITING이면 readyAt까지 대기 → 전환 확정. STARTED이면 즉시 반영 |
                                | 작업 완료 | 투두 완료 → 완료 결과·다음 작업 후보 조회 |

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

                // 선택 조회·점검 API를 필수 호출 단계나 서버 전용 경로로 오해하지 않도록 구분한다.
                .addTagsItem(new Tag().name("01. [프론트] 회원가입 이메일 인증")
                        .description("로그인 없이 호출합니다. 코드 발송 → 코드 확인 → 회원가입 순서입니다."))
                .addTagsItem(new Tag().name("02. [프론트] 회원가입 · 로그인")
                        .description("로그인 없이 호출하며 성공하면 인증 토큰을 반환합니다."))
                .addTagsItem(new Tag().name("03. [프론트] 인증 유지 · 로그아웃")
                        .description("리프레시 토큰을 요청 본문으로 전달합니다."))
                .addTagsItem(new Tag().name("04. [프론트] 비밀번호 재설정")
                        .description("로그인 없이 이메일 재인증으로 비밀번호를 재설정합니다."))
                .addTagsItem(new Tag().name("05. [프론트] 내 계정")
                        .description("로그인한 본인의 비밀번호·닉네임 변경과 회원탈퇴입니다."))
                .addTagsItem(new Tag().name("06. [프론트] 투두 관리")
                        .description("투두 생성·목록·캘린더·편집·삭제·완료·완료 취소입니다."))
                .addTagsItem(new Tag().name("07. [프론트] 작업 진행 · 타이머")
                        .description("작업 세션 시작·일시정지·재개·전환과 완료 결과입니다."))
                .addTagsItem(new Tag().name("08. [프론트] 작업 전환 제한 설정")
                        .description("다른 작업으로 전환할 때의 제한 여부와 대기 시간을 설정합니다."))
                .addTagsItem(new Tag().name("09. [프론트] 통계 · 기록")
                        .description("완료율·집중시간·일별 기록·예상시간 비교를 조회합니다."))
                .addTagsItem(new Tag().name("10. [프론트] AI 시간 예측")
                        .description("AI 초안 발급 → 사용자 예상시간 입력 → 시간 보정 제안 → 투두 저장 순서입니다."))
                .addTagsItem(new Tag().name("11. [프론트] AI 세부 분량 나누기")
                        .description("세부 작업 제안 → 편집·시간 입력 → 선택적 시간 예측 → 최종 승인 저장 순서입니다."))
                .addTagsItem(new Tag().name("12. [선택 조회] 시간 예측 프로필")
                        .description("개인화 정보를 화면에 표시할 때 조회합니다. 시간 예측 전에 호출할 필요는 없습니다."))
                .addTagsItem(new Tag().name("13. [선택 조회] 유사한 완료 투두")
                        .description("유사 작업 목록을 별도 화면에 표시할 때 호출합니다. 시간 예측 전에 호출할 필요는 없습니다."))
                .addTagsItem(new Tag().name("90. [점검용] 시간 예측 프로필 재계산")
                        .description("본인 프로필을 직접 재계산하는 선택 API입니다. 일반 예측은 서버가 내부에서 재계산합니다."))

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
