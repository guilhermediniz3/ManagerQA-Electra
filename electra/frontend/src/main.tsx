import React from "react";
import ReactDOM from "react-dom/client";
import "@mantine/core/styles.css";
import "@mantine/notifications/styles.css";
import "./app/theme/styles.css";
import { ManagerApp } from "./app/ManagerApp";
ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <ManagerApp />
  </React.StrictMode>,
);
