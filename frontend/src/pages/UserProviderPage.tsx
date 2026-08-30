import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  Check,
  CheckCircle,
  Cpu,
  Eye,
  EyeOff,
  Globe,
  KeyRound,
  Loader2,
  Plus,
  Plug,
  RefreshCw,
  Server,
  Trash2,
  X,
  XCircle,
} from 'lucide-react';
import { userProviderApi } from '../api/userProvider';
import type { UserProviderItem, UserProviderUpsertRequest } from '../types/userProvider';
import type { ProviderTestResult } from '../types/llmProvider';
import ConfirmDialog from '../components/ConfirmDialog';

/** 可设为用户默认模型的 Provider：已配置自己的 Key，或内置且全局可用 */
function isDefaultSelectable(provider: UserProviderItem): boolean {
  return provider.available;
}

/** Provider 行内配置表单 */
function ProviderConfigForm({
  provider,
  onSave,
  onCancel,
}: {
  provider: UserProviderItem;
  onSave: (data: UserProviderUpsertRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const [apiKey, setApiKey] = useState('');
  // 内置 Provider 预填全局默认值,用户可直接确认或覆盖
  const [baseUrl, setBaseUrl] = useState(provider.baseUrl ?? '');
  const [model, setModel] = useState(provider.model ?? '');
  const [temperature, setTemperature] = useState('');
  const [showApiKey, setShowApiKey] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!apiKey.trim()) {
      setError('API Key 不能为空');
      return;
    }
    if (provider.custom && (!baseUrl.trim() || !model.trim())) {
      setError('自定义 Provider 必须填写 baseUrl 与 model');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const data: UserProviderUpsertRequest = { apiKey: apiKey.trim() };
      if (baseUrl.trim()) data.baseUrl = baseUrl.trim();
      if (model.trim()) data.model = model.trim();
      if (temperature.trim()) {
        const temp = parseFloat(temperature.trim());
        if (!isNaN(temp)) data.temperature = temp;
      }
      await onSave(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : '保存失败，请重试');
    } finally {
      setSaving(false);
    }
  };

  const inputClass = 'w-full px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow';

  return (
    <form onSubmit={handleSubmit} className="mt-3 p-4 bg-slate-50 dark:bg-slate-700/40 rounded-xl border border-slate-200 dark:border-slate-600 space-y-3">
      <div>
        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
          API Key <span className="text-red-500">*</span>
        </label>
        <div className="relative">
          <input
            type={showApiKey ? 'text' : 'password'}
            value={apiKey}
            onChange={(e) => setApiKey(e.target.value)}
            placeholder="输入您的 API Key"
            className={`${inputClass} pr-9`}
            autoFocus
          />
          <button
            type="button"
            onClick={() => setShowApiKey(!showApiKey)}
            className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
          >
            {showApiKey ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
          </button>
        </div>
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
          Base URL {provider.custom ? <span className="text-red-500">*</span> : <span className="text-slate-400 font-normal">(留空使用全局配置)</span>}
        </label>
        <input
          type="text"
          value={baseUrl}
          onChange={(e) => setBaseUrl(e.target.value)}
          placeholder={provider.custom ? '例如: https://api.openai.com/v1' : '留空使用全局配置'}
          className={inputClass}
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
          模型 {provider.custom ? <span className="text-red-500">*</span> : <span className="text-slate-400 font-normal">(留空使用全局配置)</span>}
        </label>
        <input
          type="text"
          value={model}
          onChange={(e) => setModel(e.target.value)}
          placeholder={provider.custom ? '例如: gpt-4o' : '留空使用全局配置'}
          className={inputClass}
        />
      </div>

      <div>
        <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
          Temperature <span className="text-slate-400 font-normal">(可选)</span>
        </label>
        <input
          type="text"
          value={temperature}
          onChange={(e) => setTemperature(e.target.value)}
          placeholder="例如: 0.2, 0.7, 1"
          className={inputClass}
        />
      </div>

      {error && (
        <p className="text-xs text-red-600 dark:text-red-400">{error}</p>
      )}

      <div className="flex gap-2 pt-1">
        <button
          type="submit"
          disabled={saving}
          className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-primary-500 text-white text-xs font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Check className="w-3.5 h-3.5" />}
          保存
        </button>
        <button
          type="button"
          onClick={onCancel}
          disabled={saving}
          className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg bg-slate-100 dark:bg-slate-600 text-slate-600 dark:text-slate-300 text-xs font-medium hover:bg-slate-200 dark:hover:bg-slate-500 transition-colors disabled:opacity-50"
        >
          <X className="w-3.5 h-3.5" />
          取消
        </button>
      </div>
    </form>
  );
}

export default function UserProviderPage() {
  const [providers, setProviders] = useState<UserProviderItem[]>([]);
  const [defaultProviderId, setDefaultProviderId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');

  // 行内配置表单状态
  const [editingId, setEditingId] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [testResults, setTestResults] = useState<Record<string, ProviderTestResult>>({});
  const [deleteConfirmId, setDeleteConfirmId] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 自定义 Provider 新增表单
  const [showCustomForm, setShowCustomForm] = useState(false);
  const [customForm, setCustomForm] = useState({ id: '', baseUrl: '', model: '', apiKey: '' });
  const [creatingCustom, setCreatingCustom] = useState(false);
  const [customError, setCustomError] = useState('');

  // 默认模型切换状态
  const [settingDefault, setSettingDefault] = useState(false);

  const loadData = useCallback(async () => {
    try {
      const [list, defaults] = await Promise.all([
        userProviderApi.list(),
        userProviderApi.getDefault(),
      ]);
      setProviders(list);
      setDefaultProviderId(defaults.defaultProvider);
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : '加载失败，请重试');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const builtinProviders = providers.filter(p => !p.custom);
  const customProviders = providers.filter(p => p.custom);

  const handleSave = async (providerId: string, data: UserProviderUpsertRequest) => {
    await userProviderApi.upsert(providerId, data);
    setEditingId(null);
    await loadData();
  };

  const handleTest = async (providerId: string) => {
    setTestingId(providerId);
    setTestResults(prev => {
      const next = { ...prev };
      delete next[providerId];
      return next;
    });
    try {
      const result = await userProviderApi.test(providerId);
      setTestResults(prev => ({ ...prev, [providerId]: result }));
    } catch (err) {
      setTestResults(prev => ({
        ...prev,
        [providerId]: { success: false, message: err instanceof Error ? err.message : '连接测试失败', model: '' },
      }));
    } finally {
      setTestingId(null);
    }
  };

  const handleDeleteConfig = async () => {
    if (!deleteConfirmId) return;
    setDeleting(true);
    try {
      await userProviderApi.remove(deleteConfirmId);
      // 若删除的是默认模型，服务端已自动清空，同步本地状态
      if (defaultProviderId === deleteConfirmId) {
        setDefaultProviderId(null);
      }
      setDeleteConfirmId(null);
      await loadData();
    } catch (err) {
      console.error('删除配置失败:', err);
    } finally {
      setDeleting(false);
    }
  };

  const handleSetDefault = async (providerId: string | null) => {
    setSettingDefault(true);
    try {
      await userProviderApi.setDefault(providerId);
      setDefaultProviderId(providerId);
    } catch (err) {
      console.error('设置默认模型失败:', err);
    } finally {
      setSettingDefault(false);
    }
  };

  const handleCreateCustom = async (e: FormEvent) => {
    e.preventDefault();
    const id = customForm.id.trim().toLowerCase();
    if (!id || !customForm.baseUrl.trim() || !customForm.model.trim() || !customForm.apiKey.trim()) {
      setCustomError('请填写 Provider ID、Base URL、模型和 API Key');
      return;
    }
    setCreatingCustom(true);
    setCustomError('');
    try {
      await userProviderApi.upsert(id, {
        baseUrl: customForm.baseUrl.trim(),
        model: customForm.model.trim(),
        apiKey: customForm.apiKey.trim(),
      });
      setCustomForm({ id: '', baseUrl: '', model: '', apiKey: '' });
      setShowCustomForm(false);
      await loadData();
    } catch (err) {
      setCustomError(err instanceof Error ? err.message : '创建失败，请重试');
    } finally {
      setCreatingCustom(false);
    }
  };

  const renderProviderCard = (provider: UserProviderItem) => {
    const testResult = testResults[provider.id];
    const isDefault = defaultProviderId === provider.id;

    return (
      <motion.div
        key={provider.id}
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-4 shadow-sm"
      >
        <div className="flex items-start justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div className="w-9 h-9 rounded-lg bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400 flex items-center justify-center flex-shrink-0">
              <Server className="w-4.5 h-4.5" />
            </div>
            <div className="min-w-0">
              <div className="flex items-center gap-2">
                <span className="font-semibold text-sm text-slate-800 dark:text-white">{provider.id}</span>
                {provider.custom && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-indigo-100 dark:bg-indigo-900/40 text-indigo-700 dark:text-indigo-300">
                    自定义
                  </span>
                )}
                {provider.hasOwnConfig && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300">
                    已配置
                  </span>
                )}
                {isDefault && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-primary-100 dark:bg-primary-900/40 text-primary-700 dark:text-primary-300">
                    默认
                  </span>
                )}
                {!provider.enabled && (
                  <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400">
                    已停用
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 truncate">
                模型: <span className="font-mono">{provider.model}</span>
              </p>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-0.5 truncate">
                {provider.hasOwnConfig ? `API Key: ${provider.maskedApiKey}` : 'API Key: 使用全局配置'}
              </p>
            </div>
          </div>
        </div>

        {/* 测试结果 */}
        {testResult && (
          <div className={`mt-3 px-3 py-2 rounded-lg text-xs font-medium ${
            testResult.success
              ? 'bg-emerald-50 dark:bg-emerald-900/20 text-emerald-700 dark:text-emerald-300'
              : 'bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300'
          }`}>
            <div className="flex items-center gap-1.5">
              {testResult.success ? <CheckCircle className="w-3.5 h-3.5 flex-shrink-0" /> : <XCircle className="w-3.5 h-3.5 flex-shrink-0" />}
              <span>{testResult.message}</span>
            </div>
          </div>
        )}

        {/* 操作按钮 */}
        <div className="mt-3 flex items-center gap-2 border-t border-slate-100 dark:border-slate-700 pt-3">
          <button
            onClick={() => setEditingId(editingId === provider.id ? null : provider.id)}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
          >
            <KeyRound className="w-3.5 h-3.5" />
            {provider.hasOwnConfig ? '重新配置' : '配置'}
          </button>
          <button
            onClick={() => handleTest(provider.id)}
            disabled={testingId === provider.id}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-blue-600 dark:text-blue-400 hover:bg-blue-50 dark:hover:bg-blue-900/20 transition-colors disabled:opacity-50"
          >
            {testingId === provider.id
              ? <Loader2 className="w-3.5 h-3.5 animate-spin" />
              : <RefreshCw className="w-3.5 h-3.5" />}
            测试连接
          </button>
          {!isDefault && isDefaultSelectable(provider) && (
            <button
              onClick={() => handleSetDefault(provider.id)}
              disabled={settingDefault}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-primary-600 dark:text-primary-400 hover:bg-primary-50 dark:hover:bg-primary-900/20 transition-colors disabled:opacity-50"
            >
              <Plug className="w-3.5 h-3.5" />
              设为默认
            </button>
          )}
          {provider.hasOwnConfig && (
            <button
              onClick={() => setDeleteConfirmId(provider.id)}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 ml-auto transition-colors"
            >
              <Trash2 className="w-3.5 h-3.5" />
              删除配置
            </button>
          )}
        </div>

        {/* 行内配置表单 */}
        <AnimatePresence>
          {editingId === provider.id && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="overflow-hidden"
            >
              <ProviderConfigForm
                provider={provider}
                onSave={(data) => handleSave(provider.id, data)}
                onCancel={() => setEditingId(null)}
              />
            </motion.div>
          )}
        </AnimatePresence>
      </motion.div>
    );
  };

  const inputClass = 'w-full px-3.5 py-2 rounded-lg border border-slate-200 dark:border-slate-600 bg-white dark:bg-slate-700 text-sm text-slate-900 dark:text-white placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400 transition-shadow';

  return (
    <div className="max-w-5xl mx-auto">
      {/* 页面标题 */}
      <div className="flex items-center gap-4 mb-8">
        <div className="p-3 rounded-xl bg-gradient-to-r from-primary-500 to-primary-600 shadow-lg shadow-primary-500/25">
          <Cpu className="w-6 h-6 text-white" />
        </div>
        <div>
          <h1 className="text-2xl font-bold text-slate-800 dark:text-white">我的模型</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-0.5 text-sm">配置自己的模型 API Key，管理默认聊天模型</p>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-primary-500 animate-spin" />
        </div>
      ) : loadError ? (
        <div className="text-center py-20 text-red-500">{loadError}</div>
      ) : (
        <div className="space-y-8">
          {/* 默认模型 */}
          <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5 shadow-sm">
            <h2 className="text-base font-bold text-slate-800 dark:text-white mb-1">默认聊天模型</h2>
            <p className="text-xs text-slate-500 dark:text-slate-400 mb-4">策略生成与问答默认使用的模型；跟随全局默认时使用管理员配置的全局模型</p>

            {settingDefault ? (
              <div className="py-4 flex justify-center">
                <Loader2 className="w-6 h-6 text-primary-500 animate-spin" />
              </div>
            ) : (
              <div className="space-y-2">
                <label className="flex items-center gap-3 p-3 rounded-lg border border-slate-200 dark:border-slate-600 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors">
                  <input
                    type="radio"
                    name="default-provider"
                    checked={defaultProviderId === null}
                    onChange={() => handleSetDefault(null)}
                    className="w-4 h-4 text-primary-500 focus:ring-primary-500"
                  />
                  <span className="text-sm text-slate-700 dark:text-slate-300 font-medium">跟随全局默认</span>
                </label>
                {providers.filter(isDefaultSelectable).map(provider => (
                  <label
                    key={provider.id}
                    className="flex items-center gap-3 p-3 rounded-lg border border-slate-200 dark:border-slate-600 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700/50 transition-colors"
                  >
                    <input
                      type="radio"
                      name="default-provider"
                      checked={defaultProviderId === provider.id}
                      onChange={() => handleSetDefault(provider.id)}
                      className="w-4 h-4 text-primary-500 focus:ring-primary-500"
                    />
                    <div className="flex-1 min-w-0">
                      <span className="text-sm text-slate-700 dark:text-slate-300 font-medium">{provider.id}</span>
                      <span className="text-xs text-slate-400 dark:text-slate-500 ml-2 font-mono">{provider.model}</span>
                    </div>
                    {provider.hasOwnConfig && (
                      <span className="px-1.5 py-0.5 rounded text-[10px] font-medium bg-emerald-100 dark:bg-emerald-900/40 text-emerald-700 dark:text-emerald-300 flex-shrink-0">
                        已配置
                      </span>
                    )}
                  </label>
                ))}
              </div>
            )}
          </div>

          {/* 内置模型配置 */}
          <div>
            <h2 className="text-base font-bold text-slate-800 dark:text-white mb-4 flex items-center gap-2">
              <Globe className="w-4.5 h-4.5 text-primary-500" />
              内置模型配置
            </h2>
            {builtinProviders.length === 0 ? (
              <div className="text-center py-10 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 text-sm">
                暂无内置模型，请联系管理员配置
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {builtinProviders.map(renderProviderCard)}
              </div>
            )}
          </div>

          {/* 自定义 Provider */}
          <div>
            <div className="flex items-center justify-between mb-4">
              <h2 className="text-base font-bold text-slate-800 dark:text-white flex items-center gap-2">
                <Server className="w-4.5 h-4.5 text-primary-500" />
                自定义 Provider
              </h2>
              <button
                onClick={() => setShowCustomForm(!showCustomForm)}
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-primary-500 text-white text-xs font-medium hover:bg-primary-600 transition-colors"
              >
                {showCustomForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
                {showCustomForm ? '取消' : '添加自定义 Provider'}
              </button>
            </div>

            {showCustomForm && (
              <form
                onSubmit={handleCreateCustom}
                className="mb-4 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-5 shadow-sm space-y-3"
              >
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                      Provider ID <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={customForm.id}
                      onChange={(e) => setCustomForm(f => ({ ...f, id: e.target.value }))}
                      placeholder="例如: my-openai"
                      className={inputClass}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                      Base URL <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={customForm.baseUrl}
                      onChange={(e) => setCustomForm(f => ({ ...f, baseUrl: e.target.value }))}
                      placeholder="例如: https://api.openai.com/v1"
                      className={inputClass}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                      模型 <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="text"
                      value={customForm.model}
                      onChange={(e) => setCustomForm(f => ({ ...f, model: e.target.value }))}
                      placeholder="例如: gpt-4o"
                      className={inputClass}
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-slate-600 dark:text-slate-300 mb-1">
                      API Key <span className="text-red-500">*</span>
                    </label>
                    <input
                      type="password"
                      value={customForm.apiKey}
                      onChange={(e) => setCustomForm(f => ({ ...f, apiKey: e.target.value }))}
                      placeholder="输入 API Key"
                      className={inputClass}
                    />
                  </div>
                </div>

                {customError && (
                  <p className="text-xs text-red-600 dark:text-red-400">{customError}</p>
                )}

                <button
                  type="submit"
                  disabled={creatingCustom}
                  className="flex items-center gap-1.5 px-4 py-2 rounded-lg bg-primary-500 text-white text-xs font-medium hover:bg-primary-600 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                >
                  {creatingCustom ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
                  创建
                </button>
              </form>
            )}

            {customProviders.length === 0 && !showCustomForm ? (
              <div className="text-center py-10 bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 text-slate-400 dark:text-slate-500 text-sm">
                暂无自定义 Provider，可添加任意 OpenAI 兼容端点
              </div>
            ) : (
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                {customProviders.map(renderProviderCard)}
              </div>
            )}
          </div>
        </div>
      )}

      {/* 删除配置确认 */}
      <ConfirmDialog
        open={deleteConfirmId !== null}
        title="删除模型配置"
        message={`确定要删除 "${deleteConfirmId ?? ''}" 的配置吗？删除后该模型将回退到全局默认配置。`}
        confirmText="确定删除"
        cancelText="取消"
        confirmVariant="danger"
        loading={deleting}
        onConfirm={handleDeleteConfig}
        onCancel={() => {
          if (!deleting) {
            setDeleteConfirmId(null);
          }
        }}
      />
    </div>
  );
}
