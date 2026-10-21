package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.PredictionStrategy;
import com.swu.atch.ai.domain.TimePredictionExperiment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimePredictionExperimentRepository
        extends JpaRepository<TimePredictionExperiment, Long> {

    // 동일한 예측 실행에 속한 전략별 결과 조회
    List<TimePredictionExperiment>
    findByPredictionRunIdAndUser_IdOrderByIdAsc(
            UUID predictionRunId,
            Long userId
    );

    // 특정 Todo의 예측 실험 이력 조회
    List<TimePredictionExperiment>
    findByTodo_IdAndUser_IdOrderByCreatedAtDescIdDesc(
            Long todoId,
            Long userId
    );

    // 사용자 소유권을 확인하는 실험 단건 조회
    Optional<TimePredictionExperiment> findByIdAndUser_Id(
            Long id,
            Long userId
    );

    // 완료 후 평가된 특정 전략의 실험 기록
    List<TimePredictionExperiment>
    findByUser_IdAndStrategyTypeAndEvaluatedAtIsNotNull(
            Long userId,
            PredictionStrategy strategyType
    );
}