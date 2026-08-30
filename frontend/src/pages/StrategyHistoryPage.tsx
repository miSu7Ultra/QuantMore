import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { motion } from 'framer-motion';
import { FileCode2, History, Loader2, Wand2 } from 'lucide-react';
import { strategyApi } from '../api/generator';
import { FREQUENCY_LABELS, MARKET_LABELS } from '../types/generator';
import type { GenerateStrategyResponse } from '../types/generator';
import StrategyResultPanel from '../components/StrategyResultPanel';
import { ROUTES } from '../constants/routes';
import { formatDateTime } from '../utils/date';

export default function StrategyHistoryPage() {
  const [history, setHistory] = useState<GenerateStrategyResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  // 选中的记录与详情加载状态
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [result, setResult] = useState<GenerateStrategyResponse | null>(null);
  const [loadingDetail, setLoadingDetail] = useState(false);

  useEffect(() => {
    (async () => {
      try {
        const list = await strategyApi.history();
        setHistory(list);
        // 默认选中第一条
        if (list.length > 0) {
          setSelectedId(list[0].id);
        }
      } catch (err) {
        setLoadError(err instanceof Error ? err.message : '加载历史记录失败');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  // 点击历史记录时加载详情
  const handleSelect = async (id: number) => {
    if (id === selectedId && result) return;
    setSelectedId(id);
    setLoadingDetail(true);
    try {
      const detail = await strategyApi.getById(id);
      setResult(detail);
    } catch (err) {
      setResult(null);
      setLoadError(err instanceof Error ? err.message : '加载记录失败');
    } finally {
      setLoadingDetail(false);
    }
  };

  return (
    <div className="max-w-7xl mx-auto">
      {/* 页面标题 */}
      <div className="flex items-center justify-between mb-8">
        <div className="flex items-center gap-4">
          <div className="p-3 rounded-xl bg-gradient-to-r from-primary-500 to-primary-600 shadow-lg shadow-primary-500/25">
            <History className="w-6 h-6 text-white" />
          </div>
          <div>
            <h1 className="text-2xl font-bold text-slate-800 dark:text-white">生成历史</h1>
            <p className="text-slate-500 dark:text-slate-400 mt-0.5 text-sm">查看历史生成的策略代码与说明</p>
          </div>
        </div>
        <Link
          to={ROUTES.generator}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 text-sm font-medium hover:bg-slate-200 dark:hover:bg-slate-600 transition-colors"
        >
          <Wand2 className="w-4 h-4" />
          返回生成器
        </Link>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      ) : loadError && history.length === 0 ? (
        <div className="text-center py-20 text-red-500">{loadError}</div>
      ) : history.length === 0 ? (
        <div className="text-center py-24 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700">
          <FileCode2 className="w-16 h-16 text-slate-300 dark:text-slate-600 mx-auto mb-4" />
          <p className="text-slate-500 dark:text-slate-400">暂无生成记录</p>
          <Link to={ROUTES.generator} className="mt-4 inline-block text-primary-500 hover:text-primary-600 font-medium">
            立即生成第一个策略
          </Link>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 items-start">
          {/* 左侧：历史列表 */}
          <div className="lg:col-span-2 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm overflow-hidden">
            <div className="px-4 py-3 border-b border-slate-100 dark:border-slate-700">
              <span className="text-sm font-semibold text-slate-700 dark:text-slate-300">共 {history.length} 条记录</span>
            </div>
            <div className="max-h-[calc(100vh-16rem)] overflow-y-auto">
              {history.map(item => (
                <motion.button
                  key={item.id}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  onClick={() => handleSelect(item.id)}
                  className={`w-full text-left px-4 py-3 border-b border-slate-50 dark:border-slate-700 last:border-b-0 transition-colors ${
                    selectedId === item.id
                      ? 'bg-primary-50 dark:bg-primary-900/30'
                      : 'hover:bg-slate-50 dark:hover:bg-slate-700/50'
                  }`}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className={`text-sm font-medium truncate ${
                      selectedId === item.id
                        ? 'text-primary-700 dark:text-primary-300'
                        : 'text-slate-800 dark:text-white'
                    }`}>
                      {item.strategyName}
                    </span>
                    <span className="text-xs text-slate-400 dark:text-slate-500 flex-shrink-0">
                      {MARKET_LABELS[item.market] ?? item.market} · {FREQUENCY_LABELS[item.frequency] ?? item.frequency}
                    </span>
                  </div>
                  <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">{formatDateTime(item.createdAt)}</p>
                </motion.button>
              ))}
            </div>
          </div>

          {/* 右侧：详情 */}
          <div className="lg:col-span-3">
            <StrategyResultPanel
              result={result}
              loading={loadingDetail}
              emptyHint="从左侧选择一条记录查看详情"
            />
          </div>
        </div>
      )}
    </div>
  );
}
