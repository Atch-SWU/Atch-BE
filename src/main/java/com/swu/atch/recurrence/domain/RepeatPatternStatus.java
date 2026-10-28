package com.swu.atch.recurrence.domain;

// 반복 패턴 감지 및 추천 상태
public enum RepeatPatternStatus {
    DETECTING, // 패턴 감지 기록 중
    SUGGESTED, // 사용자에게 반복 일정 등록을 추천
    DISMISSED, // 다시 제안하지 않기로 선택
    REGISTERED // 사용자가 수락해 RecurrenceRule로 등록
}
