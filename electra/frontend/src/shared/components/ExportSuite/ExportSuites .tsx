import React from "react";
import { RiFileExcel2Line } from "react-icons/ri";
import { ImFilePdf } from "react-icons/im";
import axios from "axios";
import api from "../../../api/axiosConfig"

interface ExportSuitesProps {
  testSuiteId: number;
}

const ExportSuites: React.FC<ExportSuitesProps> = ({ testSuiteId }) => {
  const handleExportExcel = async () => {
    try {
      const token = localStorage.getItem("token");
      const response = await api.get(
        `/relatorio-jira/excel?testSuiteId=${testSuiteId}`,
        {
          responseType: "blob",
          headers: {
            Authorization: `Bearer ${token}`,
            "Cache-Control": "no-cache",
            Pragma: "no-cache",
          },
        }
      );

      if (!response.data) {
        throw new Error("Resposta vazia do backend.");
      }

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `relatorio_jira_${testSuiteId}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error("Erro ao exportar Excel:", error);
      alert("Erro ao exportar Excel. Verifique o console para mais detalhes.");
    }
  };

  const handleExportPdf = async () => {
    try {
      const token = localStorage.getItem("token");
      const response = await api.get(
        `/relatorio-jira/pdf?testSuiteId=${testSuiteId}`,
        {
          responseType: "blob",
          headers: {
            Authorization: `Bearer ${token}`,
            "Cache-Control": "no-cache",
            Pragma: "no-cache",
          },
        }
      );

      if (!response.data) {
        throw new Error("Resposta vazia do backend.");
      }

      const url = window.URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement("a");
      link.href = url;
      link.setAttribute("download", `relatorio_jira_${testSuiteId}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (error) {
      console.error("Erro ao exportar PDF:", error);
      alert("Erro ao exportar PDF. Verifique o console para mais detalhes.");
    }
  };
  return (
    <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
      <RiFileExcel2Line
        style={{ color: "#198754", cursor: "pointer" }}
        onClick={handleExportExcel}
        title="Exportar para Excel"
      />
      <ImFilePdf
        style={{ color: "#dc3545", cursor: "pointer" }}
        onClick={handleExportPdf}
        title="Exportar para PDF"
      />
    </div>
  );
};

export default ExportSuites;