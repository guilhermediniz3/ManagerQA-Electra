"use strict";
/**
 * A ponte entre a interface e o aplicativo.
 *
 * A interface roda como uma página normal servida pelo backend local, e o
 * contextIsolation continua ativo: nada do Node é exposto ao conteúdo. O que
 * atravessa é só o que a página não tem como fazer sozinha — escolher uma pasta
 * do computador, que nenhum navegador entrega por questão de segurança e que o
 * RunManager precisa para saber onde fica o projeto de automação.
 *
 * A página verifica se isto existe antes de usar: no navegador não existe, e lá
 * o caminho continua sendo digitado à mão.
 */
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("managerDesktop", {
  /** Abre o seletor de pastas e devolve o caminho, ou null se cancelar. */
  escolherPasta: (inicial) => ipcRenderer.invoke("escolher-pasta", inicial),

  /**
   * Abre um arquivo no programa padrão do sistema.
   *
   * É o que permite assistir ao vídeo da falha sem depender do Vimeo: o arquivo
   * está no disco desde que o teste rodou, e mandar para a nuvem só para poder
   * vê-lo seria caminho longo demais.
   */
  abrirArquivo: (caminho) => ipcRenderer.invoke("abrir-arquivo", caminho),
});
