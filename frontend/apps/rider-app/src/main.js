import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import "./assets/styles/rider.css";
import { installAuthExpiredHandler } from "../../../shared/auth-session";

installAuthExpiredHandler(router, {
  loginRoute: { name: "RiderLogin" },
  profileKeys: ["takeout-rider-profile"]
});

createApp(App).use(router).mount("#app");
