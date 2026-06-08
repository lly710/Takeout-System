import { setAuthToken } from "../../../../../shared/http";
import { merchantLogin } from "@/api/merchant";
import { initializeMerchantWorkspace, resetWorkspaceInitialization } from "./workspaceBridge";
import {
  authForm,
  authMessage,
  clearProfileStorage,
  isSignedIn,
  merchantId,
  merchantProfile,
  resetMerchantState,
  saveProfile,
  unwrap
} from "./state";

async function login() {
  const account = String(authForm.account || "").trim();
  const password = String(authForm.password || "").trim();
  if (!account || !password) {
    authMessage.value = "请输入商家账号和密码。";
    return false;
  }
  try {
    const data = unwrap(await merchantLogin({ account, password }));
    setAuthToken(data.token || "");
    saveProfile(data.profile || data);
    authMessage.value = "登录成功，正在同步工作台数据。";
    await initializeMerchantWorkspace(true);
    return true;
  } catch (error) {
    setAuthToken("");
    merchantProfile.value = null;
    authMessage.value = error?.message || "商家账号或密码错误，请重新输入。";
    return false;
  }
}

function logout() {
  setAuthToken("");
  clearProfileStorage();
  resetMerchantState();
  resetWorkspaceInitialization();
}

export function useMerchantSession() {
  return {
    authForm,
    authMessage,
    merchantProfile,
    isSignedIn,
    merchantId,
    login,
    logout
  };
}
