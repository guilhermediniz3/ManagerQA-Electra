import React, { useState, useEffect } from "react";
import { Modal, Button, Form } from "react-bootstrap";
import DatePicker from "react-datepicker";
import "react-datepicker/dist/react-datepicker.css";
import { FaFileExcel, FaFilePdf } from "react-icons/fa";
import api from "../../../api/axiosConfig";

interface Tester {
  id: number;
  name: string;
}

interface ReportModalProps {
  show: boolean;
  onHide: () => void;
}

const ReportModal: React.FC<ReportModalProps> = ({ show, onHide }) => {
  const [reportDate, setReportDate] = useState<Date | null>(null);
  const [selectedTester, setSelectedTester] = useState<string>("");
  const [testers, setTesters] = useState<Tester[]>([]);

  useEffect(() => {
    const fetchTesters = async () => {
      try {
        const response = await api.get("/testers");
        setTesters(response.data);
      } catch (error) {
        console.error("Erro ao buscar testers:", error);
      }
    };
    if (show) fetchTesters();
  }, [show]);

  // Excel (ajustado para não enviar testerName quando for "Todos os Testers")
  const handleGenerateExcel = async () => {
    try {
      const params: Record<string, string> = {
        data: reportDate?.toISOString().split('T')[0] || ''
      };
      
      if (selectedTester) {
        params.testerName = selectedTester;
      }
      
      window.open(
        `${api.defaults.baseURL}/relatorios/excel?${new URLSearchParams(params)}`,
        "_blank"
      );
    } catch (error) {
      console.error("Erro ao gerar Excel:", error);
      alert("Erro ao gerar Excel");
    }
  };

  // PDF (ajustado para não enviar testerName quando for "Todos os Testers")
  const handleGeneratePdf = async () => {
    if (!reportDate) {
      alert("Por favor, selecione uma data");
      return;
    }

    try {
      const params: Record<string, string> = {
        data: reportDate.toISOString().split('T')[0]
      };
      
      if (selectedTester) {
        params.testerName = selectedTester;
      }
      
      window.open(
        `${api.defaults.baseURL}/relatorios/pdf?${new URLSearchParams(params)}`,
        "_blank"
      );
    } catch (error) {
      console.error("Erro ao gerar PDF:", error);
      alert("Erro ao gerar PDF");
    }
  };

  return (
    <Modal show={show} onHide={onHide}>
      <Modal.Header closeButton>
        <Modal.Title>Gerar Relatório</Modal.Title>
      </Modal.Header>
      <Modal.Body>
        <Form>
          <Form.Group className="mb-3">
            <Form.Label>Data</Form.Label>
            <DatePicker
              selected={reportDate}
              onChange={(date: Date | null) => setReportDate(date)}
              dateFormat="dd/MM/yyyy"
              className="form-control"
              placeholderText="Selecione a data"
            />
          </Form.Group>

          <Form.Group className="mb-3">
            <Form.Label>Tester</Form.Label>
            <Form.Select
              value={selectedTester}
              onChange={(e) => setSelectedTester(e.target.value)}
            >
              <option value="">Todos os Testers</option>
              {testers.map((tester) => (
                <option key={tester.id} value={tester.name}>
                  {tester.name}
                </option>
              ))}
            </Form.Select>
          </Form.Group>
        </Form>
      </Modal.Body>
      <Modal.Footer>
        <Button variant="success" onClick={handleGenerateExcel}>
          <FaFileExcel style={{ marginRight: "8px" }} />
          Gerar Excel
        </Button>

        <Button variant="danger" onClick={handleGeneratePdf}>
          <FaFilePdf style={{ marginRight: "8px" }} />
          Gerar PDF
        </Button>

        <Button variant="secondary" onClick={onHide}>
          Fechar
        </Button>
      </Modal.Footer>
    </Modal>
  );
};

export default ReportModal;