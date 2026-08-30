package com.quantmore.modules.llmprovider.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserProviderConfigEntity 测试")
class UserProviderConfigEntityTest {

  @Test
  @DisplayName("builder 未显式设置 enabled 时默认 true")
  void builderDefaultsEnabledToTrue() {
    UserProviderConfigEntity entity = UserProviderConfigEntity.builder().build();

    assertThat(entity.isEnabled()).isTrue();
  }
}
