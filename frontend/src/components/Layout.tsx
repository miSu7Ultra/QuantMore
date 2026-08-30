import {Link, Outlet, useLocation, useNavigate} from 'react-router-dom';
import {motion} from 'framer-motion';
import {
  ChevronRight,
  Cpu,
  Database,
  FileCode2,
  History,
  LogOut,
  MessageSquare,
  Moon,
  ShieldCheck,
  Sparkles,
  Sun,
  Upload,
} from 'lucide-react';
import {useTheme} from '../hooks/useTheme';
import {ROUTES} from '../constants/routes';
import {useAuth} from '../AuthContext';

interface NavItem {
  id: string;
  path: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  description?: string;
}

interface NavGroup {
  id: string;
  title: string;
  items: NavItem[];
}

export default function Layout() {
  const location = useLocation();
  const currentPath = location.pathname;
  const {theme, toggleTheme} = useTheme();
  const {user, isAdmin, logout} = useAuth();
  const navigate = useNavigate();

  // 按业务模块组织的导航项
  const navGroups: NavGroup[] = [
    {
      id: 'knowledge',
      title: '知识库',
      items: [
        { id: 'kb-manage', path: ROUTES.knowledgebase, label: '知识库管理', icon: Database, description: '管理知识文档' },
        { id: 'kb-upload', path: ROUTES.knowledgebaseUpload, label: '上传知识库', icon: Upload, description: '上传文档并向量化' },
        { id: 'chat', path: ROUTES.knowledgebaseChat, label: '问答助手', icon: MessageSquare, description: '基于知识库问答' },
      ],
    },
    {
      id: 'strategy',
      title: '策略',
      items: [
        { id: 'generator', path: ROUTES.generator, label: '策略生成器', icon: FileCode2, description: '生成 PTrade 策略代码' },
        { id: 'generator-history', path: ROUTES.generatorHistory, label: '生成历史', icon: History, description: '查看历史生成记录' },
      ],
    },
    {
      id: 'system',
      title: '系统',
      items: [
        { id: 'my-providers', path: ROUTES.myProviders, label: '我的模型', icon: Cpu, description: '管理个人模型配置' },
        ...(isAdmin
          ? [{ id: 'admin-settings', path: ROUTES.settings, label: '管理员设置', icon: ShieldCheck, description: '全局模型与知识库管理' }]
          : []),
      ],
    },
  ];

  // 判断当前页面是否匹配导航项
  const isActive = (path: string) => {
    return currentPath === path;
  };

  // 退出登录
  const handleLogout = () => {
    logout();
    navigate(ROUTES.login);
  };

  return (
    <div className="flex min-h-screen bg-gradient-to-br from-slate-50 to-indigo-50 dark:from-slate-900 dark:to-slate-800">
      {/* 左侧边栏 */}
      <aside className="w-64 bg-white dark:bg-slate-900 border-r border-slate-100 dark:border-slate-700 fixed h-screen left-0 top-0 z-50 flex flex-col">
        {/* Logo */}
        <div className="p-6 border-b border-slate-100 dark:border-slate-700 flex items-center justify-between">
          <Link to={ROUTES.knowledgebase} className="flex items-center gap-3">
            <div className="w-10 h-10 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-primary-500/30">
              <Sparkles className="w-5 h-5" />
            </div>
            <div>
              <span className="text-base font-bold text-slate-800 dark:text-white tracking-tight block">QuantMore · PTrade 助手</span>
              <span className="text-xs text-slate-400 dark:text-slate-500">PTrade 策略代码生成平台</span>
            </div>
          </Link>
        </div>

        {/* 主题切换按钮 */}
        <div className="px-4 pb-2">
          <button
            onClick={toggleTheme}
            className="w-full flex items-center justify-center gap-2 px-3 py-2 rounded-lg bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-700 transition-colors"
          >
            {theme === 'dark' ? (
              <>
                <Sun className="w-4 h-4" />
                <span className="text-sm font-medium">浅色模式</span>
              </>
            ) : (
              <>
                <Moon className="w-4 h-4" />
                <span className="text-sm font-medium">深色模式</span>
              </>
            )}
          </button>
        </div>

        {/* 导航菜单 */}
        <nav className="flex-1 p-4 overflow-y-auto">
          <div className="space-y-6">
            {navGroups.map((group) => (
              <div key={group.id}>
                <div className="px-3 mb-2">
                  <span className="text-xs font-semibold text-slate-400 dark:text-slate-500 uppercase tracking-wider">
                    {group.title}
                  </span>
                </div>
                <div className="space-y-1">
                  {group.items.map((item) => {
                    const active = isActive(item.path);

                    return (
                      <Link
                        key={item.id}
                        to={item.path}
                        className={`group relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200
                          ${active
                            ? 'bg-primary-50 dark:bg-primary-900/30 text-primary-600 dark:text-primary-400'
                            : 'text-slate-600 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 hover:text-slate-900 dark:hover:text-white'
                          }`}
                      >
                        <div className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors
                          ${active
                            ? 'bg-primary-100 dark:bg-primary-900/50 text-primary-600 dark:text-primary-400'
                            : 'bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 group-hover:bg-slate-200 dark:group-hover:bg-slate-700 group-hover:text-slate-700 dark:group-hover:text-white'
                          }`}
                        >
                          <item.icon className="w-5 h-5" />
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-sm block ${active ? 'font-semibold' : 'font-medium'}`}>
                            {item.label}
                          </span>
                          {item.description && (
                            <span className="text-xs text-slate-400 dark:text-slate-500 truncate block">
                              {item.description}
                            </span>
                          )}
                        </div>
                        {active && <ChevronRight className="w-4 h-4 text-primary-400" />}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </div>
        </nav>

        {/* 底部用户信息 */}
        <div className="p-4 border-t border-slate-100 dark:border-slate-700">
          {user ? (
            <div className="px-2 py-2 flex items-center gap-3">
              <div className="w-9 h-9 rounded-full bg-gradient-to-br from-primary-500 to-indigo-600 flex items-center justify-center text-white text-sm font-bold flex-shrink-0">
                {user.username.charAt(0).toUpperCase()}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-slate-800 dark:text-white truncate">{user.username}</p>
                <span
                  className={`inline-block mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-medium ${
                    isAdmin
                      ? 'bg-amber-100 dark:bg-amber-900/40 text-amber-700 dark:text-amber-400'
                      : 'bg-slate-100 dark:bg-slate-700 text-slate-500 dark:text-slate-400'
                  }`}
                >
                  {isAdmin ? '管理员' : '普通用户'}
                </span>
              </div>
              <button
                onClick={handleLogout}
                className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/30 rounded-lg transition-colors"
                title="退出登录"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <Link
              to={ROUTES.login}
              className="block px-3 py-2 text-center text-sm text-slate-600 dark:text-slate-400 hover:text-primary-500 transition-colors"
            >
              未登录，点击登录
            </Link>
          )}
        </div>
      </aside>

      {/* 主内容区 */}
      <main className="flex-1 ml-64 p-10 min-h-screen overflow-y-auto">
        <motion.div
          key={currentPath}
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          transition={{ duration: 0.3 }}
        >
          <Outlet />
        </motion.div>
      </main>
    </div>
  );
}
