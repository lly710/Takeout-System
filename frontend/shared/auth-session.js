import { authExpiredEventName } from "./http";

let toastTimer = null;

function showSessionToast(message) {
  const existing = document.querySelector("[data-auth-expired-toast]");
  if (existing) {
    existing.remove();
  }

  const toast = document.createElement("div");
  toast.dataset.authExpiredToast = "true";
  toast.textContent = message || "登录状态已过期，请重新登录。";
  Object.assign(toast.style, {
    position: "fixed",
    top: "24px",
    left: "50%",
    transform: "translateX(-50%)",
    zIndex: "99999",
    padding: "12px 18px",
    borderRadius: "10px",
    background: "#1f2937",
    color: "#fff",
    boxShadow: "0 12px 30px rgba(15, 23, 42, 0.18)",
    fontSize: "14px",
    lineHeight: "20px"
  });
  document.body.appendChild(toast);

  window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => toast.remove(), 2600);
}

export function installAuthExpiredHandler(router, options = {}) {
  const loginRoute = options.loginRoute || { path: "/login" };
  const profileKeys = options.profileKeys || [];

  window.addEventListener(authExpiredEventName, (event) => {
    profileKeys.forEach((key) => window.localStorage.removeItem(key));
    showSessionToast(event.detail?.message);

    const current = router.currentRoute.value;
    const target = {
      ...loginRoute,
      query: {
        redirect: current.fullPath,
        reason: "expired"
      }
    };

    if (current.path !== "/login") {
      router.replace(target);
    }
  });
}
