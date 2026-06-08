import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import "./assets/styles/admin.css";
import "../../../shared/app-shell.css";
import { installAuthExpiredHandler } from "../../../shared/auth-session";

installAuthExpiredHandler(router, {
  loginRoute: { name: "AdminLogin" },
  profileKeys: ["takeout-admin-profile"]
});

createApp(App).use(ElementPlus).use(router).mount("#app");
