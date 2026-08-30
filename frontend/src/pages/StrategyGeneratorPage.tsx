import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import {
  BookOpen,
  ChevronDown,
  History,
  Loader2,
  Send,
  Wand2,
} from 'lucide-react';
import { knowledgeBaseApi } from '../api/knowledgebase';
import type { KnowledgeBaseItem } from '../api/knowledgebase';
import { userProviderApi } from '../api/userProvider';
import type { UserProviderItem } from '../types/userProvider';
import { strategyApi } from '../api/generator';
import {
  FREQUENCY_OPTIONS,
  MARKET_OPTIONS,
} from '../types/generator';
import type { GenerateStrategyRequest, GenerateStrategyResponse } from '../types/generator';
import StrategyResultPanel from '../components/StrategyResultPanel';
import { ROUTES } from '../constants/routes';

export default function StrategyGeneratorPage() {
  // 表单状态
  const [strategyName, setStrategyName] = useState('');
  const [market, setMarket] = useState<'STOCK' | 'ETF' | 'CONVERTIBLE_BOND' | 'FUTURES' | 'MARGIN'>('STOCK');
  const [frequency, setFrequency] = useState<'DAILY' | 'MINUTE' | 'TICK'>('DAILY');
  const [buyConditions, setBuyConditions] = useState('');
  const [sellConditions, setSellConditions] = useState('');
  const [riskControls, setRiskControls] = useState('');
  const [selectedKbIds, setSelectedKbIds] = useState<Set<number>>(new Set());
  const [providerId, setProviderId] = useState('');

  // 数据状态
  const [knowledgeBases, setKnowledgeBases] = useState<KnowledgeBaseItem[]>([]);
  const [configuredProviders, setConfiguredProviders] = useState<UserProviderItem[]>([]);
  const [loadingLists, setLoadingLists] = useState(true);
  const [formError, setFormError] = useState('');

  // 生成状态
  const [generating, setGenerating] = useState(false);
  const [result, setResult] = useState<GenerateStrategyResponse | null>(null);

  // 加载知识库列表与已配置模型
  useEffect(() => {
    (async () => {
      try {
        const [kbList, providerList] = await Promise.all([
          knowledgeBaseApi.getAllKnowledgeBases(),
          userProviderApi.list(),
        ]);
        setKnowledgeBases(kbList);

        // 只展示可直接使用的模型（用户已配 Key 或全局内置已有 Key），默认选中用户默认模型
        const configured = providerList.filter(p => p.available);
        setConfiguredProviders(configured);
        const defaultProvider = configured.find(p => p.defaultChatProvider);
        if (defaultProvider) {
          setProviderId(defaultProvider.id);
        }
      } catch (err) {
        console.error('加载数据失败:', err);
      } finally {
        setLoadingLists(false);
      }
    })();
  }, []);

  const toggleKb = useCallback((kbId: number) => {
    setSelectedKbIds(prev => {
      const next = new Set(prev);
      if (next.has(kbId)) {
        next.delete(kbId);
      } else {
        next.add(kbId);
      }
      return next;
    });
  }, []);

  const handleGenerate = async () => {
    setFormError('');
    if (!strategyName.trim()) {
      setFormError('请填写策略名称');
      return;
    }
    if (!buyConditions.trim()) {
      setFormError('请填写买入条件');
      return;
    }

    const request: GenerateStrategyRequest = {
      strategyName: strategyName.trim(),
      market,
      frequency,
      buyConditions: buyConditions.trim(),
    };
    if (sellConditions.trim()) request.sellConditions = sellConditions.trim();
    if (riskControls.trim()) request.riskControls = riskControls.trim();
    if (selectedKbIds.size > 0) request.knowledgeBaseIds = Array.from(selectedKbIds);
    if (providerId) request.providerId = providerId;

    setGenerating(true);
    try {
      const data = await strategyApi.generate(request);
      setResult(data);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : '生成失败，请重试');
    } finally {
      setGenerating(false);
    }
  };

  const inputClass = 'w-full px-3.5 py-2.5 rounded-xl border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow';
  const labelClass = 'block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5';

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-4">
          <div className="p-3 rounded-xl bg-gradient-to-r from-primary-500 to-primary-600 shadow-lg shadow-primary-500/25">
            <Wand2 className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white">策略生成器</h1>
            <p className="text-slate-500 dark:text-slate-400 mt-0.5 text-sm">描述交易条件，AI 生成 PTrade 策略代码</p>
          </div>
        </div>
        <Link
          to={ROUTES.generatorHistory}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 text-sm font-medium hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
        >
          <History className="w-4 h-4" />
          生成历史
        </Link>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 items-start">
        {/* 左侧：条件表单 */}
        <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-6 shadow-sm">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="md:col-span-3">
              <label className={labelClass}>
                策略名称 <span className="text-red-500">*</span>
              </label>
              <input
                type="text"
                value={strategyName}
                onChange={(e) => setStrategyName(e.target.value)}
                placeholder="例如: 均线金叉买入策略"
                className={inputClass}
              />
            </div>

            <div>
              <label className={labelClass}>交易市场</label>
              <div className="relative">
                <select
                  value={market}
                  onChange={(e) => setMarket(e.target.value as typeof market)}
                  className={`${inputClass} appearance-none pr-9 cursor-pointer`}
                >
                  {MARKET_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              </div>
            </div>

            <div>
              <label className={labelClass}>运行频率</label>
              <div className="relative">
                <select
                  value={frequency}
                  onChange={(e) => setFrequency(e.target.value as typeof frequency)}
                  className={`${inputClass} appearance-none pr-9 cursor-pointer`}
                >
                  {FREQUENCY_OPTIONS.map(option => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              </div>
            </div>

            <div>
              <label className={labelClass}>生成模型</label>
              <div className="relative">
                <select
                  value={providerId}
                  onChange={(e) => setProviderId(e.target.value)}
                  disabled={configuredProviders.length === 0}
                  className={`${inputClass} appearance-none pr-9 cursor-pointer disabled:cursor-not-allowed disabled:opacity-60`}
                >
                  {configuredProviders.length === 0 ? (
                    <option value="">未配置模型（使用默认）</option>
                  ) : (
                    <>
                      <option value="">默认模型</option>
                      {configuredProviders.map(provider => (
                        <option key={provider.id} value={provider.id}>
                          {provider.id} · {provider.model}
                        </option>
                      ))}
                    </>
                  )}
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-400 pointer-events-none" />
              </div>
            </div>
          </div>

          <div className="mt-4">
            <label className={labelClass}>
              买入条件 <span className="text-red-500">*</span>
            </label>
            <textarea
              value={buyConditions}
              onChange={(e) => setBuyConditions(e.target.value)}
              rows={3}
              placeholder="例如: 5日均线上穿20日均线时买入，仓位 30%"
              className={`${inputClass} resize-y`}
            />
          </div>

          <div className="mt-4">
            <label className={labelClass}>卖出条件 <span className="text-slate-400 font-normal">(可选)</span></label>
            <textarea
              value={sellConditions}
              onChange={(e) => setSellConditions(e.target.value)}
              rows={3}
              placeholder="例如: 收盘价跌破10日均线时卖出，或盈利达 8% 止盈"
              className={`${inputClass} resize-y`}
            />
          </div>

          <div className="mt-4">
            <label className={labelClass}>风控要求 <span className="text-slate-400 font-normal">(可选)</span></label>
            <textarea
              value={riskControls}
              onChange={(e) => setRiskControls(e.target.value)}
              rows={2}
              placeholder="例如: 单票最大仓位 20%，单日最大回撤 3% 停止交易"
              className={`${inputClass} resize-y`}
            />
          </div>

          {/* 知识库选择 */}
          <div className="mt-4">
            <label className={labelClass}>
              参考知识库 <span className="text-slate-400 font-normal">(可选，多选)</span>
            </label>
            <div className="border border-slate-200 dark:border-slate-600 rounded-xl overflow-hidden">
              {loadingLists ? (
                <div className="flex items-center justify-center py-6">
                  <Loader2 className="w-5 h-5 text-primary-500 animate-spin" />
                </div>
              ) : knowledgeBases.length === 0 ? (
                <div className="text-center py-6 text-slate-400 dark:text-slate-500 text-sm">
                  暂无知识库，可前往上传后用于生成
                </div>
              ) : (
                <div className="max-h-44 overflow-y-auto">
                  {knowledgeBases.map(kb => (
                    <label
                      key={kb.id}
                      className="flex items-center gap-2.5 px-3.5 py-2 hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors cursor-pointer border-b border-slate-50 dark:border-slate-700 last:border-b-0"
                    >
                      <input
                        type="checkbox"
                        checked={selectedKbIds.has(kb.id)}
                        onChange={() => toggleKb(kb.id)}
                        className="w-4 h-4 text-primary-500 rounded focus:ring-primary-500"
                      />
                      <span className="text-sm text-slate-700 dark:text-slate-300 truncate flex-1">{kb.name}</span>
                      <span
                        className={`px-1.5 py-0.5 rounded text-[10px] font-medium flex-shrink-0 ${
                          kb.visibility === 'PUBLIC'
                            ? 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-300'
                            : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
                        }`}
                      >
                        {kb.visibility === 'PUBLIC' ? '公共' : '私有'}
                      </span>
                    </label>
                  ))}
                </div>
              )}
            </div>
            {selectedKbIds.size > 0 && (
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-1.5">
                已选择 {selectedKbIds.size} 个知识库
              </p>
            )}
          </div>

          {formError && (
            <div className="mt-4 px-4 py-3 rounded-xl bg-red-50 dark:bg-red-900/30 border border-red-200 dark:border-red-800 text-red-600 dark:text-red-400 text-sm">
              {formError}
            </div>
          )}

          <motion.button
            onClick={handleGenerate}
            disabled={generating}
            className="mt-5 w-full flex items-center justify-center gap-2 py-3 rounded-xl text-white font-semibold text-sm bg-gradient-to-r from-primary-500 to-primary-600 shadow-lg shadow-primary-500/25 hover:from-primary-600 hover:to-primary-700 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
            whileHover={{ scale: generating ? 1 : 1.01 }}
            whileTap={{ scale: generating ? 1 : 0.99 }}
          >
            {generating ? (
              <>
                <Loader2 className="w-4 h-4 animate-spin" />
                正在生成策略...
              </>
            ) : (
              <>
                <Send className="w-4 h-4" />
                生成策略
              </>
            )}
          </motion.button>

          <p className="mt-3 text-xs text-slate-400 dark:text-slate-500 flex items-center gap-1.5 justify-center">
            <BookOpen className="w-3.5 h-3.5" />
            策略代码仅使用知识库中出现的 PTrade API 生成
          </p>
        </div>

        {/* 右侧：生成结果 */}
        <div className="lg:sticky lg:top-0">
          <StrategyResultPanel
            result={result}
            loading={generating}
            emptyHint="填写左侧表单，点击「生成策略」后结果将显示在这里"
          />
        </div>
      </div>
    </div>
  );
}
