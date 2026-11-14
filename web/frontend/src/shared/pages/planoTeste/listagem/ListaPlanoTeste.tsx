import React, { useState, useEffect } from "react";
import Button from "react-bootstrap/Button";
import Table from "react-bootstrap/Table";
import Pagination from "react-bootstrap/Pagination";
import "bootstrap/dist/css/bootstrap.min.css";
import { FaEdit } from "react-icons/fa";
import "./styles.css";
import NavHorizontal from "../../../components/navs/horizontal/NavHorizontal";
import NavVertical from "../../../components/navs/vertical/NavVertical";
import { useNavigate } from "react-router-dom";
import ReportModal from "../../../components/Modal/ReportModal";
import api from "../../../../api/axiosConfig"; // Import the configured API instance

interface TestPlan {
  id: number;
  name: string;
  data: string;
  deliveryData: string;
  status: string;
  testerQAName: string;
  developerName: string;
  jira: string;
  callNumber: string;
  systemModuleName: string;
}

const TestPlanPage = () => {
  const navigate = useNavigate();
  const [results, setResults] = useState<TestPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [totalPages, setTotalPages] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [showReportModal, setShowReportModal] = useState(false);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [searchValue, setSearchValue] = useState("");

  const fetchTestPlans = async () => {
    setLoading(true);
    setError(null);
    try {
      const token = localStorage.getItem("token");

      const endpoint = searchValue
        ? `/testplans/search?searchValue=${searchValue}&page=${page}&size=${size}`
        : `/testplans/all?page=${page}&size=${size}`;

      const response = await api.get(endpoint, {
        headers: { Authorization: `Bearer ${token}` },
      });

      setResults(response.data.content || []);
      setTotalPages(response.data.totalPages || 0);
    } catch (error) {
      console.error("Erro ao buscar os planos de teste:", error);
      setError("Erro ao carregar os dados.");
    } finally {
      setLoading(false);
    }
  };

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const { value } = e.target;
    setSearchValue(value);
    setPage(0);
  };

  const handlePageChange = (newPage: number) => {
    setPage(newPage);
  };

  useEffect(() => {
    fetchTestPlans();
  }, [page, searchValue]);

  return (
    <div className="container">
      <NavHorizontal />
      <NavVertical />

      <br />
      <br />
      <br />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <input
          type="text"
          placeholder="Pesquisar por Jira ou Chamada..."
          className="search-bar"
          value={searchValue}
          onChange={handleSearchChange}
        />
      </div>

      <div className="button-container">
        <Button
          type="submit"
          className="btn btn-primary"
          onClick={() => navigate("/planoTeste/cadastrar")}
        >
          Nova UL
        </Button>
        <Button
          className="btn btn-success"
          onClick={() => setShowReportModal(true)}
        >
          Report
        </Button>
      </div>

      <ReportModal show={showReportModal} onHide={() => setShowReportModal(false)} />

      <Table striped bordered hover className="table-custom">
        <thead>
          <tr>
            <th>UL</th>
            <th>Chamado</th>
            <th>Descrição</th>
            <th>Data</th>
            <th>Sprint</th>
            <th>Tester</th>
            <th>Desenvolvedor</th>
            <th>Módulo</th>
            <th>Status</th>
            <th>Ações</th>
          </tr>
        </thead>
        <tbody>
          {results.map((item) => (
            <tr key={item.id}>
              <td>{item.jira}</td>
              <td>{item.callNumber}</td>
              <td>{item.name}</td>
              <td>{item.data}</td>
              <td>{item.deliveryData}</td>
              <td>{item.testerQAName}</td>
              <td>{item.developerName}</td>
              <td>{item.systemModuleName}</td>
              <td>
                <Button variant={getStatusColor(item.status)} className="status-button-fixed">
                  {item.status}
                </Button>
              </td>
              <td>
                <FaEdit
                  className="icon-edit"
                  style={{ color: "#0d6efd", cursor: "pointer", marginRight: "15px" }}
                  onClick={() => navigate(`/planoTeste/editar/${item.id}`)}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </Table>

      <Pagination className="pagination-custom">
        <Pagination.First onClick={() => handlePageChange(0)} disabled={page === 0} />
        <Pagination.Prev onClick={() => handlePageChange(page - 1)} disabled={page === 0} />
        {Array.from({ length: totalPages }, (_, index) => (
          <Pagination.Item key={index} active={index === page} onClick={() => handlePageChange(index)}>
            {index + 1}
          </Pagination.Item>
        ))}
        <Pagination.Next onClick={() => handlePageChange(page + 1)} disabled={page === totalPages - 1} />
        <Pagination.Last onClick={() => handlePageChange(totalPages - 1)} disabled={page === totalPages - 1} />
      </Pagination>

      {error && <p>{error}</p>}
      {loading && <p>Carregando...</p>}
    </div>
  );
};

const getStatusColor = (status: string) => {
  switch (status) {
    case "EM_PROGRESSO":
      return "outline-primary";
    case "CRIADA":
      return "outline-secondary";
    case "CONCLUIDA":
      return "outline-success";
    case "IMPEDIMENTO":
      return "outline-warning";
    case "RETORNO":
      return "outline-danger";
    default:
      return "outline-dark";
  }
};

export default TestPlanPage;