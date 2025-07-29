// IMPORTANTE:
// Este ficheiro deve estar dentro de uma estrutura de pastas que corresponda ao pacote.
// Ex: seu-projeto/src/com/example/DeadlockApp.java
package com.example;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DeadlockApp extends Application {

    // --- Constantes e Variáveis Globais ---
    private static final int MAX_PROCESSOS = 10;
    private static final int MAX_RECURSOS = 10;

    // --- Estruturas de Dados para Controle do Sistema ---
    private final List<Recurso> tiposRecurso = new ArrayList<>();
    private final Map<Integer, Processo> processosAtivos = new ConcurrentHashMap<>();
    private final Lock lockSistema = new ReentrantLock(true); // Lock para garantir acesso seguro às estruturas de dados

    // Matrizes para o algoritmo de detecção de deadlock
    private int[][] alocacao = new int[MAX_PROCESSOS][MAX_RECURSOS];
    private int[][] requisicao = new int[MAX_PROCESSOS][MAX_RECURSOS];
    private int[] disponivel;

    // --- Componentes da Interface Gráfica ---
    private final TextArea logArea = new TextArea();
    private final ListView<String> processosStatusList = new ListView<>();
    private final ListView<String> recursosStatusList = new ListView<>();
    private final Label deadlockStatusLabel = new Label("Status Deadlock: Nenhum deadlock detectado.");

    // --- Controle de Threads ---
    private ExecutorService executorProcessos = Executors.newFixedThreadPool(MAX_PROCESSOS);
    private SistemaOperacional so;
    private Thread threadSO;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Simulador de Detecção de Deadlock");

        // Layout principal
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        // --- Painel de Configuração (Topo) ---
        VBox configBox = criarPainelConfiguracao();
        root.setTop(configBox);

        // --- Painel de Visualização (Centro) ---
        GridPane statusPane = criarPainelStatus();
        root.setCenter(statusPane);

        // --- Painel de Log (Baixo) ---
        VBox logBox = new VBox(5, new Label("Log de Operações:"), logArea);
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        root.setBottom(logBox);

        Scene scene = new Scene(root, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            // Garante que todas as threads sejam encerradas ao fechar a janela
            if (executorProcessos != null) {
                executorProcessos.shutdownNow();
            }
            if (threadSO != null) {
                threadSO.interrupt();
            }
        });
        primaryStage.show();
    }

    /**
     * Cria o painel de configuração para adicionar recursos e processos.
     */
    private VBox criarPainelConfiguracao() {
        // --- Seção de Recursos ---
        GridPane resourceGrid = new GridPane();
        resourceGrid.setHgap(10);
        resourceGrid.setVgap(5);
        resourceGrid.setPadding(new Insets(10));
        resourceGrid.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, new CornerRadii(5), BorderWidths.DEFAULT)));

        TextField nomeRecursoField = new TextField();
        nomeRecursoField.setPromptText("Ex: Impressora");
        TextField idRecursoField = new TextField();
        idRecursoField.setPromptText("Ex: 1");
        TextField qtdRecursoField = new TextField();
        qtdRecursoField.setPromptText("Ex: 3");
        Button addRecursoBtn = new Button("Adicionar Recurso");

        resourceGrid.add(new Label("Nome do Recurso:"), 0, 0);
        resourceGrid.add(nomeRecursoField, 1, 0);
        resourceGrid.add(new Label("ID do Recurso:"), 0, 1);
        resourceGrid.add(idRecursoField, 1, 1);
        resourceGrid.add(new Label("Quantidade:"), 0, 2);
        resourceGrid.add(qtdRecursoField, 1, 2);
        resourceGrid.add(addRecursoBtn, 1, 3);

        // --- Seção de Processos ---
        GridPane processGrid = new GridPane();
        processGrid.setHgap(10);
        processGrid.setVgap(5);
        processGrid.setPadding(new Insets(10));
        processGrid.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, new CornerRadii(5), BorderWidths.DEFAULT)));

        TextField idProcessoField = new TextField();
        idProcessoField.setPromptText("Ex: 1 (1-" + MAX_PROCESSOS + ")");
        TextField tempoSolicitacaoField = new TextField();
        tempoSolicitacaoField.setPromptText("ΔTs (segundos)");
        TextField tempoUtilizacaoField = new TextField();
        tempoUtilizacaoField.setPromptText("ΔTu (segundos)");
        Button addProcessoBtn = new Button("Criar Processo");
        Button removerProcessoBtn = new Button("Remover Processo");

        processGrid.add(new Label("ID do Processo:"), 0, 0);
        processGrid.add(idProcessoField, 1, 0);
        processGrid.add(new Label("Tempo de Solicitação (s):"), 0, 1);
        processGrid.add(tempoSolicitacaoField, 1, 1);
        processGrid.add(new Label("Tempo de Utilização (s):"), 0, 2);
        processGrid.add(tempoUtilizacaoField, 1, 2);
        processGrid.add(addProcessoBtn, 1, 3);
        processGrid.add(removerProcessoBtn, 2, 3);

        // --- Seção de Controle do SO ---
        GridPane soGrid = new GridPane();
        soGrid.setHgap(10);
        soGrid.setVgap(5);
        soGrid.setPadding(new Insets(10));
        soGrid.setBorder(new Border(new BorderStroke(null, BorderStrokeStyle.SOLID, new CornerRadii(5), BorderWidths.DEFAULT)));

        TextField tempoVerificacaoField = new TextField();
        tempoVerificacaoField.setPromptText("Δt (segundos)");
        Button iniciarSimulacaoBtn = new Button("Iniciar Simulação");
        Button forcarDeadlockBtn = new Button("Criar Exemplo de Deadlock");

        soGrid.add(new Label("Intervalo de Verificação SO (s):"), 0, 0);
        soGrid.add(tempoVerificacaoField, 1, 0);
        soGrid.add(iniciarSimulacaoBtn, 1, 1);
        soGrid.add(forcarDeadlockBtn, 2, 1);

        // --- Ações dos Botões ---
        addRecursoBtn.setOnAction(e -> adicionarRecurso(nomeRecursoField.getText(), idRecursoField.getText(), qtdRecursoField.getText()));
        addProcessoBtn.setOnAction(e -> adicionarProcesso(idProcessoField.getText(), tempoSolicitacaoField.getText(), tempoUtilizacaoField.getText()));
        removerProcessoBtn.setOnAction(e -> removerProcesso(idProcessoField.getText()));
        iniciarSimulacaoBtn.setOnAction(e -> iniciarSimulacao(tempoVerificacaoField.getText()));
        forcarDeadlockBtn.setOnAction(e -> forcarDeadlock());

        // Agrupando seções de configuração
        HBox configHBox = new HBox(20, resourceGrid, processGrid, soGrid);
        configHBox.setAlignment(Pos.CENTER);
        
        VBox configVBox = new VBox(10, new Label("Configuração do Sistema"), configHBox);
        configVBox.setAlignment(Pos.CENTER);
        configVBox.setPadding(new Insets(10));
        
        return configVBox;
    }

    /**
     * Cria o painel que exibe o status dos processos e recursos.
     */
    private GridPane criarPainelStatus() {
        GridPane statusPane = new GridPane();
        statusPane.setHgap(10);
        statusPane.setVgap(10);
        statusPane.setPadding(new Insets(20, 0, 20, 0));

        VBox processosBox = new VBox(5, new Label("Status dos Processos"), processosStatusList);
        VBox recursosBox = new VBox(5, new Label("Status dos Recursos"), recursosStatusList);

        statusPane.add(processosBox, 0, 0);
        statusPane.add(recursosBox, 1, 0);
        statusPane.add(deadlockStatusLabel, 0, 1, 2, 1);

        GridPane.setHgrow(processosBox, Priority.ALWAYS);
        GridPane.setHgrow(recursosBox, Priority.ALWAYS);
        
        return statusPane;
    }

    // --- Lógica de Negócio ---

    private void adicionarRecurso(String nome, String idStr, String qtdStr) {
        if (so != null && so.isAlive()) {
            log("ERRO: Não é possível adicionar recursos após o início da simulação.");
            return;
        }
        try {
            int id = Integer.parseInt(idStr);
            int quantidade = Integer.parseInt(qtdStr);

            if (nome.isEmpty() || tiposRecurso.size() >= MAX_RECURSOS) {
                log("ERRO: Nome do recurso inválido ou limite de tipos de recurso atingido.");
                return;
            }
            if (tiposRecurso.stream().anyMatch(r -> r.id == id || r.nome.equalsIgnoreCase(nome))) {
                log("ERRO: ID ou nome de recurso já existe.");
                return;
            }

            Recurso novoRecurso = new Recurso(nome, id, quantidade);
            tiposRecurso.add(novoRecurso);
            // Ordena para manter consistência dos índices
            tiposRecurso.sort(Comparator.comparingInt(r -> r.id)); 
            log("INFO: Recurso '" + nome + "' (ID: " + id + ", Qtd: " + quantidade + ") adicionado.");
            atualizarStatusRecursos();

        } catch (NumberFormatException ex) {
            log("ERRO: ID e Quantidade do recurso devem ser números inteiros.");
        }
    }

    private void adicionarProcesso(String idStr, String tsStr, String tuStr) {
        if (tiposRecurso.isEmpty()) {
            log("ERRO: Adicione pelo menos um tipo de recurso antes de criar um processo.");
            return;
        }
        if (so != null && so.isAlive()) {
             log("ERRO: Não é possível adicionar processos após o início da simulação.");
            return;
        }

        try {
            int id = Integer.parseInt(idStr);
            long ts = Long.parseLong(tsStr);
            long tu = Long.parseLong(tuStr);

            if (id <= 0 || id > MAX_PROCESSOS) {
                log("ERRO: ID do processo deve ser entre 1 e " + MAX_PROCESSOS + ".");
                return;
            }
            if (processosAtivos.containsKey(id)) {
                log("ERRO: Processo com ID " + id + " já existe.");
                return;
            }

            Processo p = new Processo(id, ts, tu, this);
            processosAtivos.put(id, p);
            log("INFO: Processo " + id + " criado.");
            atualizarStatusProcessos();

        } catch (NumberFormatException ex) {
            log("ERRO: ID, ΔTs e ΔTu do processo devem ser números.");
        }
    }
    
    private void removerProcesso(String idStr) {
        try {
            int id = Integer.parseInt(idStr);
            Processo p = processosAtivos.remove(id);

            if (p != null) {
                p.parar(); // Sinaliza para a thread parar
                liberarRecursosDeProcesso(p.idProcesso);
                log("INFO: Processo " + id + " removido e seus recursos foram liberados.");
                atualizarStatusProcessos();
                atualizarStatusRecursos();
            } else {
                log("ERRO: Processo com ID " + id + " não encontrado.");
            }
        } catch (NumberFormatException ex) {
            log("ERRO: ID do processo deve ser um número.");
        }
    }

    private void iniciarSimulacao(String dtStr) {
        if (tiposRecurso.isEmpty()) {
            log("ERRO: Adicione recursos antes de iniciar.");
            return;
        }
        if (processosAtivos.isEmpty()) {
            log("ERRO: Adicione processos antes de iniciar.");
            return;
        }
        if (so != null && so.isAlive()) {
             log("ERRO: Simulação já iniciada.");
            return;
        }

        try {
            long dt = Long.parseLong(dtStr);

            // Inicializa o vetor de recursos disponíveis se ainda não foi
            if (disponivel == null) {
                disponivel = new int[tiposRecurso.size()];
                for (int i = 0; i < tiposRecurso.size(); i++) {
                    disponivel[i] = tiposRecurso.get(i).quantidadeTotal;
                }
            }

            // Inicia a thread do Sistema Operacional
            so = new SistemaOperacional(dt, this);
            threadSO = new Thread(so);
            threadSO.setDaemon(true); // Permite que a JVM feche mesmo que a thread esteja rodando
            threadSO.start();

            // Inicia as threads dos processos
            executorProcessos = Executors.newFixedThreadPool(MAX_PROCESSOS);
            processosAtivos.values().forEach(executorProcessos::submit);
            
            log("INFO: Simulação iniciada. Verificação de deadlock a cada " + dt + " segundos.");
            atualizarStatusRecursos(); // Atualiza a UI para mostrar os recursos disponíveis

        } catch (NumberFormatException ex) {
            log("ERRO: Intervalo de verificação (Δt) deve ser um número.");
        }
    }

    /**
     * Configura um cenário clássico de deadlock para demonstração.
     */
    private void forcarDeadlock() {
        if (so != null && so.isAlive()) {
            log("ERRO: Não é possível forçar deadlock com a simulação em andamento.");
            return;
        }

        // 1. Limpar estado anterior
        tiposRecurso.clear();
        processosAtivos.clear();
        alocacao = new int[MAX_PROCESSOS][MAX_RECURSOS];
        requisicao = new int[MAX_PROCESSOS][MAX_RECURSOS];
        disponivel = null; // Reseta o array de disponíveis
        log("INFO: Configuração reiniciada para exemplo de deadlock.");

        // 2. Criar recursos (2 recursos, 1 instância cada)
        adicionarRecurso("Impressora", "1", "1");
        adicionarRecurso("Scanner", "2", "1");

        // 3. Criar processos
        adicionarProcesso("1", "999", "999"); // Tempos altos para não executarem sozinhos
        adicionarProcesso("2", "999", "999");

        // 4. Configurar manualmente o estado de deadlock
        lockSistema.lock();
        try {
            // Inicializa o 'disponivel' para o setup manual
            disponivel = new int[tiposRecurso.size()];
            for (int i = 0; i < tiposRecurso.size(); i++) {
                disponivel[i] = tiposRecurso.get(i).quantidadeTotal;
            }

            // P1 (ID 1, index 0) aloca R1 (Impressora, index 0)
            alocacao[0][0] = 1;
            disponivel[0]--;

            // P2 (ID 2, index 1) aloca R2 (Scanner, index 1)
            alocacao[1][1] = 1;
            disponivel[1]--;

            // P1 requisita R2
            requisicao[0][1] = 1;
            processosAtivos.get(1).bloquear(1); // Bloqueia P1 esperando pelo recurso de índice 1

            // P2 requisita R1
            requisicao[1][0] = 1;
            processosAtivos.get(2).bloquear(0); // Bloqueia P2 esperando pelo recurso de índice 0

            log("INFO: Estado de deadlock forçado manualmente.");
            log("INFO: P1 alocou Impressora e espera por Scanner.");
            log("INFO: P2 alocou Scanner e espera por Impressora.");
            log("INFO: Clique em 'Iniciar Simulação' para que o SO detecte o deadlock.");

        } finally {
            lockSistema.unlock();
        }

        // 5. Atualizar a UI
        atualizarTodosStatus();
    }


    // --- Métodos de Sincronização e Comunicação ---

    public void solicitarRecurso(int idProcesso, int indiceRecurso) {
        lockSistema.lock();
        try {
            log("PROCESSO " + idProcesso + " está a solicitar " + getNomeRecurso(indiceRecurso));
            // O processo 'idProcesso' quer 1 instância do recurso 'indiceRecurso'
            if (disponivel[indiceRecurso] > 0) {
                disponivel[indiceRecurso]--;
                alocacao[idProcesso - 1][indiceRecurso]++;
                log("PROCESSO " + idProcesso + " alocou o recurso " + getNomeRecurso(indiceRecurso));
            } else {
                // Se não há recurso disponível, o processo bloqueia
                requisicao[idProcesso - 1][indiceRecurso]++;
                Processo p = processosAtivos.get(idProcesso);
                if (p != null) {
                    p.bloquear(indiceRecurso);
                    log("PROCESSO " + idProcesso + " bloqueado esperando por " + getNomeRecurso(indiceRecurso));
                }
            }
            atualizarTodosStatus();
        } finally {
            lockSistema.unlock();
        }
    }

    public void liberarRecurso(int idProcesso, int indiceRecurso) {
        lockSistema.lock();
        try {
            if (alocacao[idProcesso - 1][indiceRecurso] > 0) {
                alocacao[idProcesso - 1][indiceRecurso]--;
                disponivel[indiceRecurso]++;
                log("PROCESSO " + idProcesso + " liberou o recurso " + getNomeRecurso(indiceRecurso));

                // Acorda processos que possam estar esperando por este recurso
                acordarProcessos(indiceRecurso);
                atualizarTodosStatus();
            }
        } finally {
            lockSistema.unlock();
        }
    }
    
    private void liberarRecursosDeProcesso(int idProcesso) {
        lockSistema.lock();
        try {
            for (int i = 0; i < tiposRecurso.size(); i++) {
                if (alocacao[idProcesso - 1][i] > 0) {
                    disponivel[i] += alocacao[idProcesso - 1][i];
                    alocacao[idProcesso - 1][i] = 0;
                    acordarProcessos(i);
                }
                requisicao[idProcesso - 1][i] = 0;
            }
        } finally {
            lockSistema.unlock();
        }
    }

    private void acordarProcessos(int indiceRecurso) {
        // Itera sobre todos os processos para ver se algum estava esperando pelo recurso liberado
        for (int i = 0; i < MAX_PROCESSOS; i++) {
            if (requisicao[i][indiceRecurso] > 0) {
                Processo p = processosAtivos.get(i + 1);
                if (p != null && p.getStatus() == StatusProcesso.BLOQUEADO && disponivel[indiceRecurso] > 0) {
                    // Tenta alocar o recurso para o processo que estava bloqueado
                    disponivel[indiceRecurso]--;
                    alocacao[i][indiceRecurso]++;
                    requisicao[i][indiceRecurso]--; // A requisição foi atendida
                    p.acordar();
                    log("PROCESSO " + p.idProcesso + " foi acordado e alocou " + getNomeRecurso(indiceRecurso));
                }
            }
        }
    }
    
    public void detectarDeadlock() {
        lockSistema.lock();
        try {
            int numProcessos = processosAtivos.size();
            int numRecursos = tiposRecurso.size();
            
            if (numProcessos == 0) return;

            // Algoritmo de Detecção de Deadlock (baseado no Algoritmo do Banqueiro)
            int[] trabalho = Arrays.copyOf(disponivel, numRecursos);
            boolean[] finalizar = new boolean[MAX_PROCESSOS];
            // Inicialmente, considera processos que não estão alocando nada como finalizados
            for (int i = 0; i < MAX_PROCESSOS; i++) {
                if (!processosAtivos.containsKey(i + 1)) {
                    finalizar[i] = true;
                } else {
                    boolean temAlocacao = false;
                    for(int j=0; j<numRecursos; j++) {
                        if(alocacao[i][j] > 0) {
                            temAlocacao = true;
                            break;
                        }
                    }
                    // Se um processo não tem nada alocado, não pode estar em um ciclo de deadlock
                    finalizar[i] = !temAlocacao;
                }
            }

            boolean encontrouProcesso;
            do {
                encontrouProcesso = false;
                for (int i = 0; i < MAX_PROCESSOS; i++) {
                    if (!finalizar[i] && processoPodeExecutar(i, trabalho, numRecursos)) {
                        // Simula a liberação de recursos
                        for (int j = 0; j < numRecursos; j++) {
                            trabalho[j] += alocacao[i][j];
                        }
                        finalizar[i] = true;
                        encontrouProcesso = true;
                    }
                }
            } while (encontrouProcesso);

            // Verifica se algum processo não pôde ser finalizado
            List<Integer> processosEmDeadlock = new ArrayList<>();
            for (int i = 0; i < MAX_PROCESSOS; i++) {
                if (processosAtivos.containsKey(i + 1) && !finalizar[i]) {
                    processosEmDeadlock.add(i + 1);
                }
            }

            // Atualiza a UI com o resultado
            Platform.runLater(() -> {
                if (processosEmDeadlock.isEmpty()) {
                    deadlockStatusLabel.setText("Status Deadlock: Nenhum deadlock detectado.");
                    deadlockStatusLabel.setStyle("-fx-text-fill: green;");
                } else {
                    String ids = processosEmDeadlock.stream()
                                                    .map(String::valueOf)
                                                    .collect(Collectors.joining(", "));
                    deadlockStatusLabel.setText("DEADLOCK DETECTADO! Processos envolvidos: " + ids);
                    deadlockStatusLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                    log("DEADLOCK: Detectado envolvendo processos: " + ids);
                }
            });

        } finally {
            lockSistema.unlock();
        }
    }

    private boolean processoPodeExecutar(int idProcessoIndex, int[] trabalho, int numRecursos) {
        for (int j = 0; j < numRecursos; j++) {
            if (requisicao[idProcessoIndex][j] > trabalho[j]) {
                return false; // Não há recursos suficientes para satisfazer a requisição
            }
        }
        return true;
    }


    // --- Métodos de Atualização da UI ---

    private void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }

    private void atualizarTodosStatus() {
        atualizarStatusProcessos();
        atualizarStatusRecursos();
    }

    private void atualizarStatusProcessos() {
        Platform.runLater(() -> {
            processosStatusList.getItems().clear();
            List<Integer> sortedIds = new ArrayList<>(processosAtivos.keySet());
            Collections.sort(sortedIds);

            for (Integer id : sortedIds) {
                Processo p = processosAtivos.get(id);
                if (p != null) {
                    String status = "P" + id + " - Status: " + p.getStatus();
                    String alocados = getRecursosAlocadosString(p.idProcesso);
                    if (!alocados.isEmpty()) {
                        status += " | Alocado: " + alocados;
                    }
                    if (p.getStatus() == StatusProcesso.BLOQUEADO) {
                        status += " | Esperando por: " + getNomeRecurso(p.getRecursoEsperado());
                    }
                    processosStatusList.getItems().add(status);
                }
            }
        });
    }

    private void atualizarStatusRecursos() {
        Platform.runLater(() -> {
            recursosStatusList.getItems().clear();
            for (int i = 0; i < tiposRecurso.size(); i++) {
                Recurso r = tiposRecurso.get(i);
                String status;
                // Verifica se a simulação está rodando (disponivel não é nulo) e se o índice é válido
                if (disponivel != null && i < disponivel.length) {
                    status = r.nome + " (ID: " + r.id + ") | Total: " + r.quantidadeTotal + " | Disponível: " + disponivel[i];
                } else {
                    // Antes da simulação, mostra apenas o total
                    status = r.nome + " (ID: " + r.id + ") | Total: " + r.quantidadeTotal;
                }
                recursosStatusList.getItems().add(status);
            }
        });
    }

    // --- Métodos Auxiliares ---
    public Recurso getTipoRecurso(int indice) {
        if (indice >= 0 && indice < tiposRecurso.size()) {
            return tiposRecurso.get(indice);
        }
        return null;
    }
    
    public int getIndiceRecursoAleatorio() {
        if (tiposRecurso.isEmpty()) return -1;
        return new Random().nextInt(tiposRecurso.size());
    }
    
    public int getNumTiposRecurso() {
        return tiposRecurso.size();
    }

    private String getNomeRecurso(int indice) {
        Recurso r = getTipoRecurso(indice);
        return r != null ? r.nome : "N/A";
    }

    private String getRecursosAlocadosString(int idProcesso) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tiposRecurso.size(); i++) {
            if (alocacao[idProcesso - 1][i] > 0) {
                sb.append(getNomeRecurso(i)).append(" (").append(alocacao[idProcesso - 1][i]).append(") ");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Verifica se um processo possui algum recurso alocado.
     * @param idProcesso O ID do processo a ser verificado.
     * @return true se o processo possui pelo menos um recurso, false caso contrário.
     */
    public boolean processoTemRecursos(int idProcesso) {
        lockSistema.lock();
        try {
            for (int i = 0; i < tiposRecurso.size(); i++) {
                if (alocacao[idProcesso - 1][i] > 0) {
                    return true;
                }
            }
            return false;
        } finally {
            lockSistema.unlock();
        }
    }

    /**
     * Libera um recurso aleatório de um processo que o possui.
     * @param idProcesso O ID do processo.
     */
    public void liberarUmRecurso(int idProcesso, List<Integer> recursosPossuidos) {
        lockSistema.lock();
        try {
            if (!recursosPossuidos.isEmpty()) {
                int indiceParaLiberar = recursosPossuidos.get(0);
                log("PROCESSO " + idProcesso + " vai liberar o recurso " + getNomeRecurso(indiceParaLiberar));
                liberarRecurso(idProcesso, indiceParaLiberar);
                recursosPossuidos.remove(0); // Remove o recurso liberado da lista de possuídos
            }
        } finally {
            lockSistema.unlock();
        }
    }
}

// =================================================================================
// CLASSE RECURSO
// =================================================================================
class Recurso {
    final String nome;
    final int id;
    final int quantidadeTotal;

    public Recurso(String nome, int id, int quantidadeTotal) {
        this.nome = nome;
        this.id = id;
        this.quantidadeTotal = quantidadeTotal;
    }
}

// =================================================================================
// ENUM STATUS PROCESSO
// =================================================================================
enum StatusProcesso {
    EXECUTANDO,
    BLOQUEADO
}

// =================================================================================
// CLASSE PROCESSO (THREAD)
// =================================================================================
class Processo implements Runnable {
    final int idProcesso;
    List<Integer> recursosPossuidos = new ArrayList<>();
    private final long tempoSolicitacao; // ΔTs em segundos
    private final long tempoUtilizacao;  // ΔTu em segundos
    private final DeadlockApp app;

    private volatile StatusProcesso status;
    private volatile boolean rodando = true;
    private final Object lock = new Object();
    private int recursoEsperado = -1;

    public Processo(int id, long ts, long tu, DeadlockApp app) {
        this.idProcesso = id;
        this.tempoSolicitacao = ts;
        this.tempoUtilizacao = tu;
        this.app = app;
        this.status = StatusProcesso.EXECUTANDO;
    }

    @Override
    public void run() {
        while (rodando) {
            try {

                // Decide se vai solicitar um novo recurso ou liberar um existente.
                if (app.processoTemRecursos(idProcesso)) {
                    // --- AÇÃO DE LIBERAÇÃO ---
                    // Simula o uso do recurso por um tempo antes de liberá-lo.
                    TimeUnit.SECONDS.sleep(tempoUtilizacao);
                    app.liberarUmRecurso(idProcesso, recursosPossuidos);

                } else {
                    // --- AÇÃO DE SOLICITAÇÃO ---
                    // Espera o intervalo de tempo de solicitação.
                    TimeUnit.SECONDS.sleep(tempoSolicitacao);
                    
                    int indiceRecurso = app.getIndiceRecursoAleatorio();
                    if (indiceRecurso != -1) {
                        app.solicitarRecurso(idProcesso, indiceRecurso);
                        // Se o processo for bloqueado, ele espera aqui até ser acordado.
                        synchronized (lock) {
                            while (status == StatusProcesso.BLOQUEADO) {
                                lock.wait();
                            }
                        }
                        recursosPossuidos.add(indiceRecurso);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
                rodando = false; // Encerra o loop
            }
        }
        System.out.println("Processo " + idProcesso + " encerrado.");
    }

    public void parar() {
        this.rodando = false;
        Thread.currentThread().interrupt(); // Interrompe o sleep/wait
    }

    public void bloquear(int indiceRecurso) {
        this.status = StatusProcesso.BLOQUEADO;
        this.recursoEsperado = indiceRecurso;
    }

    public void acordar() {
        synchronized (lock) {
            this.status = StatusProcesso.EXECUTANDO;
            this.recursoEsperado = -1;
            lock.notify(); // Notifica a thread do processo para continuar
        }
    }
    
    public StatusProcesso getStatus() {
        return status;
    }
    
    public int getRecursoEsperado() {
        return recursoEsperado;
    }
}

// =================================================================================
// CLASSE SISTEMA OPERACIONAL (THREAD)
// =================================================================================
class SistemaOperacional implements Runnable {
    private final long intervaloVerificacao; // Δt em segundos
    private final DeadlockApp app;
    private volatile boolean rodando = true;

    public SistemaOperacional(long dt, DeadlockApp app) {
        this.intervaloVerificacao = dt;
        this.app = app;
    }

    @Override
    public void run() {
        while (rodando && !Thread.currentThread().isInterrupted()) {
            try {
                TimeUnit.SECONDS.sleep(intervaloVerificacao);
                app.detectarDeadlock();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                rodando = false;
            }
        }
        System.out.println("Thread do Sistema Operacional encerrada.");
    }
    
    public boolean isAlive() {
        return rodando && !Thread.currentThread().isInterrupted();
    }
}
