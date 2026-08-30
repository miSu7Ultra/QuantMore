import { useState } from 'react';
import { knowledgeBaseApi } from '../api/knowledgebase';
import type { KbVisibility, UploadKnowledgeBaseResponse } from '../api/knowledgebase';
import FileUploadCard from '../components/FileUploadCard';
import { useAuth } from '../AuthContext';
import { Globe, Lock } from 'lucide-react';

interface KnowledgeBaseUploadPageProps {
  onUploadComplete: (result: UploadKnowledgeBaseResponse) => void;
  onBack: () => void;
}

export default function KnowledgeBaseUploadPage({ onUploadComplete, onBack }: KnowledgeBaseUploadPageProps) {
  const { isAdmin } = useAuth();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  // 可见性默认私有；仅管理员可选择公共
  const [visibility, setVisibility] = useState<KbVisibility>('PRIVATE');

  const handleUpload = async (file: File, name?: string) => {
    setUploading(true);
    setError('');

    try {
      const data = await knowledgeBaseApi.uploadKnowledgeBase(file, name, undefined, visibility);
      onUploadComplete(data);
    } catch (err: unknown) {
      const errorMessage = err instanceof Error ? err.message : '上传失败，请重试';
      setError(errorMessage);
      setUploading(false);
    }
  };

  return (
    <FileUploadCard
      title="上传知识库"
      subtitle="上传文档，AI 将基于知识库内容回答您的问题"
      accept=".pdf,.doc,.docx,.txt,.md"
      formatHint="支持 PDF、DOCX、DOC、TXT、MD"
      maxSizeHint="最大 50MB"
      uploading={uploading}
      uploadButtonText="开始上传"
      selectButtonText="选择文件"
      showNameInput={true}
      nameLabel="知识库名称（可选）"
      namePlaceholder="留空则使用文件名"
      error={error}
      extraFields={
        isAdmin ? (
          <div>
            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
              可见性
            </label>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <button
                type="button"
                onClick={() => setVisibility('PRIVATE')}
                className={`flex items-center gap-3 p-3 rounded-xl border transition-colors ${
                  visibility === 'PRIVATE'
                    ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30'
                    : 'border-slate-200 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700/50'
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${
                    visibility === 'PRIVATE'
                      ? 'bg-primary-100 dark:bg-primary-900/50 text-primary-600 dark:text-primary-400'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
                  }`}
                >
                  <Lock className="w-4.5 h-4.5" />
                </div>
                <div className="text-left">
                  <p className="text-sm font-medium text-slate-800 dark:text-white">私有</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">仅自己可见，其他用户无法访问</p>
                </div>
              </button>
              <button
                type="button"
                onClick={() => setVisibility('PUBLIC')}
                className={`flex items-center gap-3 p-3 rounded-xl border transition-colors ${
                  visibility === 'PUBLIC'
                    ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/30'
                    : 'border-slate-200 dark:border-slate-600 hover:bg-slate-50 dark:hover:bg-slate-700/50'
                }`}
              >
                <div
                  className={`w-9 h-9 rounded-lg flex items-center justify-center flex-shrink-0 ${
                    visibility === 'PUBLIC'
                      ? 'bg-primary-100 dark:bg-primary-900/50 text-primary-600 dark:text-primary-400'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
                  }`}
                >
                  <Globe className="w-4.5 h-4.5" />
                </div>
                <div className="text-left">
                  <p className="text-sm font-medium text-slate-800 dark:text-white">公共</p>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">所有用户可见（仅管理员可上传）</p>
                </div>
              </button>
            </div>
          </div>
        ) : undefined
      }
      onUpload={handleUpload}
      onBack={onBack}
    />
  );
}
