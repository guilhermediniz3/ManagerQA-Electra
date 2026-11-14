import React, { useState, useEffect } from "react";
import { useNavigate, useParams } from "react-router-dom";
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

const EditarDesenvolvedor = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [developer, setDeveloper] = useState<Developer>({
    name: "",
    active: true,
    technologyIds: [],
  });
  const [technologies, setTechnologies] = useState<Technology[]>([]);
  const [showSuccessToast, setShowSuccessToast] = useState(false);
  const [showErrorToast, setShowErrorToast] = useState(false);
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  // Buscar dados do desenvolvedor e tecnologias
  useEffect(() => {
    const fetchTechnologiesAndDeveloper = async () => {
      try {
        const [techResponse, devResponse] = await Promise.all([
          api.get("technologies"),
          api.get(`developers/${id}`)
        ]);

        setTechnologies(techResponse.data);
        
        const devData = devResponse.data;
        setDeveloper({ 
          ...devData, 
          name: devData.name !== null ? devData.name : "",
          // Garante que só a primeira tecnologia seja mantida (caso exista)
          technologyIds: devData.technologyIds?.length > 0 ? [devData.technologyIds[0]] : []
        });
      } catch (error) {
        console.error("Erro ao buscar dados:", error);
      }
    };
    fetchTechnologiesAndDeveloper();
  }, [id]);

  // Alterna a seleção de tecnologias (agora seleciona apenas uma)
  const toggleTechnology = (tech: Technology) => {
    const isSelected = developer.technologyIds.includes(tech.id);
    setDeveloper({
      ...developer,
      technologyIds: isSelected ? [] : [tech.id], // Sempre mantém apenas 0 ou 1 tecnologia
    });
  };

  // Verifica se há alguma tecnologia selecionada
  const hasSelectedTechnology = developer.technologyIds.length > 0;

  // Atualizar desenvolvedor
  const updateDeveloper = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();

    const payload: Partial<Developer> = {
      active: developer.active,
      technologyIds: developer.technologyIds,
    };
    
    if (developer.name && developer.name.trim() !== "") {
      payload.name = developer.name;
    }

    try {
      await api.patch(`developers/${id}`, payload);
      setSuccessMessage("Desenvolvedor atualizado com sucesso!");
      setShowSuccessToast(true);
    } catch (error) {
      setErrorMessage("Erro ao atualizar Desenvolvedor.");
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
        <h1>Editar Desenvolvedor</h1>
        <form onSubmit={updateDeveloper}>
          <div className="mb-3">
            <label htmlFor="name" className="form-label">Nome</label>
            <input
              type="text"
              className="form-control"
              id="name"
              value={developer.name || ""}
              onChange={(e) => setDeveloper({ ...developer, name: e.target.value })}
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
                  className={`technology-item ${developer.technologyIds.includes(tech.id) ? "selected" : ""} ${
                    tech.active === false || (hasSelectedTechnology && !developer.technologyIds.includes(tech.id)) ? "disabled" : ""
                  }`}
                  onClick={() => tech.active !== false && toggleTechnology(tech)}
                  disabled={tech.active === false || (hasSelectedTechnology && !developer.technologyIds.includes(tech.id))}
                >
                  {tech.name}
                </button>
              ))}
            </div>
          </div>
          <div className="d-flex justify-content-between gap-2">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => navigate("/desenvolvedores/listagem")}
            >
              Voltar
            </button>
            <button
              type="submit"
              className="btn btn-success"
              style={{ backgroundColor: "#28a745", borderColor: "#28a745" }}
            >
              Salvar
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

export default EditarDesenvolvedor;