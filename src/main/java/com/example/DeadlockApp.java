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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    
    private final Semaphore lockSistema = new Semaphore(1, true);

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
        primaryStage.setTitle("Simulador de Detecção de Deadlock com Semáforos");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.setTop(criarPainelConfiguracao());
        root.setCenter(criarPainelStatus());
        
        VBox logBox = new VBox(5, new Label("Log de Operações:"), logArea);
        logArea.setEditable(false);
        logArea.setPrefHeight(150);
        root.setBottom(logBox);

        Scene scene = new Scene(root, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            if (executorProcessos != null) {
                executorProcessos.shutdownNow();
            }
            if (threadSO != null) {
                threadSO.interrupt();
            }
        });
        primaryStage.show();
    }

    private VBox criarPainelConfiguracao() {
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

        addRecursoBtn.setOnAction(e -> adicionarRecurso(nomeRecursoField.getText(), idRecursoField.getText(), qtdRecursoField.getText()));
        addProcessoBtn.setOnAction(e -> adicionarProcesso(idProcessoField.getText(), tempoSolicitacaoField.getText(), tempoUtilizacaoField.getText()));
        removerProcessoBtn.setOnAction(e -> removerProcesso(idProcessoField.getText()));
        iniciarSimulacaoBtn.setOnAction(e -> iniciarSimulacao(tempoVerificacaoField.getText()));
        forcarDeadlockBtn.setOnAction(e -> forcarDeadlock());

        HBox configHBox = new HBox(20, resourceGrid, processGrid, soGrid);
        configHBox.setAlignment(Pos.CENTER);
        
        VBox configVBox = new VBox(10, new Label("Configuração do Sistema"), configHBox);
        configVBox.setAlignment(Pos.CENTER);
        configVBox.setPadding(new Insets(10));
        
        return configVBox;
    }

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
            Processo p = processosAtivos.get(id);

            if (p != null) {
                p.parar();
                processosAtivos.remove(id);
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
        if (tiposRecurso.isEmpty() || processosAtivos.isEmpty()) {
            log("ERRO: Adicione recursos e processos antes de iniciar.");
            return;
        }
        if (so != null && so.isAlive()) {
             log("ERRO: Simulação já iniciada.");
            return;
        }

        try {
            long dt = Long.parseLong(dtStr);

            if (disponivel == null) {
                disponivel = new int[tiposRecurso.size()];
                for (int i = 0; i < tiposRecurso.size(); i++) {
                    disponivel[i] = tiposRecurso.get(i).quantidadeTotal;
                }
            }

            so = new SistemaOperacional(dt, this);
            threadSO = new Thread(so);
            threadSO.setDaemon(true);
            threadSO.start();

            executorProcessos = Executors.newFixedThreadPool(MAX_PROCESSOS);
            processosAtivos.values().forEach(executorProcessos::submit);
            
            log("INFO: Simulação iniciada. Verificação de deadlock a cada " + dt + " segundos.");
            atualizarStatusRecursos();

        } catch (NumberFormatException ex) {
            log("ERRO: Intervalo de verificação (Δt) deve ser um número.");
        }
    }

    private void forcarDeadlock() {
        if (so != null && so.isAlive()) {
            log("ERRO: Não é possível forçar deadlock com a simulação em andamento.");
            return;
        }

        tiposRecurso.clear();
        processosAtivos.clear();
        alocacao = new int[MAX_PROCESSOS][MAX_RECURSOS];
        requisicao = new int[MAX_PROCESSOS][MAX_RECURSOS];
        disponivel = null;
        log("INFO: Configuração reiniciada para exemplo de deadlock.");

        adicionarRecurso("Impressora", "1", "1");
        adicionarRecurso("Scanner", "2", "1");

        adicionarProcesso("1", "999", "999");
        adicionarProcesso("2", "999", "999");

        try {
            lockSistema.acquire();
            try {
                disponivel = new int[tiposRecurso.size()];
                for (int i = 0; i < tiposRecurso.size(); i++) {
                    disponivel[i] = tiposRecurso.get(i).quantidadeTotal;
                }

                alocacao[0][0] = 1;
                disponivel[0]--;
                alocacao[1][1] = 1;
                disponivel[1]--;

                requisicao[0][1] = 1;
                processosAtivos.get(1).bloquear(1);
                requisicao[1][0] = 1;
                processosAtivos.get(2).bloquear(0);

                log("INFO: Estado de deadlock forçado manualmente.");
                log("INFO: P1 alocou Impressora e espera por Scanner.");
                log("INFO: P2 alocou Scanner e espera por Impressora.");
                log("INFO: Clique em 'Iniciar Simulação' para que o SO detecte o deadlock.");

            } finally {
                lockSistema.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log("ERRO: A configuração de deadlock foi interrompida.");
        }
        atualizarTodosStatus();
    }


    // --- Métodos de Sincronização e Comunicação ---

    public void solicitarRecurso(int idProcesso, int indiceRecurso) {
        try {
            lockSistema.acquire();
            try {
                log("PROCESSO " + idProcesso + " está a solicitar " + getNomeRecurso(indiceRecurso));
                if (disponivel[indiceRecurso] > 0) {
                    disponivel[indiceRecurso]--;
                    alocacao[idProcesso - 1][indiceRecurso]++;
                    log("PROCESSO " + idProcesso + " alocou o recurso " + getNomeRecurso(indiceRecurso));
                } else {
                    requisicao[idProcesso - 1][indiceRecurso]++;
                    Processo p = processosAtivos.get(idProcesso);
                    if (p != null) {
                        p.bloquear(indiceRecurso);
                        log("PROCESSO " + idProcesso + " bloqueado esperando por " + getNomeRecurso(indiceRecurso));
                    }
                }
                atualizarTodosStatus();
            } finally {
                lockSistema.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void liberarRecurso(int idProcesso, int indiceRecurso) {
        try {
            lockSistema.acquire();
            try {
                if (alocacao[idProcesso - 1][indiceRecurso] > 0) {
                    alocacao[idProcesso - 1][indiceRecurso]--;
                    disponivel[indiceRecurso]++;
                    log("PROCESSO " + idProcesso + " liberou o recurso " + getNomeRecurso(indiceRecurso));
                    acordarProcessos(indiceRecurso);
                    atualizarTodosStatus();
                }
            } finally {
                lockSistema.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void liberarRecursosDeProcesso(int idProcesso) {
        try {
            lockSistema.acquire();
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
                lockSistema.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void acordarProcessos(int indiceRecurso) {
        for (int i = 0; i < MAX_PROCESSOS; i++) {
            if (requisicao[i][indiceRecurso] > 0) {
                Processo p = processosAtivos.get(i + 1);
                if (p != null && p.getStatus() == StatusProcesso.BLOQUEADO && disponivel[indiceRecurso] > 0) {
                    disponivel[indiceRecurso]--;
                    alocacao[i][indiceRecurso]++;
                    requisicao[i][indiceRecurso]--;
                    p.acordar();
                    log("PROCESSO " + p.idProcesso + " foi acordado e alocou " + getNomeRecurso(indiceRecurso));
                }
            }
        }
    }
    
    public void detectarDeadlock() {
        try {
            lockSistema.acquire();
            try {
                if (processosAtivos.isEmpty()) return;

                int numRecursos = tiposRecurso.size();
                int[] trabalho = Arrays.copyOf(disponivel, numRecursos);
                boolean[] finalizar = new boolean[MAX_PROCESSOS];
                
                for (int i = 0; i < MAX_PROCESSOS; i++) {
                    if (!processosAtivos.containsKey(i + 1)) {
                        finalizar[i] = true;
                    } else {
                        finalizar[i] = !processoTemRecursosNoIndice(i);
                    }
                }

                boolean encontrouProcesso;
                do {
                    encontrouProcesso = false;
                    for (int i = 0; i < MAX_PROCESSOS; i++) {
                        if (!finalizar[i] && processoPodeExecutar(i, trabalho, numRecursos)) {
                            for (int j = 0; j < numRecursos; j++) {
                                trabalho[j] += alocacao[i][j];
                            }
                            finalizar[i] = true;
                            encontrouProcesso = true;
                        }
                    }
                } while (encontrouProcesso);

                List<Integer> processosEmDeadlock = new ArrayList<>();
                for (int i = 0; i < MAX_PROCESSOS; i++) {
                    if (processosAtivos.containsKey(i + 1) && !finalizar[i]) {
                        processosEmDeadlock.add(i + 1);
                    }
                }

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
                lockSistema.release();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean processoPodeExecutar(int idProcessoIndex, int[] trabalho, int numRecursos) {
        for (int j = 0; j < numRecursos; j++) {
            if (requisicao[idProcessoIndex][j] > trabalho[j]) {
                return false;
            }
        }
        return true;
    }


    // --- Métodos de Atualização da UI ---

    public void log(String message) {
        Platform.runLater(() -> logArea.appendText(message + "\n"));
    }
    
    public void logUsoRecurso(int idProcesso, int indiceRecurso) {
        Processo p = processosAtivos.get(idProcesso);
        if (p != null) {
            log("PROCESSO " + idProcesso + " está a UTILIZAR " + getNomeRecurso(indiceRecurso) + " por " + p.getTempoUtilizacao() + "s.");
        }
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
                if (disponivel != null && i < disponivel.length) {
                    status = r.nome + " (ID: " + r.id + ") | Total: " + r.quantidadeTotal + " | Disponível: " + disponivel[i];
                } else {
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
    
    private boolean processoTemRecursosNoIndice(int processoIndex) {
        if (processoIndex < 0 || processoIndex >= alocacao.length) return false;
        for (int i = 0; i < tiposRecurso.size(); i++) {
            if (alocacao[processoIndex][i] > 0) {
                return true;
            }
        }
        return false;
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
    private final long tempoSolicitacao; // ΔTs em segundos
    private final long tempoUtilizacao;  // ΔTu em segundos
    private final DeadlockApp app;

    private volatile StatusProcesso status;
    private volatile boolean rodando = true;
    private volatile Thread thread;
    
    private final Semaphore semaforoBloqueio = new Semaphore(0);
    private int recursoEsperado = -1;

    private static class RecursoAlocado {
        final int indiceRecurso;
        final long tempoLiberacao;

        RecursoAlocado(int indice, long tempoDeUsoMs) {
            this.indiceRecurso = indice;
            this.tempoLiberacao = System.currentTimeMillis() + tempoDeUsoMs;
        }
    }

    private final List<RecursoAlocado> recursosEmUso = Collections.synchronizedList(new ArrayList<>());
    
    private long proximaSolicitacao;

    public Processo(int id, long ts, long tu, DeadlockApp app) {
        this.idProcesso = id;
        this.tempoSolicitacao = ts;
        this.tempoUtilizacao = tu;
        this.app = app;
        this.status = StatusProcesso.EXECUTANDO;
    }

    @Override
    public void run() {
        this.thread = Thread.currentThread();
        this.proximaSolicitacao = System.currentTimeMillis() + (tempoSolicitacao * 1000);

        while (rodando) {
            try {
                long agora = System.currentTimeMillis();

                // 1. VERIFICA SE PRECISA LIBERAR RECURSOS
                synchronized (recursosEmUso) {
                    recursosEmUso.removeIf(recurso -> {
                        if (agora >= recurso.tempoLiberacao) {
                            app.liberarRecurso(idProcesso, recurso.indiceRecurso);
                            return true;
                        }
                        return false;
                    });
                }

                // 2. VERIFICA SE PRECISA SOLICITAR NOVO RECURSO
                if (agora >= proximaSolicitacao) {
                    
                    // CORREÇÃO: Cria uma lista de recursos que o processo ainda não possui.
                    List<Integer> recursosSolicitaveis = IntStream.range(0, app.getNumTiposRecurso())
                                                                  .boxed()
                                                                  .collect(Collectors.toList());
                    synchronized(recursosEmUso) {
                        for(RecursoAlocado ra : recursosEmUso) {
                            recursosSolicitaveis.remove(Integer.valueOf(ra.indiceRecurso));
                        }
                    }

                    if (!recursosSolicitaveis.isEmpty()) {
                        // Escolhe um recurso aleatório da lista de solicitáveis
                        int indiceRecursoSolicitado = recursosSolicitaveis.get(new Random().nextInt(recursosSolicitaveis.size()));
                        
                        app.solicitarRecurso(idProcesso, indiceRecursoSolicitado);

                        if (status == StatusProcesso.BLOQUEADO) {
                            semaforoBloqueio.acquire();
                        }
                        
                        // Se não foi bloqueado (ou foi acordado), adiciona o recurso à lista de gerenciamento
                        if (status == StatusProcesso.EXECUTANDO) {
                            recursosEmUso.add(new RecursoAlocado(indiceRecursoSolicitado, tempoUtilizacao * 1000));
                            app.logUsoRecurso(idProcesso, indiceRecursoSolicitado);
                        }
                    } else {
                        app.log("PROCESSO " + idProcesso + " já possui todos os tipos de recursos. Nenhuma nova solicitação será feita por enquanto.");
                    }

                    // Agenda a próxima solicitação independentemente de ter conseguido ou não
                    this.proximaSolicitacao = System.currentTimeMillis() + (tempoSolicitacao * 1000);
                }

                TimeUnit.MILLISECONDS.sleep(100);

            } catch (InterruptedException e) {
                rodando = false;
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Processo " + idProcesso + " encerrado.");
    }

    public void parar() {
        this.rodando = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    public void bloquear(int indiceRecurso) {
        this.status = StatusProcesso.BLOQUEADO;
        this.recursoEsperado = indiceRecurso;
    }

    public void acordar() {
        this.status = StatusProcesso.EXECUTANDO;
        this.recursoEsperado = -1;
        semaforoBloqueio.release();
    }
    
    public StatusProcesso getStatus() {
        return status;
    }
    
    public int getRecursoEsperado() {
        return recursoEsperado;
    }

    public long getTempoUtilizacao() {
        return tempoUtilizacao;
    }
}

// =================================================================================
// CLASSE SISTEMA OPERACIONAL (THREAD)
// =================================================================================
class SistemaOperacional implements Runnable {
    private final long intervaloVerificacao;
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
                rodando = false;
                Thread.currentThread().interrupt();
            }
        }
        System.out.println("Thread do Sistema Operacional encerrada.");
    }
    
    public boolean isAlive() {
        return rodando && !Thread.currentThread().isInterrupted();
    }
}
