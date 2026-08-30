import { BrowserRouter, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import Layout from './components/Layout';
import { Suspense, lazy } from 'react';
import type { UploadKnowledgeBaseResponse } from './api/knowledgebase';
import { ROUTES } from './constants/routes';
import { AuthProvider, RequireAuth } from './AuthContext';

// Lazy load pages
const LoginPage = lazy(() => import('./pages/LoginPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const KnowledgeBaseManagePage = lazy(() => import('./pages/KnowledgeBaseManagePage'));
const KnowledgeBaseUploadPage = lazy(() => import('./pages/KnowledgeBaseUploadPage'));
const KnowledgeBaseQueryPage = lazy(() => import('./pages/KnowledgeBaseQueryPage'));
const SettingsPage = lazy(() => import('./pages/SettingsPage'));
const UserProviderPage = lazy(() => import('./pages/UserProviderPage'));
const StrategyGeneratorPage = lazy(() => import('./pages/StrategyGeneratorPage'));
const StrategyHistoryPage = lazy(() => import('./pages/StrategyHistoryPage'));

// Loading component
const Loading = () => (
  <div className="flex items-center justify-center min-h-[50vh]">
    <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full animate-spin" />
  </div>
);

// 知识库管理页面包装器
function KnowledgeBaseManagePageWrapper() {
  const navigate = useNavigate();

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  const handleChat = () => {
    navigate(ROUTES.knowledgebaseChat);
  };

  return <KnowledgeBaseManagePage onUpload={handleUpload} onChat={handleChat} />;
}

// 知识库上传页面包装器
function KnowledgeBaseUploadPageWrapper() {
  const navigate = useNavigate();

  const handleUploadComplete = (_result: UploadKnowledgeBaseResponse) => {
    // 上传完成后返回管理页面
    navigate(ROUTES.knowledgebase);
  };

  const handleBack = () => {
    navigate(ROUTES.knowledgebase);
  };

  return <KnowledgeBaseUploadPage onUploadComplete={handleUploadComplete} onBack={handleBack} />;
}

// 知识库问答页面包装器
function KnowledgeBaseQueryPageWrapper() {
  const navigate = useNavigate();

  const handleBack = () => {
    navigate(ROUTES.knowledgebase);
  };

  const handleUpload = () => {
    navigate(ROUTES.knowledgebaseUpload);
  };

  return <KnowledgeBaseQueryPage onBack={handleBack} onUpload={handleUpload} />;
}

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Suspense fallback={<Loading />}>
          <Routes>
            {/* 公开页面：登录 / 注册 */}
            <Route path={ROUTES.login} element={<LoginPage />} />
            <Route path={ROUTES.register} element={<RegisterPage />} />

            {/* 受保护页面：需登录后才能访问 */}
            <Route
              path="/"
              element={
                <RequireAuth>
                  <Layout />
                </RequireAuth>
              }
            >
              {/* 默认重定向到知识库管理页面 */}
              <Route index element={<Navigate to={ROUTES.knowledgebase} replace />} />

              {/* 知识库管理 */}
              <Route path="knowledgebase" element={<KnowledgeBaseManagePageWrapper />} />

              {/* 知识库上传 */}
              <Route path="knowledgebase/upload" element={<KnowledgeBaseUploadPageWrapper />} />

              {/* 问答助手（知识库聊天） */}
              <Route path="knowledgebase/chat" element={<KnowledgeBaseQueryPageWrapper />} />

              {/* 策略生成器 */}
              <Route path="generator" element={<StrategyGeneratorPage />} />

              {/* 策略生成历史 */}
              <Route path="generator/history" element={<StrategyHistoryPage />} />

              {/* 我的模型（用户级 Provider 配置） */}
              <Route path="settings/my-providers" element={<UserProviderPage />} />

              {/* 管理员设置 */}
              <Route path="settings" element={<SettingsPage />} />

              {/* 未匹配路由回退到知识库管理 */}
              <Route path="*" element={<Navigate to={ROUTES.knowledgebase} replace />} />
            </Route>
          </Routes>
        </Suspense>
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;
