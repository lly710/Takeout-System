export const pageMenu = [
  { key: "home", label: "首页", icon: "HomeFilled", keywords: ["工作台", "看板", "首页"], routeName: "MerchantHome" },
  { key: "orders", label: "订单管理", icon: "DocumentCopy", keywords: ["订单", "接单", "退款"], routeName: "MerchantOrders" },
  { key: "products", label: "商品管理", icon: "Goods", keywords: ["商品", "菜品", "分类"], routeName: "MerchantProducts" },
  { key: "marketing", label: "营销推广", icon: "Promotion", keywords: ["活动", "优惠券", "满减"], routeName: "MerchantMarketing" },
  { key: "analytics", label: "经营数据", icon: "DataAnalysis", keywords: ["数据", "营收", "流量"], routeName: "MerchantAnalytics" },
  { key: "reviews", label: "评价管理", icon: "Star", keywords: ["评价", "差评", "星级"], routeName: "MerchantReviews" },
  { key: "finance", label: "财务管理", icon: "Wallet", keywords: ["财务", "对账", "结算"], routeName: "MerchantFinance" },
  { key: "settings", label: "店铺设置", icon: "Setting", keywords: ["设置", "店铺", "营业"], routeName: "MerchantSettings" }
];

export const orderStatusMeta = {
  CREATED: { label: "待接单", tab: "PENDING", tone: "warning" },
  PENDING_ACCEPT: { label: "待接单", tab: "PENDING", tone: "warning" },
  MERCHANT_ACCEPTED: { label: "待出餐", tab: "PROCESSING", tone: "primary" },
  RIDER_ACCEPTED: { label: "待出餐", tab: "PROCESSING", tone: "warning" },
  PREPARING: { label: "待出餐", tab: "PROCESSING", tone: "warning" },
  MERCHANT_PREPARED: { label: "商家已出餐", tab: "PROCESSING", tone: "success" },
  ARRIVED_STORE: { label: "骑手已取货", tab: "PROCESSING", tone: "success" },
  DELIVERING: { label: "配送中", tab: "PROCESSING", tone: "success" },
  COMPLETED: { label: "已完成", tab: "COMPLETED", tone: "info" },
  CANCELLED: { label: "已取消", tab: "COMPLETED", tone: "info" },
  REFUNDING: { label: "退款中", tab: "REFUND", tone: "danger" },
  REFUNDED: { label: "已退款", tab: "REFUND", tone: "danger" }
};

export const marketingSeed = [
  { id: "A-101", type: "满减", title: "午高峰满 25 减 6", time: "10:00 - 14:00", status: "进行中", budget: "￥680", effect: "带来 126 单" },
  { id: "A-102", type: "折扣", title: "饮品第二件 7 折", time: "全天", status: "待开始", budget: "￥320", effect: "预计提升加购率 18%" },
  { id: "A-103", type: "优惠券", title: "新客 8 元无门槛券", time: "本周", status: "进行中", budget: "￥500", effect: "新客占比 31%" },
  { id: "A-104", type: "竞价推广", title: "商圈晚餐档位加价", time: "17:00 - 21:00", status: "投放中", budget: "￥900", effect: "曝光提升 24%" }
];

export const reviewSeed = [
  { id: 1, user: "王女士", score: 5, tag: "出餐快", content: "打包完整，骑手取餐也很快。", time: "今天 12:20" },
  { id: 2, user: "陈先生", score: 4, tag: "口味稳定", content: "味道不错，建议配菜再多一点。", time: "今天 11:48" },
  { id: 3, user: "李同学", score: 2, tag: "差评", content: "饮品撒漏，申请退款处理中。", time: "昨天 20:11" },
  { id: 4, user: "赵女士", score: 5, tag: "回头客", content: "连续点了三次，配送都很准时。", time: "昨天 18:36" }
];
