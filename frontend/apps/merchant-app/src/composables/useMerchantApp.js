import { useMerchantOrders } from "@/composables/merchant/useMerchantOrders";
import { useMerchantProducts } from "@/composables/merchant/useMerchantProducts";
import { useMerchantSession } from "@/composables/merchant/useMerchantSession";
import { merchantStorageKey, todayString } from "@/composables/merchant/state";
import { useMerchantWorkspace } from "@/composables/merchant/useMerchantWorkspace";

export { merchantStorageKey };

export function useMerchantApp() {
  return {
    ...useMerchantSession(),
    ...useMerchantProducts(),
    ...useMerchantOrders(),
    ...useMerchantWorkspace(),
    todayString
  };
}
