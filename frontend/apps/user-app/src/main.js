import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import router from "./router";
import "./styles/user-mobile.css";
import "../../../shared/app-shell.css";
import { installAuthExpiredHandler } from "../../../shared/auth-session";

installAuthExpiredHandler(router, {
  loginRoute: { name: "Login" }
});

createApp(App).use(ElementPlus).use(router).mount("#app");
