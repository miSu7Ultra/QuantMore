/**
 * 策略生成相关类型（对应后端 /api/strategy）
 */

/** 交易市场 */
export type StrategyMarket = 'STOCK' | 'ETF' | 'CONVERTIBLE_BOND' | 'FUTURES' | 'MARGIN';

/** 运行频率 */
export type StrategyFrequency = 'DAILY' | 'MINUTE' | 'TICK';

/** 策略生成请求 */
export interface GenerateStrategyRequest {
  strategyName: string;
  market: StrategyMarket;
  frequency: StrategyFrequency;
  buyConditions: string;
  sellConditions?: string;
  riskControls?: string;
  /** 可选：空 = 全部可见知识库 */
  knowledgeBaseIds?: number[];
  /** 可选：空 = 用户默认模型 */
  providerId?: string;
}

/** 策略生成响应 */
export interface GenerateStrategyResponse {
  id: number;
  strategyName: string;
  fileName: string;
  market: StrategyMarket;
  frequency: StrategyFrequency;
  code: string;
  explanation: string;
  providerId: string | null;
  createdAt: string;
}

/** 市场中文标签 */
export const MARKET_LABELS: Record<StrategyMarket, string> = {
  STOCK: '股票',
  ETF: 'ETF',
  CONVERTIBLE_BOND: '可转债',
  FUTURES: '期货',
  MARGIN: '融资融券',
};

/** 频率中文标签 */
export const FREQUENCY_LABELS: Record<StrategyFrequency, string> = {
  DAILY: '日线',
  MINUTE: '分钟',
  TICK: 'Tick',
};

/** 市场选项（用于表单下拉） */
export const MARKET_OPTIONS: { value: StrategyMarket; label: string }[] = [
  { value: 'STOCK', label: '股票' },
  { value: 'ETF', label: 'ETF' },
  { value: 'CONVERTIBLE_BOND', label: '可转债' },
  { value: 'FUTURES', label: '期货' },
  { value: 'MARGIN', label: '融资融券' },
];

/** 频率选项（用于表单下拉） */
export const FREQUENCY_OPTIONS: { value: StrategyFrequency; label: string }[] = [
  { value: 'DAILY', label: '日线' },
  { value: 'MINUTE', label: '分钟' },
  { value: 'TICK', label: 'Tick' },
];
