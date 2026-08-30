import { useState } from 'react';
import { knowledgeBaseApi } from '../api/knowledgebase';
import type { KbVisibility, UploadKnowledgeBaseResponse } from '../api/knowledgebase';
import FileUploadCard from '../components/FileUploadCard';
import { useAuth } from '../AuthContext';

interface KnowledgeBaseUploadPageProps {
  onUploadComplete: (result: UploadKnowledgeBaseResponse) => void;
  onBack: () => void;
}

export default function KnowledgeBaseUploadPage({ onUploadComplete, onBack }: KnowledgeBaseUploadPageProps) {
  const { isAdmin } = useAuth();
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [category, setCategory] = useState('');
  const [visibility, setVisibility] = useState<KbVisibility>('PRIVATE');

  const handleUpload = async (file: File, name?: string) => {
    setUploading(true);
    setError('');

    try {
      const data = await knowledgeBaseApi.uploadKnowledgeBase(
        file,
        name,
        category.trim() || undefined,
        isAdmin ? visibility : 'PRIVATE',
      );
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
      onUpload={handleUpload}
      onBack={onBack}
      extraFields={
        <>
          <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-lg dark:shadow-slate-900/50">
            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">
              分类（可选）
            </label>
            <input
              type="text"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="例如: 策略示例、API 文档"
              className="w-full px-4 py-3 border border-slate-200 dark:border-slate-600 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent bg-white dark:bg-slate-700 text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500"
              disabled={uploading}
            />
          </div>
          {isAdmin && (
            <div className="bg-white dark:bg-slate-800 rounded-2xl p-6 shadow-lg dark:shadow-slate-900/50">
              <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-3">
                可见性
              </label>
              <div className="flex gap-6">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="visibility"
                    value="PRIVATE"
                    checked={visibility === 'PRIVATE'}
                    onChange={() => setVisibility('PRIVATE')}
                    className="accent-primary-500"
                    disabled={uploading}
                  />
                  <span className="text-sm text-slate-700 dark:text-slate-300">
                    私有 <span className="text-slate-400 text-xs">(仅自己可见)</span>
                  </span>
                </label>
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="radio"
                    name="visibility"
                    value="PUBLIC"
                    checked={visibility === 'PUBLIC'}
                    onChange={() => setVisibility('PUBLIC')}
                    className="accent-primary-500"
                    disabled={uploading}
                  />
                  <span className="text-sm text-slate-700 dark:text-slate-300">
                    公共 <span className="text-slate-400 text-xs">(全员可见,管理员)</span>
                  </span>
                </label>
              </div>
            </div>
          )}
        </>
      }
    />
  );
}
