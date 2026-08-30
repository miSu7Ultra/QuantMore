import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

// 初始化主题（避免页面闪烁）：默认浅色，用户可手动切换深色
(function initTheme() {
    const stored = localStorage.getItem('theme');
    if (stored === 'dark') {
        document.documentElement.classList.add('dark');
    }
})();

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
