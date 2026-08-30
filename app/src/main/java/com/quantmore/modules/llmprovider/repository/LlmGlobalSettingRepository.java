package com.quantmore.modules.llmprovider.repository;

import com.quantmore.modules.llmprovider.model.LlmGlobalSettingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmGlobalSettingRepository extends JpaRepository<LlmGlobalSettingEntity, Long> {
}
