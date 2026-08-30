package com.quantmore.modules.knowledgebase.model;

/**
 * 知识库可见性
 */
public enum KbVisibility {
  /** 公共知识库（仅管理员可上传，全员可见），owner 为 NULL */
  PUBLIC,
  /** 私有知识库（仅上传者可见） */
  PRIVATE
}
