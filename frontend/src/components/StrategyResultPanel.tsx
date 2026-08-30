import { useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Check, Copy, Download, FileCode2, Loader2 } from 'lucide-react';
import CodeBlock from './CodeBlock';
import { FREQUENCY_LABELS, MARKET_LABELS } from '../types/generator';
import type { GenerateStrategyResponse } from '../types/generator';
import { formatDateTime } from '../utils/date';

interface StrategyResultPanelProps {
  /** 当前展示的策略结果 */
  result: GenerateStrategyResponse | null;
  /** 是否正在加载 */
  loading?: boolean;
  /** 无结果时的提示文案 */
  emptyHint?: string;
}

/** 清理 AI 返回的 Markdown 格式 */
function formatMarkdown(text: string): string {
  if (!text) return '';
  return text
    // 处理转义换行符
    .replace(/\\n/g, '\n')
    // 确保标题 # 后有空格
    .replace(/^(#{1,6})([^\s#\n])/gm, '$1 $2')
    // 确保有序列表数字后有空格（如 1.xxx -> 1. xxx）
    .replace(/^(\s*)(\d+)\.([^\s\n])/gm, '$1$2. $3')
    // 确保无序列表 - 或 * 后有空格
    .replace(/^(\s*[-*])([^\s\n-])/gm, '$1 $2')
    // 压缩多余空行
    .replace(/\n{3,}/g, '\n\n');
}

/**
 * 策略结果展示面板：说明 + 代码 + 复制/下载，生成器与历史页共用
 */
export default function StrategyResultPanel({ result, loading = false, emptyHint }: StrategyResultPanelProps) {
  const [copied, setCopied] = useState(false);

  const handleCopy = async () => {
    if (!result) return;
    try {
      await navigator.clipboard.writeText(result.code);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error('复制失败:', err);
    }
  };

  const handleDownload = () => {
    if (!result) return;
    const blob = new Blob([result.code], { type: 'text/x-python;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = result.fileName.endsWith('.py') ? result.fileName : `${result.fileName}.py`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.URL.revokeObjectURL(url);
  };

  return (
    <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm h-full flex flex-col min-h-[400px]">
      {loading ? (
        <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 dark:text-slate-500">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin mb-3" />
          <p className="text-sm">正在生成策略，请稍候...</p>
        </div>
      ) : !result ? (
        <div className="flex-1 flex flex-col items-center justify-center py-20 text-slate-400 dark:text-slate-500">
          <FileCode2 className="w-14 h-14 mb-3 opacity-50" />
          <p className="text-sm">{emptyHint ?? '填写左侧表单，点击生成策略'}</p>
        </div>
      ) : (
        <>
          {/* 结果头部 */}
          <div className="p-5 border-b border-slate-100 dark:border-slate-700">
            <div className="flex items-start justify-between gap-3 flex-wrap">
              <div className="min-w-0">
                <h2 className="text-lg font-bold text-slate-800 dark:text-white truncate">{result.strategyName}</h2>
                <div className="flex items-center gap-2 mt-2 flex-wrap">
                  <span className="px-2 py-0.5 rounded text-xs font-medium bg-primary-50 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300">
                    {MARKET_LABELS[result.market] ?? result.market}
                  </span>
                  <span className="px-2 py-0.5 rounded text-xs font-medium bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300">
                    {FREQUENCY_LABELS[result.frequency] ?? result.frequency}
                  </span>
                  {result.providerId && (
                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400 font-mono">
                      {result.providerId}
                    </span>
                  )}
                  <span className="text-xs text-slate-400 dark:text-slate-500">
                    {formatDateTime(result.createdAt)}
                  </span>
                </div>
              </div>
              <div className="flex gap-2 flex-shrink-0">
                <button
                  onClick={handleCopy}
                  className={`flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-medium transition-colors ${
                    copied
                      ? 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                  }`}
                  title="复制代码"
                >
                  {copied ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                  {copied ? '已复制' : '复制代码'}
                </button>
                <button
                  onClick={handleDownload}
                  className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg text-xs font-medium bg-primary-500 text-white hover:bg-primary-600 transition-colors"
                  title="下载 .py 文件"
                >
                  <Download className="w-3.5 h-3.5" />
                  下载 .py
                </button>
              </div>
            </div>
          </div>

          {/* 策略说明 */}
          <div className="p-5 border-b border-slate-100 dark:border-slate-700">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">策略说明</h3>
            <div className="prose prose-slate dark:prose-invert prose-sm max-w-none">
              <ReactMarkdown
                remarkPlugins={[remarkGfm]}
                components={{
                  // 自定义代码块渲染
                  code: ({ className, children }) => {
                    const match = /language-(\w+)/.exec(className || '');
                    const isInline = !match;

                    if (isInline) {
                      return (
                        <code className="bg-slate-100 dark:bg-slate-600 text-primary-600 dark:text-primary-400 px-1.5 py-0.5 rounded-md text-sm font-normal">
                          {children}
                        </code>
                      );
                    }

                    // 代码块使用 CodeBlock 组件
                    return (
                      <CodeBlock language={match[1]}>
                        {String(children).replace(/\n$/, '')}
                      </CodeBlock>
                    );
                  },
                  // 禁用默认 pre 渲染，由 CodeBlock 处理
                  pre: ({ children }) => <>{children}</>,
                }}
              >
                {formatMarkdown(result.explanation)}
              </ReactMarkdown>
            </div>
          </div>

          {/* 策略代码 */}
          <div className="p-5 flex-1 min-h-0">
            <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">策略代码</h3>
            <CodeBlock language="python">{result.code}</CodeBlock>
          </div>
        </>
      )}
    </div>
  );
}
