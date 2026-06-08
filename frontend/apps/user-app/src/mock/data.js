export const userPageData = {
  location: "北京市朝阳区望京 SOHO",
  merchants: [
    {
      id: 1,
      name: "城南小馆",
      distance: "800m",
      tag: "热销盖饭",
      notice: "满 20 元起送，预计 28 分钟送达"
    },
    {
      id: 2,
      name: "轻食能量站",
      distance: "1.4km",
      tag: "健身轻食",
      notice: "低脂高蛋白，预计 32 分钟送达"
    }
  ],
  cart: [
    { id: 101, name: "黄焖鸡米饭", price: 22, count: 1 },
    { id: 102, name: "鱼香肉丝盖饭", price: 24, count: 1 }
  ],
  order: {
    no: "WM202605260001",
    status: "配送中",
    rider: "骑手小陈",
    riderPhone: "138****1024",
    timeline: ["下单", "商家接单", "骑手接单", "到店取餐", "配送中", "完成"],
    points: [
      { name: "商家位置", value: "116.397128, 39.916527" },
      { name: "用户位置", value: "116.410800, 39.920200" },
      { name: "骑手位置", value: "116.404500, 39.918000" }
    ],
    tracks: [
      "14:02 商家接单",
      "14:09 骑手接单",
      "14:15 到店取餐",
      "14:20 配送中"
    ]
  }
};
