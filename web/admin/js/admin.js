// Shared Layout & Admin Logic
const adminLogic = {
    init: function () {
        this.injectIcons();
        this.renderSidebar();
        this.checkAuth();
        this.setupMobileMenu();
    },

    injectIcons: function () {
        if (!document.getElementById('material-icons')) {
            const link = document.createElement('link');
            link.id = 'material-icons';
            link.rel = 'stylesheet';
            link.href = 'https://fonts.googleapis.com/css2?family=Material+Symbols+Rounded:opsz,wght,FILL,GRAD@24,400,1,0';
            document.head.appendChild(link);

            // Add Icon CSS helper
            const style = document.createElement('style');
            style.textContent = `
                .material-symbols-rounded { vertical-align: middle; font-size: 20px; }
                .nav-link .material-symbols-rounded { margin-right: 8px; font-size: 22px; }
                .btn .material-symbols-rounded { font-size: 18px; }
            `;
            document.head.appendChild(style);
        }
    },

    checkAuth: function () {
        auth.onAuthStateChanged(user => {
            if (!user) window.location.href = '../index.html';
            else {
                // Determine active page
                const path = window.location.pathname;
                const page = path.split('/').pop().replace('.html', '') || 'index';
                this.setActiveLink(page);

                // Show user email
                const emailEl = document.getElementById('adminEmail');
                if (emailEl) emailEl.textContent = user.email;
            }
        });
    },

    renderSidebar: function () {
        const sidebar = document.querySelector('.sidebar');
        if (!sidebar) return;

        sidebar.innerHTML = `
            <div class="sidebar-header">
                <div class="brand-icon" style="background:transparent;">
                    <svg viewBox="0 0 80 80" width="40" height="40" xmlns="http://www.w3.org/2000/svg">
                        <rect x="4" y="4" width="72" height="72" rx="16" fill="#1E3A8A" />
                        <path fill="#FFFFFF" d="M40,22c-7.732,0 -14,6.268 -14,14c0,10.5 14,22 14,22s14,-11.5 14,-22C54,28.268 47.732,22 40,22zM40,41c-2.761,0 -5,-2.239 -5,-5s2.239,-5 5,-5s5,2.239 5,5S42.761,41 40,41z" />
                        <path fill="none" stroke="#10B981" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" d="M37,36l2,2l5,-5" />
                    </svg>
                </div>
                <div>
                    <h3 style="font-weight:700;letter-spacing:-0.5px; color:white;">CivicEye</h3>
                    <small style="opacity:0.6; color:#cbd5e1;">Admin Panel</small>
                </div>
            </div>
            <nav>
                <a href="index.html" class="nav-link" id="nav-index">
                    <span class="material-symbols-rounded">dashboard</span> Dashboard
                </a>
                <a href="issues.html" class="nav-link" id="nav-issues">
                    <span class="material-symbols-rounded">assignment_late</span> Issues
                </a>
                <a href="solved_issues.html" class="nav-link" id="nav-solved_issues">
                    <span class="material-symbols-rounded">check_circle</span> Solved Issues
                </a>
                <a href="staff.html" class="nav-link" id="nav-staff">
                    <span class="material-symbols-rounded">engineering</span> Staff
                </a>
                <a href="users.html" class="nav-link" id="nav-users">
                    <span class="material-symbols-rounded">group</span> Users
                </a>
                <a href="departments.html" class="nav-link" id="nav-departments">
                    <span class="material-symbols-rounded">domain</span> Departments
                </a>
                <a href="locations.html" class="nav-link" id="nav-locations">
                    <span class="material-symbols-rounded">location_on</span> Locations
                </a>
                <a href="reports.html" class="nav-link" id="nav-reports">
                    <span class="material-symbols-rounded">description</span> Reports
                </a>
                <div style="margin-top:auto;padding-top:24px;border-top:1px solid rgba(255,255,255,0.1);">
                     <a href="#" onclick="auth.signOut()" class="nav-link" style="color:#ef4444;">
                        <span class="material-symbols-rounded">logout</span> Logout
                    </a>
                </div>
            </nav>
        `;
    },

    setActiveLink: function (page) {
        document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
        const active = document.getElementById(`nav-${page}`);
        if (active) active.classList.add('active');
    },

    setupMobileMenu: function () {
        // Simple toggle for mobile (if header toggle exists)
        const toggle = document.getElementById('sidebarToggle');
        if (toggle) {
            toggle.addEventListener('click', () => {
                document.querySelector('.sidebar').classList.toggle('active');
            });
        }
    },

    // Toast Notification
    toast: function (msg, type = 'info') {
        const div = document.createElement('div');
        const bg = type === 'success' ? '#10b981' : type === 'error' ? '#ef4444' : '#3b82f6';
        const icon = type === 'success' ? 'check_circle' : type === 'error' ? 'error' : 'info';

        div.style.cssText = `
            position: fixed; bottom: 24px; right: 24px;
            padding: 12px 24px; background: ${bg};
            color: white; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            z-index: 9999; animation: slideIn 0.3s ease; font-weight: 500;
            display: flex; align-items: center; gap: 12px;
        `;
        div.innerHTML = `<span class="material-symbols-rounded">${icon}</span> ${msg}`;
        document.body.appendChild(div);
        setTimeout(() => {
            div.style.opacity = '0';
            setTimeout(() => div.remove(), 300);
        }, 3000);
    }
};

document.addEventListener('DOMContentLoaded', () => adminLogic.init());
