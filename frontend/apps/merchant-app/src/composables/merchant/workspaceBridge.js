import { resetWorkspaceInitialization, useMerchantWorkspace } from "./useMerchantWorkspace";

export async function initializeMerchantWorkspace(force = false) {
  return useMerchantWorkspace().initialize(force);
}

export { resetWorkspaceInitialization };
