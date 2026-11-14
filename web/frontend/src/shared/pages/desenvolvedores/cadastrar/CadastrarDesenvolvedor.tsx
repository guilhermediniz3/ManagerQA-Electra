import React, { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { Toast, ToastContainer } from "react-bootstrap";
import "./styless.css";
import NavHorizontal from "../../../components/navs/horizontal/NavHorizontal";
import NavVertical from "../../../components/navs/vertical/NavVertical";
import api from "../../../../api/axiosConfig";

interface Technology {
  id: number;
  name: string;
  active?: boolean;
}

interface Developer {
  id?: number;
  name: string;
  active?: boolean;
  technologyIds: number[];
}

const CriarDesenvolvedor = () => {
  const navigate = useNavigate();
  const [developer, setDeveloper] = useState<Developer>({ 
    name: "", 
    active: true, 
    technologyIds: [] 
  });
  const [technologies, setTechnologies] = useState<Technology[]>([]);
  const [showSuccessToast, setShowSuccessToast] = useState(false);
  const [showErrorToast, setShowErrorToast] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  // Buscar tecnologias disponíveis
  useEffect(() => {
    const fetchTechnologies = async () => {
      try {
        const response = await api.get("technologies");
        setTechnologies(response.data);
      } catch (error) {
        console.error("Erro ao buscar tecnologias:", error);
      }
    };
    fetchTechnologies();
  }, []);

  // Seleciona apenas uma tecnologia (nova implementação)
  const selectTechnology = (techId: number) => {
    // Se já está selecionada, desmarca
    if (developer.technologyIds.includes(techId)) {
      setDeveloper({
        ...developer,
        technologyIds: []
      });
    } else {
      // Senão, seleciona apenas essa tecnologia
      setDeveloper({
        ...developer,
        technologyIds: [techId] // Array com apenas um elemento
      });
    }
  };

  // Verifica se uma tecnologia está selecionada
  const isTechnologySelected = (techId: number) => {
    return developer.technologyIds.includes(techId);
  };

  // Verifica se há alguma tecnologia selecionada
  const hasSelectedTechnology = developer.technologyIds.length > 0;

  // Criar desenvolvedor
  const criarDesenvolvedor = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    try {
      await api.post("developers", developer);
      setSuccessMessage("Desenvolvedor criado com sucesso!");
      setShowSuccessToast(true);
      setTimeout(() => navigate("/desenvolvedores/listagem"), 2000);
    } catch (error) {
      setErrorMessage("Erro ao criar Desenvolvedor.");
      setShowErrorToast(true);
    }
  };

  return (
    <>
      <NavHorizontal />
      <NavVertical />
      <br/>
      <br/>
      <br/>
      <div className="container mt-4">
        <h1>Criar Desenvolvedor</h1>
        <form onSubmit={criarDesenvolvedor}>
          <div className="mb-3">
            <label htmlFor="name" className="form-label">Nome</label>
            <input
              type="text"
              className="form-control"
              id="name"
              value={developer.name}
              onChange={(e) => setDeveloper({ ...developer, name: e.target.value })}
              required
            />
          </div>

          <div className="mb-3">
            <label className="form-label">Status</label>
            <div className="form-check">
              <input
                type="checkbox"
                className="form-check-input"
                id="active"
                checked={developer.active || false}
                onChange={(e) => setDeveloper({ ...developer, active: e.target.checked })}
              />
              <label className="form-check-label" htmlFor="active">
                Ativo
              </label>
            </div>
          </div>

          <div className="mb-3">
            <label className="form-label">Tecnologias</label>
            <div className="technology-list">
              {technologies.map((tech) => (
                <button
                  key={tech.id}
                  type="button"
                  className={`technology-item ${
                    isTechnologySelected(tech.id) ? "selected" : ""
                  } ${
                    tech.active === false || (hasSelectedTechnology && !isTechnologySelected(tech.id)) ? "disabled" : ""
                  }`}
                  onClick={() => tech.active !== false && selectTechnology(tech.id)}
                  disabled={tech.active === false || (hasSelectedTechnology && !isTechnologySelected(tech.id))}
                >
                  {tech.name}
                </button>
              ))}
            </div>
          </div>
          
          <div className="d-flex justify-content-end gap-2">
            <button 
              type="button" 
              className="btn btn-secondary" 
              onClick={() => navigate("/desenvolvedores/listagem")}
            >
              Voltar
            </button>
            <button 
              type="submit" 
              className="btn btn-primary"
              disabled={!developer.name.trim() || developer.technologyIds.length === 0}
            >
              Criar
            </button>
          </div>
        </form>

        <ToastContainer position="top-end" className="p-3">
          <Toast 
            onClose={() => setShowSuccessToast(false)} 
            show={showSuccessToast} 
            delay={3000} 
            autohide 
            bg="success"
          >
            <Toast.Header>
              <strong className="me-auto">Sucesso</strong>
              <small>Agora</small>
            </Toast.Header>
            <Toast.Body>{successMessage}</Toast.Body>
          </Toast>
          <Toast 
            onClose={() => setShowErrorToast(false)} 
            show={showErrorToast} 
            delay={3000} 
            autohide 
            bg="danger"
          >
            <Toast.Header>
              <strong className="me-auto">Erro</strong>
              <small>Agora</small>
            </Toast.Header>
            <Toast.Body>{errorMessage}</Toast.Body>
          </Toast>
        </ToastContainer>
      </div>
    </>
  );
};

export default CriarDesenvolvedor;