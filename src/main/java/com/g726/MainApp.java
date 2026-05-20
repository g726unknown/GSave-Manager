package com.g726;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.OverrunStyle;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MainApp extends Application {

    private ArchiveManager manager;
    private StackPane mainContentArea;
    private StackPane rootStack;
    private HBox toastBanner;
    private FadeTransition toastFadeIn;
    private FadeTransition toastFadeOut;
    private PauseTransition toastDelay;
    private final Logger logger = SomeUtils.getLogger();
    private AppSettings settings;
    private ScheduledExecutorService autoSaveScheduler;
    private ExecutorService autoSaveWatchExecutor;
    private WatchService autoSaveWatchService;
    private final Map<String, Long> autoSaveDebounce = new ConcurrentHashMap<>();

    private StackPane gameListView; // 已升级为 StackPane 支持悬浮按钮
    private VBox settingsView;
    private VBox branchDetailView;
    private ScrollPane aboutView;

    private Map<String, String> branchLimitCache = new HashMap<>();
    private boolean saveToLatestBranch = true; // 新增：全局设置状态

    private static final String SIDEBAR_BG_COLOR = "#f0f2f5";
    private static final String SIDEBAR_TEXT_COLOR = "#24292f";
    private static final String CONTENT_BG_COLOR = "#f6f8fa";
    private static final String HEADER_BG_COLOR = "#f0f2f5";
    private static final String BORDER_COLOR = "#e1e4e8";
    private static final String ADD_GAME_BTN_COLOR = "#238636";

    @Override
    public void init() throws Exception {
        SomeUtils.initLogging();
        settings = SettingsManager.load();
        branchLimitCache = new HashMap<>(settings.getBranchLimits());
        saveToLatestBranch = settings.isSaveToLatestBranch();
        manager = new ArchiveManager();
        startAutoSaveService();
    }

    @Override
    public void start(Stage primaryStage) {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + CONTENT_BG_COLOR + ";");

        mainContentArea = new StackPane();
        initViews();

        mainContentArea.getChildren().addAll(settingsView, branchDetailView, gameListView, aboutView);
        root.setCenter(mainContentArea);

        VBox sideBar = new VBox();
        sideBar.setPrefWidth(200);
        sideBar.setStyle("-fx-background-color: " + SIDEBAR_BG_COLOR + "; -fx-border-color: " + BORDER_COLOR
                + "; -fx-border-width: 0 1 0 0;");
        sideBar.setPadding(new Insets(20, 10, 20, 10));

        Label logoLabel = new Label(" GSave Manager ");
        logoLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + SIDEBAR_TEXT_COLOR
                + "; -fx-padding: 0 10 20 10;");
        logoLabel.setAlignment(Pos.CENTER);

        VBox navButtons = new VBox(5);
        ToggleButton btnArchive = createNavButton("首页", " 🎮 ");
        ToggleButton btnSettings = createNavButton("设置", " ⚙ ");
        ToggleButton btnAbout = createNavButton("关于", " 💡 ");

        btnArchive.setSelected(true);
        switchView(gameListView);

        btnArchive.setOnAction(e -> {
            switchView(gameListView);
            renderGameListView();
            btnArchive.setSelected(true);
            btnSettings.setSelected(false);
            btnAbout.setSelected(false);
        });
        btnSettings.setOnAction(e -> {
            switchView(settingsView);
            btnArchive.setSelected(false);
            btnSettings.setSelected(true);
            btnAbout.setSelected(false);
        });
        btnAbout.setOnAction(e -> {
            switchView(aboutView);
            btnArchive.setSelected(false);
            btnSettings.setSelected(false);
            btnAbout.setSelected(true);
        });

        navButtons.getChildren().addAll(btnArchive, btnSettings, btnAbout);
        sideBar.getChildren().addAll(logoLabel, navButtons);
        root.setLeft(sideBar);

        Scene scene = new Scene(root, 1000, 650);
        rootStack = new StackPane(root);
        initToastBanner();
        rootStack.getChildren().add(toastBanner);
        StackPane.setAlignment(toastBanner, Pos.BOTTOM_LEFT);
        StackPane.setMargin(toastBanner, new Insets(0, 0, 20, 20));

        scene.setRoot(rootStack);
        primaryStage.setTitle("GSave Manager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> {
            stopAutoSaveService();
            SettingsManager.save(settings);
        });
        primaryStage.show();
    }

    private ToggleButton createNavButton(String text, String iconPlaceholder) {
        HBox content = new HBox(10);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(0, 15, 0, 15));

        Label iconLabel = new Label(iconPlaceholder);
        iconLabel.setStyle("-fx-font-size: 14px;");

        Label textLabel = new Label(text);
        textLabel.setStyle("-fx-font-size: 14px;");

        content.getChildren().addAll(iconLabel, textLabel);

        ToggleButton btn = new ToggleButton();
        btn.setGraphic(content);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setPrefHeight(40);
        btn.setStyle("-fx-cursor: hand; -fx-text-fill: " + SIDEBAR_TEXT_COLOR + ";");
        btn.setAlignment(Pos.CENTER_LEFT);

        btn.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                String css = getClass().getResource("/style.css").toExternalForm();
                if (!newScene.getStylesheets().contains(css)) {
                    newScene.getStylesheets().add(css);
                }
                btn.getStyleClass().add("nav-button");
            }
        });

        return btn;
    }

    private void initViews() {
        gameListView = new StackPane();
        renderGameListView();

        branchDetailView = new VBox();
        branchDetailView.setVisible(false);

        settingsView = new VBox(20);
        settingsView.setPadding(new Insets(20));

        Label settingsTitle = createPageHeader("软件设置");

        HBox refreshRow = new HBox(15);
        refreshRow.setAlignment(Pos.CENTER_LEFT);
        Label refreshLabel = new Label("刷新游戏库配置：");
        refreshLabel.setStyle("-fx-font-size: 14px;");

        Button btnRefresh = new Button("一键刷新/校验");
        btnRefresh.setStyle("-fx-background-color: " + ADD_GAME_BTN_COLOR
                + "; -fx-text-fill: white; -fx-background-radius: 5px; -fx-cursor: hand;");
        btnRefresh.setOnAction(e -> {
            runWithErrorAlert("刷新失败", () -> {
                manager.refreshArchives();
                renderGameListView();
            });
        });
        refreshRow.getChildren().addAll(refreshLabel, btnRefresh);

        HBox oneKeyUpdateRow = new HBox(10);
        oneKeyUpdateRow.setAlignment(Pos.CENTER_LEFT);

        Label toggleLabel = new Label("一键刷新保存至最新分支");
        toggleLabel.setStyle("-fx-font-size: 14px;");

        Label infoIcon = new Label(" ⓘ ");
        infoIcon.setStyle("-fx-font-weight: bold; -fx-text-fill: #0366d6; -fx-cursor: hand;");

        Tooltip infoTooltip = new Tooltip("关闭此项后，一键刷新功能就会保存到默认分支而非最新分支");
        infoTooltip.setShowDelay(javafx.util.Duration.millis(0));
        infoTooltip.setStyle("-fx-font-size: 12px;");

        infoIcon.setOnMouseEntered(e -> {
            javafx.geometry.Bounds bounds = infoIcon.localToScreen(infoIcon.getBoundsInLocal());
            if (bounds != null) {
                infoTooltip.show(infoIcon, bounds.getMinX(), bounds.getMinY() - 30);
                infoTooltip.setX(bounds.getMinX() + (bounds.getWidth() - infoTooltip.getWidth()) / 2);
            }
        });

        infoIcon.setOnMouseExited(e -> infoTooltip.hide());

        CheckBox toggleSwitch = new CheckBox();
        toggleSwitch.getStyleClass().add("blue-switch");
        toggleSwitch.setSelected(saveToLatestBranch);
        toggleSwitch.setOnAction(e -> {
            saveToLatestBranch = toggleSwitch.isSelected();
            settings.setSaveToLatestBranch(saveToLatestBranch);
            SettingsManager.save(settings);
        });

        oneKeyUpdateRow.getChildren().addAll(toggleLabel, infoIcon, toggleSwitch);

        HBox autoSaveModeRow = new HBox(12);
        autoSaveModeRow.setAlignment(Pos.CENTER_LEFT);
        Label autoSaveModeLabel = new Label("自动保存模式:");
        autoSaveModeLabel.setStyle("-fx-font-size: 14px;");

        ChoiceBox<String> autoSaveModeBox = new ChoiceBox<>();
        autoSaveModeBox.getItems().addAll("关闭", "定时保存", "监听保存");
        String currentMode = settings.getAutoSaveMode();
        if ("INTERVAL".equals(currentMode)) {
            autoSaveModeBox.setValue("定时保存");
        } else if ("WATCH".equals(currentMode)) {
            autoSaveModeBox.setValue("监听保存");
        } else {
            autoSaveModeBox.setValue("关闭");
        }
        autoSaveModeBox.setPrefHeight(32);

        autoSaveModeRow.getChildren().addAll(autoSaveModeLabel, autoSaveModeBox);

        HBox autoSaveIntervalRow = new HBox(12);
        autoSaveIntervalRow.setAlignment(Pos.CENTER_LEFT);
        Label autoSaveIntervalLabel = new Label("定时间隔(秒):");
        autoSaveIntervalLabel.setStyle("-fx-font-size: 14px;");
        TextField autoSaveIntervalInput = new TextField(String.valueOf(settings.getAutoSaveIntervalSeconds()));
        autoSaveIntervalInput.setPrefWidth(100);
        autoSaveIntervalInput.setPrefHeight(32);
        autoSaveIntervalRow.getChildren().addAll(autoSaveIntervalLabel, autoSaveIntervalInput);

        HBox autoSaveDebounceRow = new HBox(12);
        autoSaveDebounceRow.setAlignment(Pos.CENTER_LEFT);
        Label autoSaveDebounceLabel = new Label("抖动阈值(秒):");
        autoSaveDebounceLabel.setStyle("-fx-font-size: 14px;");
        TextField autoSaveDebounceInput = new TextField(String.valueOf(settings.getAutoSaveDebounceSeconds()));
        autoSaveDebounceInput.setPrefWidth(100);
        autoSaveDebounceInput.setPrefHeight(32);
        autoSaveDebounceRow.getChildren().addAll(autoSaveDebounceLabel, autoSaveDebounceInput);

        Button applyAutoSaveBtn = new Button("应用自动保存设置");
        applyAutoSaveBtn.setPrefHeight(32);
        applyAutoSaveBtn.getStyleClass().add("secondary-button");
        applyAutoSaveBtn.setOnAction(e -> {
            String modeText = autoSaveModeBox.getValue();
            String mode = "OFF";
            if ("定时保存".equals(modeText)) {
                mode = "INTERVAL";
            } else if ("监听保存".equals(modeText)) {
                mode = "WATCH";
            }

            int intervalSeconds;
            int debounceSeconds;
            try {
                intervalSeconds = Integer.parseInt(autoSaveIntervalInput.getText().trim());
                debounceSeconds = Integer.parseInt(autoSaveDebounceInput.getText().trim());
            } catch (NumberFormatException ex) {
                showErrorAlert("自动保存设置错误", "请输入有效的数字");
                return;
            }

            if ("INTERVAL".equals(mode) && intervalSeconds <= 0) {
                showErrorAlert("自动保存设置错误", "定时间隔必须大于 0");
                return;
            }
            if (debounceSeconds < 0) {
                showErrorAlert("自动保存设置错误", "抖动阈值不能为负数");
                return;
            }

            settings.setAutoSaveMode(mode);
            settings.setAutoSaveIntervalSeconds(intervalSeconds);
            settings.setAutoSaveDebounceSeconds(debounceSeconds);
            SettingsManager.save(settings);
            startAutoSaveService();
        });

        autoSaveModeBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean isInterval = "定时保存".equals(newVal);
            autoSaveIntervalInput.setDisable(!isInterval);
        });
        autoSaveIntervalInput.setDisable(!"定时保存".equals(autoSaveModeBox.getValue()));

        settingsView.getChildren().addAll(settingsTitle, refreshRow, oneKeyUpdateRow,
            autoSaveModeRow, autoSaveIntervalRow, autoSaveDebounceRow, applyAutoSaveBtn);
        settingsView.setVisible(false);

        VBox aboutContent = parseReadmeToVBox();

        aboutView = new ScrollPane(aboutContent);
        aboutView.setFitToWidth(true);
        aboutView.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        aboutView.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        aboutView.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        aboutView.setVisible(false);

        applySimpleFastScroll(aboutView, aboutContent);
    }

    private void renderGameListView() {
        gameListView.getChildren().clear();

        VBox contentBox = new VBox();
        HBox topBar = createPageHeaderHBox("首页");

        FlowPane cardsPane = new FlowPane(20, 20);
        cardsPane.setPadding(new Insets(20));

        Set<String> uniqueGames = manager.getGameNames();

        for (String gameName : uniqueGames) {
            GameArchive latestArchive = manager.getLatestArchiveForGame(gameName);
            String sourcePath = manager.getSourcePathForGame(gameName);

            VBox gameCard = createGameCard(gameName, latestArchive, sourcePath);
            gameCard.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    openBranchDetail(gameName);
                }
            });
            cardsPane.getChildren().add(gameCard);
        }

        VBox addGameCard = createAddGameCard();
        addGameCard.setOnMouseClicked(e -> handleAddNewGame());
        cardsPane.getChildren().add(addGameCard);

        ScrollPane scrollPane = new ScrollPane(cardsPane);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        applySimpleFastScroll(scrollPane, cardsPane);

        contentBox.getChildren().addAll(topBar, scrollPane);

        Button globalFabBtn = new Button("🔄 一键更新所有游戏");
        globalFabBtn.setStyle("-fx-background-color: " + ADD_GAME_BTN_COLOR
                + "; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 25px; -fx-padding: 12 24; -fx-cursor: hand;");
        DropShadow fabShadow = new DropShadow(10, Color.valueOf("#0000004d"));
        fabShadow.setOffsetY(3);
        globalFabBtn.setEffect(fabShadow);

        globalFabBtn.setOnAction(e -> {
            int failedCount = 0;
            String firstError = null;
            for (String gName : uniqueGames) {
                String targetBranch = "默认分支";
                if (saveToLatestBranch) {
                    String latestBranch = manager.getLatestBranchNameForGame(gName);
                    if (latestBranch != null) {
                        targetBranch = latestBranch;
                    }
                }
                try {
                    manager.updateArchive(gName, targetBranch);
                } catch (ArchiveOperationException ex) {
                    failedCount++;
                    if (firstError == null) {
                        firstError = ex.getMessage();
                    }
                    logger.log(Level.WARNING, "一键更新失败: " + gName + " / " + targetBranch, ex);
                }
            }
            renderGameListView();
            if (failedCount > 0) {
                String message = "共有 " + failedCount + " 个存档更新失败" +
                        (firstError == null ? "" : "\n示例：" + firstError) +
                        "\n详情请查看 logs/app.log";
                showErrorAlert("一键更新失败", message);
            }
        });

        StackPane.setAlignment(globalFabBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(globalFabBtn, new Insets(0, 30, 30, 0));

        gameListView.getChildren().addAll(contentBox, globalFabBtn);
    }

    private VBox createGameCard(String gameName, GameArchive latestArchive, String sourcePath) {
        VBox card = new VBox(10);
        card.setPrefSize(200, 130);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("game-card");

        Label nameLabel = new Label(gameName);
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #24292f;");

        String displayBranch = latestArchive != null ? latestArchive.getSaveName() : "无存档";
        Label statusLabel = new Label(displayBranch);
        statusLabel.setStyle(
                "-fx-font-size: 12px; -fx-background-color: #f1f8ff; -fx-text-fill: #0366d6; -fx-background-radius: 20px; -fx-padding: 2 10 2 10;");

        Label timeLabel = new Label("上次备份时间: \n未知");
        timeLabel.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #6a737d; -fx-alignment: center; -fx-text-alignment: center;");

        if (latestArchive != null) {
            String displayTime = SomeUtils.formatTimestampForDisplay(latestArchive.getTimeStamp());
            timeLabel.setText("上次备份时间: \n" + displayTime);
        }

        card.getChildren().addAll(nameLabel, statusLabel, timeLabel);

        ContextMenu contextMenu = new ContextMenu();
        contextMenu.setMinWidth(120);

        Label accessLabel = new Label("访问存档");
        accessLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #24292f;");
        HBox accessBox = new HBox(accessLabel);
        accessBox.setAlignment(Pos.CENTER);
        accessBox.getStyleClass().add("menu-action-box");

        Tooltip pathTooltip = new Tooltip("源路径: " + sourcePath);
        pathTooltip.setStyle("-fx-font-size: 12px;");
        pathTooltip.setShowDelay(javafx.util.Duration.millis(0));

        accessBox.setOnMouseEntered(e -> {
            Bounds bounds = accessBox.localToScreen(accessBox.getBoundsInLocal());
            if (bounds != null) {
                pathTooltip.show(accessBox, bounds.getMinX(), bounds.getMinY() - 30);
                pathTooltip.setX(bounds.getMinX() + (bounds.getWidth() - pathTooltip.getWidth()) / 2);
            }
        });
        accessBox.setOnMouseExited(e -> pathTooltip.hide());

        CustomMenuItem accessItem = new CustomMenuItem(accessBox, true);
        accessItem.setOnAction(e -> {
            pathTooltip.hide();
            SomeUtils.openFolder(Paths.get(sourcePath));
        });

        // 安全的分割线实现
        Region line = new Region();
        line.setStyle("-fx-background-color: #e1e4e8;");
        line.setMinHeight(1);
        line.setPrefHeight(1);
        HBox sepBox = new HBox(line);
        sepBox.setPadding(new Insets(4, 15, 4, 15));
        HBox.setHgrow(line, Priority.ALWAYS);
        CustomMenuItem separatorItem = new CustomMenuItem(sepBox, false);

        Label deleteLabel = new Label("删除备份");
        deleteLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d12420;");
        HBox deleteBox = new HBox(deleteLabel);
        deleteBox.setAlignment(Pos.CENTER);
        deleteBox.getStyleClass().add("menu-danger-box");

        CustomMenuItem deleteItem = new CustomMenuItem(deleteBox, true);
        deleteItem.setOnAction(e -> confirmAction(
                "删除备份",
                "确定要删除【" + gameName + "】的所有备份存档吗？\n注意：这只会删除备份文件",
                () -> {
                    Set<String> branchesToDelete = new HashSet<>();
                    for (GameArchive a : manager.getAllArchives()) {
                        if (a.getGameName().equals(gameName)) {
                            branchesToDelete.add(a.getSaveName());
                        }
                    }
                    for (String branch : branchesToDelete) {
                        manager.removeArchive(gameName, branch);
                    }
                    renderGameListView();
                }));

        contextMenu.getItems().addAll(accessItem, separatorItem, deleteItem);
        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }

    private VBox createAddGameCard() {
        VBox card = new VBox(10);
        card.setPrefSize(200, 130);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("add-game-card");

        Label iconLabel = new Label("+");
        iconLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: normal; -fx-text-fill: #57606a;");
        Label textLabel = new Label("添加新游戏存档");
        textLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #57606a;");

        card.getChildren().addAll(iconLabel, textLabel);
        return card;
    }

    private void switchView(javafx.scene.Node targetView) {
        gameListView.setVisible(false);
        settingsView.setVisible(false);
        branchDetailView.setVisible(false);
        if (aboutView != null) {
            aboutView.setVisible(false);
        }

        targetView.setVisible(true);
        targetView.toFront();
    }

    private void openBranchDetail(String gameName) {
        String latestBranch = manager.getLatestBranchNameForGame(gameName);
        if (latestBranch != null) {
            openBranchDetailWithBranch(gameName, latestBranch);
        }
    }

    private void openBranchDetailWithBranch(String gameName, String currentBranch) {
        branchDetailView.getChildren().clear();

        HBox topBar = new HBox(15);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPrefHeight(85);
        topBar.setPadding(new Insets(15, 30, 15, 20));
        topBar.setStyle("-fx-background-color: " + HEADER_BG_COLOR + "; -fx-border-color: " + BORDER_COLOR
                + "; -fx-border-width: 0 0 1 0;");

        Button backBtn = new Button("← 返回");
        backBtn.getStyleClass().add("back-button");
        backBtn.setOnAction(e -> {
            renderGameListView();
            switchView(gameListView);
        });

        Label titleLabel = new Label(gameName + " 详情管理");
        titleLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #24292f;");

        Set<String> branches = manager.getBranchesForGame(gameName);
        String sourceSavePath = manager.getSourcePathForGame(gameName);

        ComboBox<String> branchSelector = new ComboBox<>();
        branchSelector.getItems().addAll(branches);
        branchSelector.getItems().add("+ 添加分支");
        branchSelector.setValue(currentBranch);
        branchSelector.setPrefHeight(36);
        branchSelector.setStyle("-fx-font-size: 14px;");

        final String finalSourcePath = sourceSavePath;
        branchSelector.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("+ 添加分支".equals(item)) {
                        setStyle("-fx-text-fill: #238636; -fx-font-weight: bold; -fx-cursor: hand;");
                    } else {
                        setStyle("-fx-text-fill: #24292f;");
                    }
                }
            }
        });

        branchSelector.setOnAction(e -> {
            String selected = branchSelector.getValue();
            if ("+ 添加分支".equals(selected)) {
                Platform.runLater(() -> branchSelector.setValue(currentBranch));
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("创建新分支");
                dialog.setHeaderText("基于游戏源路径当下存档，建立新分支");
                dialog.setContentText("请输入新分支名称:");
                dialog.showAndWait().ifPresent(newName -> {
                    String trimmedName = newName.trim();
                    if (!trimmedName.isEmpty() && !branches.contains(trimmedName)) {
                        runWithErrorAlert("创建分支失败", () -> {
                            manager.addArchive(gameName, trimmedName, finalSourcePath);
                            openBranchDetailWithBranch(gameName, trimmedName);
                        });
                    }
                });
            } else if (selected != null && !selected.equals(currentBranch)) {
                openBranchDetailWithBranch(gameName, selected);
            }
        });

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);

        Label branchPrefixLabel = new Label("当前分支: ");
        branchPrefixLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #57606a;");

        topBar.getChildren().addAll(backBtn, titleLabel, topSpacer, branchPrefixLabel, branchSelector);
        HBox.setMargin(titleLabel, new Insets(0, 0, 0, 10));

        HBox controlBar = new HBox(15);
        controlBar.setAlignment(Pos.CENTER_LEFT);
        controlBar.setPadding(new Insets(15, 30, 15, 30));
        controlBar.setStyle(
                "-fx-background-color: white; -fx-border-color: " + BORDER_COLOR + "; -fx-border-width: 0 0 1 0;");

        Label historyLabel = new Label("最大保存数量:");
        historyLabel.setStyle("-fx-font-size: 14px;");

        String cacheKey = gameName + "::" + currentBranch;
        String savedLimit = branchLimitCache.getOrDefault(cacheKey, "0");
        TextField historyInput = new TextField(savedLimit);
        historyInput.setPrefWidth(70);
        historyInput.setPrefHeight(34);

        Button applyHistoryBtn = new Button("应用限制");
        applyHistoryBtn.setPrefHeight(34);
        historyInput.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused) {
                // 魔法防冲突：如果失去焦点是因为鼠标正悬停在“应用按钮”上，
                // 或者通过键盘 Tab 键把焦点切到了按钮上，就不执行回滚！
                if (applyHistoryBtn.isHover() || applyHistoryBtn.isFocused()) {
                    return;
                }
                String currentCache = branchLimitCache.getOrDefault(cacheKey, "0");
                if (!historyInput.getText().equals(currentCache)) {
                    historyInput.setText(currentCache);
                }
            }
        });
        applyHistoryBtn.setOnAction(e -> {
            try {
                int limit = Integer.parseInt(historyInput.getText());
                branchLimitCache.put(cacheKey, historyInput.getText());
                settings.getBranchLimits().put(cacheKey, historyInput.getText());
                SettingsManager.save(settings);
                runWithErrorAlert("应用限制失败", () -> {
                    manager.enforceHistoryLimit(gameName, currentBranch, limit);
                    openBranchDetailWithBranch(gameName, currentBranch);
                });
            } catch (NumberFormatException ex) {
            }
        });

        historyInput.setOnAction(e -> applyHistoryBtn.fire());

        Region controlSpacer = new Region();
        HBox.setHgrow(controlSpacer, Priority.ALWAYS);

        Button openBranchDirBtn = new Button("打开分支目录");
        openBranchDirBtn.setPrefHeight(34);
        openBranchDirBtn.getStyleClass().add("secondary-button");
        openBranchDirBtn.setOnAction(e -> {
            Path branchPath = Paths.get("Backup").resolve(gameName).resolve(currentBranch);
            try {
                if (Files.notExists(branchPath)) {
                    Files.createDirectories(branchPath);
                }
            } catch (Exception ex) {
            }
            SomeUtils.openFolder(branchPath);
        });

        Button copyBranchBtn = new Button("复制分支");
        copyBranchBtn.setPrefHeight(34);
        copyBranchBtn.getStyleClass().add("secondary-button");
        copyBranchBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("复制分支");
            dialog.setHeaderText("将复制当前分支【" + currentBranch + "】的所有历史快照");
            dialog.setContentText("请输入新副本分支的名称:");
            dialog.showAndWait().ifPresent(newName -> {
                String trimmedName = newName.trim();
                if (!trimmedName.isEmpty() && !branches.contains(trimmedName)) {
                    runWithErrorAlert("复制分支失败", () -> {
                        manager.createBranchCopy(gameName, currentBranch, trimmedName);
                        openBranchDetailWithBranch(gameName, trimmedName);
                    });
                }
            });
        });

        Button deleteBranchBtn = new Button("删除此分支");
        deleteBranchBtn.setPrefHeight(34);
        deleteBranchBtn.getStyleClass().add("danger-button");
        deleteBranchBtn.setOnAction(e -> confirmAction(
                "删除此分支",
                "确定要彻底删除【" + currentBranch + "】分支下的所有历史备份吗？\n此操作无法撤销！",
                () -> {
                    manager.removeArchive(gameName, currentBranch);
                    if (manager.hasGame(gameName)) {
                        openBranchDetail(gameName);
                    } else {
                        renderGameListView();
                        switchView(gameListView);
                    }
                }));

        controlBar.getChildren().addAll(historyLabel, historyInput, applyHistoryBtn, controlSpacer, openBranchDirBtn,
                copyBranchBtn, deleteBranchBtn);

        VBox listContainer = new VBox(12);
        listContainer.setPadding(new Insets(20, 30, 100, 30));

        List<GameArchive> archives = manager.getArchivesForBranch(gameName, currentBranch);

        for (GameArchive archive : archives) {
            HBox row = new HBox(10);
            row.getStyleClass().add("timestamp-row");
            row.setAlignment(Pos.CENTER_LEFT);

            String baseTimeStr = SomeUtils.formatTimestampForDisplay(archive.getTimeStamp());
            String customName = archive.getCustomName();

            // 1. 组合容器
            HBox timeTagContainer = new HBox(12); // 稍微拉开一点时间戳和标签的距离
            timeTagContainer.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(timeTagContainer, Priority.ALWAYS);

            // 2. 时间戳
            Label timeLabel = new Label(baseTimeStr);
            timeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #24292f;");
            timeLabel.setMinWidth(Region.USE_PREF_SIZE);

            timeTagContainer.getChildren().add(timeLabel);

            // 3. 标签逻辑 (弃用 Emoji，改用现代化的"胶囊"标签样式)
            if (!customName.isEmpty()) {
                Label tagNameLabel = new Label(customName);
                // 使用浅蓝色底色、深蓝色文字、大圆角，做出类似 GitHub 标签的视觉效果
                tagNameLabel.setStyle(
                        "-fx-font-size: 12px; -fx-text-fill: #0969da; -fx-background-color: #ddf4ff; -fx-padding: 3 8; -fx-background-radius: 12px;");
                tagNameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
                tagNameLabel.setMinWidth(0);
                tagNameLabel.setMaxWidth(300);

                // 修复：重新启用绝对坐标计算，让提示框死死钉在标签正上方
                Tooltip tagTooltip = new Tooltip(customName);
                tagTooltip.setShowDelay(javafx.util.Duration.millis(0));
                tagTooltip.setStyle("-fx-font-size: 12px;");

                tagNameLabel.setOnMouseEntered(e -> {
                    javafx.geometry.Bounds bounds = tagNameLabel.localToScreen(tagNameLabel.getBoundsInLocal());
                    if (bounds != null) {
                        tagTooltip.show(tagNameLabel, bounds.getMinX(), bounds.getMinY() - 30);
                        tagTooltip.setX(bounds.getMinX() + (bounds.getWidth() - tagTooltip.getWidth()) / 2);
                    }
                });
                tagNameLabel.setOnMouseExited(e -> tagTooltip.hide());

                timeTagContainer.getChildren().add(tagNameLabel);
            }

            // 4. 重命名按钮 (弃用 Emoji，加上明显边框)
            Button renameBtn = new Button("设置标签");
            renameBtn.setMinWidth(Region.USE_PREF_SIZE);
            renameBtn.setStyle("-fx-cursor: hand;");

            renameBtn.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog(archive.getCustomName());
                dialog.setTitle("设置标签");
                dialog.setHeaderText("为该时间点 [" + baseTimeStr + "] 增加一个备注名称");
                dialog.setContentText("请输入标签 (允许重名，清空则删除标签):");

                dialog.showAndWait().ifPresent(newName -> {
                    manager.renameSnapshot(archive.getGameName(), archive.getSaveName(), archive.getTimeStamp(),
                            newName.trim());
                    openBranchDetailWithBranch(gameName, currentBranch);
                });
            });

            // 5. 分割弹簧
            Region rowSpacer = new Region();
            HBox.setHgrow(rowSpacer, Priority.ALWAYS);

            // 6. 其他功能按钮
            Button restoreBtn = new Button("还原覆盖");
            restoreBtn.setMinWidth(Region.USE_PREF_SIZE);
            restoreBtn.setStyle(
                    "-fx-background-color: #238636; -fx-text-fill: white; -fx-cursor: hand; -fx-font-weight: bold;");
            restoreBtn.setOnAction(e -> confirmAction(
                    "即将覆盖当前游戏的本地源文件",
                    "确定要将本地游戏存档回退到 [" + timeLabel.getText() + "] 吗？\n警告：当前本地未备份的最新游戏进度将被永久覆盖丢失！",
                    () -> manager.restoreArchive(archive.getGameName(), archive.getSaveName(),
                            archive.getTimeStamp())));

            Button createFromBtn = new Button("创建分叉");
            createFromBtn.setMinWidth(Region.USE_PREF_SIZE);
            createFromBtn.setStyle("-fx-cursor: hand;");
            createFromBtn.setOnAction(e -> {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("创建分叉");
                dialog.setHeaderText("以该时间戳为起点创建新分支");
                dialog.setContentText("请输入新分支名称:");
                dialog.showAndWait().ifPresent(newName -> {
                    String trimmedName = newName.trim();
                    if (!trimmedName.isEmpty() && !branches.contains(trimmedName)) {
                        runWithErrorAlert("创建分叉失败", () -> {
                            manager.createBranchFromSnapshot(archive.getGameName(), archive.getSaveName(),
                                    archive.getTimeStamp(), trimmedName);
                            openBranchDetailWithBranch(gameName, trimmedName);
                        });
                    }
                });
            });

            Button delBtn = new Button("删除");
            delBtn.setMinWidth(Region.USE_PREF_SIZE);
            delBtn.getStyleClass().add("danger-text-button");
            delBtn.setOnAction(e -> confirmAction(
                    "删除时间戳备份",
                    "确定要永久删除 [" + timeLabel.getText() + "] 这个备份记录吗？\n此操作无法撤销！",
                    () -> {
                        manager.removeSnapshot(archive.getGameName(), archive.getSaveName(), archive.getTimeStamp());
                        openBranchDetailWithBranch(gameName, currentBranch);
                    }));

            // 7. 组装行
            row.getChildren().addAll(timeTagContainer, rowSpacer, restoreBtn, renameBtn, createFromBtn, delBtn);

            listContainer.getChildren().add(row);
        }

        ScrollPane scrollPane = new ScrollPane(listContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle(
                "-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");

        applySimpleFastScroll(scrollPane, listContainer);

        Button fabBtn = new Button("➕ 立即备份当前存档");
        fabBtn.setStyle("-fx-background-color: " + ADD_GAME_BTN_COLOR
                + "; -fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold; -fx-background-radius: 25px; -fx-padding: 12 24; -fx-cursor: hand;");
        DropShadow fabShadow = new DropShadow(10, Color.valueOf("#0000004d"));
        fabShadow.setOffsetY(3);
        fabBtn.setEffect(fabShadow);

        fabBtn.setOnAction(e -> {
            runWithErrorAlert("备份失败", () -> manager.updateArchive(gameName, currentBranch));
            try {
                String limitText = historyInput.getText();
                branchLimitCache.put(cacheKey, limitText);
                settings.getBranchLimits().put(cacheKey, limitText);
                SettingsManager.save(settings);
                int limit = Integer.parseInt(limitText);
                if (limit > 0) {
                    runWithErrorAlert("历史限制处理失败",
                            () -> manager.enforceHistoryLimit(gameName, currentBranch, limit));
                }
            } catch (NumberFormatException ex) {
            }
            openBranchDetailWithBranch(gameName, currentBranch);
        });

        StackPane contentStack = new StackPane();
        contentStack.getChildren().addAll(scrollPane, fabBtn);
        StackPane.setAlignment(fabBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(fabBtn, new Insets(0, 30, 30, 0));
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        branchDetailView.getChildren().addAll(topBar, controlBar, contentStack);
        switchView(branchDetailView);
    }

    private void applySimpleFastScroll(ScrollPane scrollPane, Region content) {
        final double SPEED_MULTIPLIER = 1.5;
        scrollPane.addEventFilter(ScrollEvent.SCROLL, event -> {
            double deltaY = event.getDeltaY();
            if (deltaY == 0)
                return;
            double contentHeight = content.getBoundsInLocal().getHeight();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();
            double scrollableHeight = contentHeight - viewportHeight;
            if (scrollableHeight > 0) {
                double currentVvalue = scrollPane.getVvalue();
                double newVvalue = currentVvalue - (deltaY * SPEED_MULTIPLIER) / scrollableHeight;
                scrollPane.setVvalue(Math.max(0, Math.min(1, newVvalue)));
            }
            event.consume();
        });
    }

    private void confirmAction(String header, String content, Runnable action) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("操作确认");
        alert.setHeaderText(header);
        alert.setContentText(content);

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                runWithErrorAlert(header + "失败", action);
            }
        });
    }

    private void runWithErrorAlert(String title, Runnable action) {
        try {
            action.run();
        } catch (ArchiveOperationException ex) {
            logger.log(Level.WARNING, title, ex);
            showErrorAlert(title, ex.getMessage() + "\n详情请查看 logs/app.log");
        } catch (Exception ex) {
            logger.log(Level.SEVERE, title, ex);
            showErrorAlert(title, "发生未知错误，请查看 logs/app.log");
        }
    }

    private void showErrorAlert(String title, String message) {
        String toastMessage = title + "：" + message;
        showToast(toastMessage);
    }

    private void initToastBanner() {
        toastBanner = new HBox(12);
        toastBanner.setVisible(false);
        toastBanner.setOpacity(0);
        toastBanner.setAlignment(Pos.CENTER_LEFT);
        toastBanner.setMaxWidth(Region.USE_PREF_SIZE);
        toastBanner.setPrefWidth(Region.USE_COMPUTED_SIZE);
        toastBanner.setMinWidth(Region.USE_PREF_SIZE);
        toastBanner.setMaxHeight(Region.USE_PREF_SIZE);
        toastBanner.setPrefHeight(Region.USE_COMPUTED_SIZE);
        toastBanner.setMinHeight(Region.USE_PREF_SIZE);
        toastBanner.setPadding(new Insets(10, 14, 10, 14));
        toastBanner.setStyle(
                "-fx-background-color: rgba(0, 0, 0, 0.65);" +
                "-fx-background-radius: 8px;" +
                "-fx-border-radius: 8px;" +
                "-fx-border-color: rgba(255, 255, 255, 0.15);");

        Label messageLabel = new Label();
        messageLabel.setStyle("-fx-text-fill: white; -fx-font-size: 13px;");
        messageLabel.setWrapText(false);
        messageLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        messageLabel.setMaxWidth(Region.USE_PREF_SIZE);
        messageLabel.setMaxHeight(Region.USE_PREF_SIZE);

        Button closeBtn = new Button("✕");
        closeBtn.setStyle(
                "-fx-background-color: transparent;" +
                "-fx-text-fill: white;" +
                "-fx-font-size: 12px;" +
                "-fx-cursor: hand;");
        closeBtn.setOnAction(e -> hideToast());

        toastBanner.getChildren().addAll(messageLabel, closeBtn);
        toastBanner.setUserData(messageLabel);

        toastFadeIn = new FadeTransition(Duration.millis(180), toastBanner);
        toastFadeIn.setFromValue(0);
        toastFadeIn.setToValue(1);

        toastFadeOut = new FadeTransition(Duration.millis(260), toastBanner);
        toastFadeOut.setFromValue(1);
        toastFadeOut.setToValue(0);
        toastFadeOut.setOnFinished(e -> toastBanner.setVisible(false));

        toastDelay = new PauseTransition(Duration.seconds(5));
        toastDelay.setOnFinished(e -> hideToast());
    }

    private void showToast(String message) {
        Label messageLabel = (Label) toastBanner.getUserData();
        messageLabel.setText(message);
        toastDelay.stop();
        toastFadeOut.stop();
        toastFadeIn.stop();

        toastBanner.setVisible(true);
        toastFadeIn.playFromStart();
        toastDelay.playFromStart();
    }

    private void hideToast() {
        toastDelay.stop();
        toastFadeIn.stop();
        toastFadeOut.playFromStart();
    }

    private void startAutoSaveService() {
        stopAutoSaveService();

        String mode = settings.getAutoSaveMode();
        if ("INTERVAL".equals(mode)) {
            startIntervalAutoSave();
        } else if ("WATCH".equals(mode)) {
            startWatchAutoSave();
        }
    }

    private void stopAutoSaveService() {
        if (autoSaveScheduler != null) {
            autoSaveScheduler.shutdownNow();
            autoSaveScheduler = null;
        }
        if (autoSaveWatchService != null) {
            try {
                autoSaveWatchService.close();
            } catch (Exception ex) {
            }
            autoSaveWatchService = null;
        }
        if (autoSaveWatchExecutor != null) {
            autoSaveWatchExecutor.shutdownNow();
            autoSaveWatchExecutor = null;
        }
        autoSaveDebounce.clear();
    }

    private void startIntervalAutoSave() {
        int intervalSeconds = settings.getAutoSaveIntervalSeconds();
        if (intervalSeconds <= 0) {
            return;
        }
        autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
        autoSaveScheduler.scheduleAtFixedRate(() -> {
            for (String gameName : manager.getGameNames()) {
                triggerAutoSaveForGame(gameName, "interval");
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private void startWatchAutoSave() {
        Map<Path, String> pathToGameName = new HashMap<>();
        for (String gameName : manager.getGameNames()) {
            String sourcePath = manager.getSourcePathForGame(gameName);
            if (sourcePath == null || sourcePath.isEmpty()) {
                continue;
            }
            Path path = Paths.get(sourcePath);
            if (Files.isDirectory(path)) {
                pathToGameName.put(path, gameName);
            }
        }

        if (pathToGameName.isEmpty()) {
            return;
        }

        try {
            autoSaveWatchService = Paths.get(".").getFileSystem().newWatchService();
            for (Path path : pathToGameName.keySet()) {
                path.register(autoSaveWatchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "自动监听初始化失败", e);
            showAutoSaveError("自动监听失败", "无法初始化自动监听，请查看 logs/app.log");
            return;
        }

        autoSaveWatchExecutor = Executors.newSingleThreadExecutor();
        autoSaveWatchExecutor.execute(() -> {
            while (true) {
                WatchKey key;
                try {
                    key = autoSaveWatchService.take();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (ClosedWatchServiceException ex) {
                    break;
                }

                Path dir = (Path) key.watchable();
                String gameName = pathToGameName.get(dir);
                boolean hasChanges = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    hasChanges = true;
                }

                if (hasChanges && gameName != null) {
                    triggerAutoSaveForGame(gameName, "watch");
                }

                boolean valid = key.reset();
                if (!valid) {
                    pathToGameName.remove(dir);
                    if (pathToGameName.isEmpty()) {
                        break;
                    }
                }
            }
        });
    }

    private void triggerAutoSaveForGame(String gameName, String source) {
        String targetBranch = "默认分支";
        if (saveToLatestBranch) {
            String latestBranch = manager.getLatestBranchNameForGame(gameName);
            if (latestBranch != null) {
                targetBranch = latestBranch;
            }
        }

        if (!shouldAutoSave(gameName, targetBranch)) {
            return;
        }

        try {
            manager.updateArchive(gameName, targetBranch);
        } catch (ArchiveOperationException ex) {
            logger.log(Level.WARNING, "自动保存失败: " + gameName + " / " + source, ex);
            showAutoSaveError("自动保存失败", ex.getMessage());
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "自动保存异常: " + gameName + " / " + source, ex);
            showAutoSaveError("自动保存异常", "发生未知错误，请查看 logs/app.log");
        }
    }

    private void showAutoSaveError(String title, String message) {
        Platform.runLater(() -> showErrorAlert(title, message));
    }

    private boolean shouldAutoSave(String gameName, String saveName) {
        int debounceSeconds = settings.getAutoSaveDebounceSeconds();
        if (debounceSeconds <= 0) {
            return true;
        }
        long now = System.currentTimeMillis();
        String key = gameName + "::" + saveName;
        Long last = autoSaveDebounce.get(key);
        long threshold = debounceSeconds * 1000L;
        if (last != null && now - last < threshold) {
            return false;
        }
        autoSaveDebounce.put(key, now);
        return true;
    }

    private void handleAddNewGame() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("请选择游戏存档的源文件夹");
        File selectedDir = dirChooser.showDialog(mainContentArea.getScene().getWindow());

        if (selectedDir != null) {

            Path selectedPath = selectedDir.toPath();
            if (selectedPath.getParent() == null || selectedPath.getNameCount() <= 1) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "为了您的数据安全，禁止直接选择磁盘根目录或一级目录作为存档源！\n请选择更深层级的具体游戏存档文件夹");
                alert.showAndWait();
                return;
            }
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("游戏存档已入库");
            dialog.setHeaderText("已选择存档路径: \n" + selectedDir.getAbsolutePath());
            dialog.setContentText("请填写游戏存档名字:");

            Optional<String> result = dialog.showAndWait();

            result.ifPresent(gameName -> {
                String trimmedName = gameName.trim();
                if (!trimmedName.isEmpty()) {
                    runWithErrorAlert("添加存档失败", () -> {
                        manager.addArchive(trimmedName, "默认分支", selectedDir.getAbsolutePath());
                        renderGameListView();
                    });
                }
            });
        }
    }

    private HBox createPageHeaderHBox(String titleText) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPrefHeight(85);
        header.setPadding(new Insets(15, 30, 15, 20));
        header.setStyle("-fx-background-color: " + HEADER_BG_COLOR + "; -fx-border-color: " + BORDER_COLOR
                + "; -fx-border-width: 0 0 1 0;");

        if (!titleText.isEmpty()) {
            Label titleLabel = new Label(titleText);
            titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #24292f;");
            header.getChildren().add(titleLabel);
        }

        return header;
    }

    private Label createPageHeader(String titleText) {
        Label titleLabel = new Label(titleText);
        titleLabel
                .setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #24292f; -fx-padding: 0 0 15 0;");
        return titleLabel;
    }

    // --- 辅助方法：生成自动换行的教程文本 ---
    private Label createWrapLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #57606a; -fx-line-spacing: 6px;");
        return label;
    }

    // --- 辅助方法：轻量级 Markdown 解析渲染器 ---
    private VBox parseReadmeToVBox() {
        VBox container = new VBox(10);
        container.setPadding(new Insets(30, 40, 60, 40));
        container.setStyle("-fx-background-color: transparent;");

        File readmeFile = new File("README.md");
        if (!readmeFile.exists()) {
            container.getChildren().add(createWrapLabel("未找到 README.md 文件\n请确保将 README.md 放在软件运行的同级目录下"));
            return container;
        }

        try {
            List<String> lines = Files.readAllLines(readmeFile.toPath(), StandardCharsets.UTF_8);
            StringBuilder paragraph = new StringBuilder();

            for (String line : lines) {
                String trimmedLine = line.trim();

                // 识别 H1 标题
                if (trimmedLine.startsWith("# ")) {
                    flushParagraph(container, paragraph);
                    Label l = new Label(trimmedLine.substring(2));
                    l.setStyle(
                            "-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: #24292f; -fx-padding: 0 0 10 0;");
                    container.getChildren().add(l);
                }
                // 识别 H2 标题
                else if (trimmedLine.startsWith("## ")) {
                    flushParagraph(container, paragraph);
                    Label l = new Label(trimmedLine.substring(3));
                    l.setStyle(
                            "-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #24292f; -fx-padding: 15 0 5 0;");
                    container.getChildren().add(l);
                }
                // 识别 H3 标题
                else if (trimmedLine.startsWith("### ")) {
                    flushParagraph(container, paragraph);
                    Label l = new Label(trimmedLine.substring(4));
                    l.setStyle(
                            "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #24292f; -fx-padding: 10 0 5 0;");
                    container.getChildren().add(l);
                }
                // 识别图片语法 ![alt](path)
                else if (trimmedLine.startsWith("![") && trimmedLine.contains("](") && trimmedLine.endsWith(")")) {
                    flushParagraph(container, paragraph);
                    String altText = trimmedLine.substring(trimmedLine.indexOf("[") + 1, trimmedLine.indexOf("]"));
                    String imgPath = trimmedLine.substring(trimmedLine.indexOf("](") + 2, trimmedLine.length() - 1);

                    File imgFile = new File(imgPath);
                    if (imgFile.exists()) {
                        // 如果硬盘上有这张图，加载并加上好看的阴影
                        javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(
                                new javafx.scene.image.Image(imgFile.toURI().toString()));
                        imageView.setFitWidth(650);
                        imageView.setPreserveRatio(true);
                        imageView.setEffect(new DropShadow(10, Color.valueOf("#00000020")));

                        VBox imgBox = new VBox(imageView);
                        imgBox.setPadding(new Insets(10, 0, 10, 0));
                        container.getChildren().add(imgBox);
                    } else {
                        // 找不到图片时显示文字占位符
                        Label l = new Label("[图片缺失: " + altText + " | 路径: " + imgPath + "]");
                        l.setStyle("-fx-text-fill: #8b949e; -fx-font-style: italic; -fx-padding: 10 0 10 0;");
                        container.getChildren().add(l);
                    }
                }
                // 遇到空行，说明一个段落结束
                else if (trimmedLine.isEmpty()) {
                    flushParagraph(container, paragraph);
                }
                // 普通文本累加到段落中 (保留换行符，以便列表正常显示)
                else {
                    paragraph.append(line).append("\n");
                }
            }
            // 循环结束后清空最后的缓存段落
            flushParagraph(container, paragraph);

        } catch (Exception e) {
            container.getChildren().add(createWrapLabel("读取 README.md 失败：" + e.getMessage()));
        }

        return container;
    }

    // 将累积的段落文本渲染为一个自动换行的 Label
    private void flushParagraph(VBox container, StringBuilder paragraph) {
        if (paragraph.length() > 0) {
            // 简单处理：把 Markdown 的加粗符号 ** 过滤掉，让纯文本更干净
            String text = paragraph.toString().replace("**", "");
            Label l = createWrapLabel(text.trim());
            container.getChildren().add(l);
            paragraph.setLength(0); // 清空缓存
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}