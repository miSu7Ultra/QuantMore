import {useEffect, useState} from 'react';

type Theme = 'light' | 'dark';

export function useTheme() {
    const [theme, setTheme] = useState<Theme>(() => {
        // 默认浅色;仅当用户显式选择深色时使用深色
        const stored = localStorage.getItem('theme') as Theme;
        return stored === 'dark' ? 'dark' : 'light';
    });

    // 同步到 document 和 localStorage
    useEffect(() => {
        const root = document.documentElement;
        if (theme === 'dark') {
            root.classList.add('dark');
        } else {
            root.classList.remove('dark');
        }
        localStorage.setItem('theme', theme);
    }, [theme]);

    // 切换主题
    const toggleTheme = () => {
        setTheme((prev) => (prev === 'light' ? 'dark' : 'light'));
    };

    return {theme, toggleTheme};
}
