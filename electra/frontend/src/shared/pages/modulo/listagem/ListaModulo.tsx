import React, { useEffect, useState } from "react";
import { FaEdit, FaUserPlus, FaToggleOn, FaToggleOff } from "react-icons/fa";
import { useNavigate } from "react-router-dom";
import NavVertical from "../../../components/navs/vertical/NavVertical";
import NavHorizontal from "../../../components/navs/horizontal/NavHorizontal";
import "../listagem/styless.css";
import api from "../../../../api/axiosConfig"; 

interface Module {
  id: number;
  name: string;
  active: boolean;
}

const ListagemModuloPage = () => {
  const [modulos, setModulos] = useState<Module[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [termoPesquisa, setTermoPesquisa] = useState("");
  const navigate = useNavigate();

  // Buscar módulos da API
  const buscarModulos = async () => {
    try {
      const response = await api.get("modules");
      if (response.status === 200 && Array.isArray(response.data)) {
        setModulos(response.data); // Define a lista de módulos
      } else {
        setModulos([]); // Se não for array, define lista vazia
      }
    } catch (error) {
      console.error("Erro ao carregar módulos:", error);
      setErro("Erro ao carregar módulos. Verifique o console para mais detalhes.");
    } finally {
      setCarregando(false); // Finaliza carregamento mesmo em caso de erro
    }
  };

  // Filtrar módulos com base no termo de pesquisa
  const modulosFiltrados = modulos.filter((modulo) => {
    const termos = termoPesquisa
      .toLowerCase()
      .split(",")
      .map((termo) => termo.trim())
      .filter((termo) => termo.length > 0);
    return termos.every(
      (termo) =>
        modulo.name.toLowerCase().includes(termo) ||
        String(modulo.id).includes(termo) ||
        (termo === "ativo" && modulo.active) ||
        (termo === "inativo" && !modulo.active)
    );
  });

  useEffect(() => {
    buscarModulos();
  }, []);

  // Função para ativar/inativar módulo
  const inativarModulo = async (id: number, active: boolean) => {
    if (!id || id <= 0) {
      alert("ID inválido para alteração de status.");
      return;
    }
    if (window.confirm("Tem certeza que deseja alterar o status deste módulo?")) {
      try {
        const novoStatus = !active;
        const response = await api.patch(
          `modules/${id}`,
          { active: novoStatus } // Corpo da requisição com o novo status
        );
        console.log("Resposta da alteração de status:", response.data);
        // Atualiza o estado dos módulos localmente
        setModulos((prevModulos) =>
          prevModulos.map((modulo) =>
            modulo.id === id ? { ...modulo, active: novoStatus } : modulo
          )
        );
        alert(`Módulo ${novoStatus ? "ativado" : "inativado"} com sucesso.`);
      } catch (error) {
        console.error("Erro ao alterar status do módulo:", error);
        alert("Ocorreu um erro ao alterar o status do módulo.");
      }
    }
  };

  // Função para editar módulo
  const editarModulo = (id: number) => {
    navigate(`/modulo/editar/${id}`);
  };

  if (carregando) return <div>Carregando...</div>;
  if (erro) return <div>{erro}</div>;

  return (
    <>
      <NavHorizontal />
      <NavVertical />
      <br />
      <br />
      <br />
      <div className="container mt-4">
        <h1>Módulos</h1>
        {/* Campo de Pesquisa */}
        <input
          type="text"
          placeholder="Pesquisar por nome ou status (ativo/inativo)"
          value={termoPesquisa}
          onChange={(e) => setTermoPesquisa(e.target.value)}
          className="search-bar"
        />
        {/* Botão Novo Módulo */}
        <button
          className="btn btn-primary mb-3 btn-novo-usuario"
          onClick={() => navigate("/modulo/cadastrar")}
        >
          <FaUserPlus /> Novo Módulo
        </button>
        {/* Tabela de Módulos */}
        <table className="table table-striped">
          <thead>
            <tr>
              <th>ID</th>
              <th>Nome</th>
              <th>Status</th>
              <th>Ações</th>
            </tr>
          </thead>
          <tbody>
            {modulosFiltrados.length === 0 ? (
              <tr>
                <td colSpan={4} className="text-center">
                  Nenhum módulo encontrado.
                </td>
              </tr>
            ) : (
              modulosFiltrados.map((modulo) => (
                <tr key={modulo.id}>
                  <td>{modulo.id}</td>
                  <td>{modulo.name}</td>
                  <td>{modulo.active ? "Ativo" : "Inativo"}</td>
                  <td>
                    <div className="action-icons">
                      <FaEdit
                        className="edit-icon"
                        onClick={() => editarModulo(modulo.id)}
                      />
                      {modulo.active ? (
                        <FaToggleOn
                          className="active-icon"
                          onClick={() => inativarModulo(modulo.id, modulo.active)}
                        />
                      ) : (
                        <FaToggleOff
                          className="inactive-icon"
                          onClick={() => inativarModulo(modulo.id, modulo.active)}
                        />
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
};

export default ListagemModuloPage;