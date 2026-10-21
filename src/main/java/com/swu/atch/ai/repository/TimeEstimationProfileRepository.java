package com.swu.atch.ai.repository;

import com.swu.atch.ai.domain.TimeEstimationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TimeEstimationProfileRepository
        extends JpaRepository<TimeEstimationProfile, Long> {

    Optional<TimeEstimationProfile> findByUser_Id(Long userId);
}