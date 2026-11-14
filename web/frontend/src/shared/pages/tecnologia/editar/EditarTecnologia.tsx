import React, { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { FaToggleOn, FaToggleOff } from "react-icons/fa";
import "./styless.css";
import NavHorizontal from "../../../components/navs/horizontal/NavHorizontal";
import NavVertical from "../../../components/navs/vertical/NavVertical";
import api from "../../../../api/axiosConfig";

interface Modulo {
  id: number;
  name: string;
  email: string;
  active: boolean;
}

const EditarTecnologiaPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [modulo, setModulo] = useState<Modulo | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [mensagemSucesso, setMensagemSucesso] = useState<string | null>(null);

  useEffect(() => {
    const buscarTecmologia = async () => {
      try {
        const response = await api.get(`/technologies/${id}`);
        setModulo(response.data);
        setCarregando(false);
      } catch (error) {
        setErro("Erro ao carregar tecnologia.");
        setCarregando(false);
      }
    };
    buscarTecmologia();
  }, [id]);

  const salvarAlteracoes = async () => {
    if (!modulo) return;
    try {
      await api.patch(`/technologies/${id}`, modulo);
      setMensagemSucesso("tecnologia atualizado com sucesso!");
    } catch (error) {
      console.error("Erro ao atualizar tecnologia:", error);
      alert("Erro ao atualizar tecnologia.");
    }
  };

  const voltarParaListagem = () => {
    navigate("/tecnologia/listagem");
  };

  if (carregando) return <div>Carregando...</div>;
  if (erro) return <div>{erro}</div>;
  if (!modulo) return <div>tecnologia não encontrado.</div>;

  return (
    <>
      <NavHorizontal />
      <NavVertical />
      <br />
      <br />
      <br />

      <div className="container mt-4">
        <h1>Editar Tecnologia</h1>

        {mensagemSucesso && (
          <div className="alert alert-success" role="alert">
            {mensagemSucesso}
          </div>
        )}

        <form
          onSubmit={(e) => {
            e.preventDefault();
            salvarAlteracoes();
          }}
        >
          <div className="mb-3">
            <label htmlFor="name" className="form-label">Nome</label>
            <input
              type="text"
              className="form-control"
              id="name"
              value={modulo.name}
              onChange={(e) => setModulo({ ...modulo, name: e.target.value })}
            />
          </div>

          <div className="mb-3 form-check status-icons">
            <label className="form-check-label me-2">Status:</label>
            {modulo.active ? (
              <FaToggleOn
                style={{ color: "green", cursor: "pointer", fontSize: "24px" }}
                onClick={() => setModulo({ ...modulo, active: false })}
              />
            ) : (
              <FaToggleOff
                style={{ color: "gray", cursor: "pointer", fontSize: "24px" }}
                onClick={() => setModulo({ ...modulo, active: true })}
              />
            )}
          </div>

          <div className="d-flex justify-content-end gap-2">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={voltarParaListagem}
            >
              Voltar
            </button>
            <button type="submit" className="btn btn-primary">
              Salvar
            </button>
          </div>
        </form>
      </div>
    </>
  );
};

export default EditarTecnologiaPage;