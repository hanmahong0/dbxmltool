package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import red.jiuzhou.dbxml.DirectoryManagerDialog;
import red.jiuzhou.dbxml.TabConfLoad;
import red.jiuzhou.relationship.XmlRelationshipAnalyzer;
import red.jiuzhou.ui.features.FeatureCategory;
import red.jiuzhou.ui.features.FeatureDescriptor;
import red.jiuzhou.ui.features.FeatureLauncher;
import red.jiuzhou.ui.features.FeatureRegistry;
import red.jiuzhou.ui.features.FeatureTaskExecutor;
import red.jiuzhou.ui.features.StageFeatureLauncher;
import red.jiuzhou.util.AIAssistant;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.IncrementalMenuJsonGenerator;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;


/**
 * @className: red.jiuzhou.ui.Dbxmltool.java
 * @description: 主程序
 * @author: yanxq
 * @date:  2025-04-15 20:42
 * @version V1.0
 */
@SpringBootApplication(scanBasePackages = {"red.jiuzhou.api", "red.jiuzhou.util"})
public class Dbxmltool extends Application {
    private ConfigurableApplicationContext springContext;


    private static final Logger log = LoggerFactory.getLogger(Dbxmltool.class);
    private final FeatureRegistry featureRegistry = FeatureRegistry.defaultRegistry();
    @Override
    public void init() {
        // 初始化 Spring 上下文
        springContext = new SpringApplicationBuilder(Dbxmltool.class).run();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        FeatureTaskExecutor.shutdown();
    }
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * JavaFX应用程序启动入口
     * 初始化主窗口,创建用户界面,配置菜单和工具栏
     *
     * 主要功能模块:
     * 1. 初始化数据库连接和AI助手
     * 2. 生成左侧目录菜单结构
     * 3. 创建顶部工具栏(包含各类功能按钮)
     * 4. 创建左右分割面板(左侧菜单树 + 右侧内容区)
     * 5. 配置Tab页切换监听器
     *
     * @param primaryStage JavaFX主窗口
     */
    @Override
    public void start(Stage primaryStage) {
        log.info("应用程序启动,当前数据库: {}", DatabaseUtil.getDbName());

        // 增量生成左侧菜单JSON配置
        IncrementalMenuJsonGenerator.createJsonIncrementally();

        // 当前选中的Tab名称
        AtomicReference<String> tabName = new AtomicReference<>("");

        // 创建主布局容器
        VBox root = new VBox();
        MenuTabPaneExample example = new MenuTabPaneExample();

        // 初始化AI助手 - 支持智能数据处理和转换
        try {
            AIAssistant aiAssistant = springContext.getBean(AIAssistant.class);
            example.setAiAssistant(aiAssistant);

            // 初始化AI主题转换服务
            red.jiuzhou.theme.AITransformService.initialize(aiAssistant);

            log.info("AI助手初始化成功");
        } catch (Exception e) {
            log.warn("AI助手初始化失败: {}", e.getMessage());
        }

        // 配置设计洞察功能网关 - 支持文件分析和数据可视化
        example.setFeatureGateway(new MenuTabPaneExample.FeatureGateway() {
            @Override
            public boolean supportsDesignerInsight() {
                // 启用设计洞察功能
                return true;
            }

            @Override
            public void openDesignerInsight(Path path) {
                // 打开设计洞察窗口分析指定文件
                if (path == null) {
                    return;
                }
                DesignerInsightStage stage = ensureDesignerInsightStage(primaryStage);
                if (stage != null) {
                    stage.inspectFile(path);
                } else {
                    log.warn("设计洞察窗口尚未就绪，无法打开文件: {}", path);
                }
            }
        });

        // 创建顶部Tab页容器
        TabPane tabPane = example.createTopPane();
        VBox rightControl = new PaginatedTable().createVbox(tabPane, new Tab(""));

        // 添加顶部工具栏(包含所有功能按钮)
        ToolBar toolBar = createToolBar(primaryStage, rightControl);
        root.getChildren().add(toolBar);

        // 创建左右分割面板
        SplitPane splitPane = new SplitPane();
        VBox leftControl = new VBox(new Label("菜单"));

        leftControl.setSpacing(8);
        leftControl.setPadding(new Insets(8));

        // 读取左侧菜单配置并创建菜单树
        String leftMenuJson = FileUtil.readUtf8String(YamlUtils.getProperty("file.homePath") + File.separator + "leftMenu.json");
        TreeView<String> leftMenu = example.createLeftMenu(leftMenuJson, tabPane);

        // ==================== 创建快捷操作按钮组 ====================
        // 提供常用的文件和目录操作功能
        HBox quickActions = new HBox(8);
        quickActions.setPadding(new Insets(0, 0, 6, 0));

        // 打开位置 - 在文件管理器中显示选中项
        Button openLocationBtn = new Button("📂 打开位置");
        openLocationBtn.setTooltip(new Tooltip("在文件资源管理器中打开选中文件或文件夹的位置"));

        // 复制路径 - 复制选中项的完整路径到剪贴板
        Button copyPathBtn = new Button("📋 复制路径");
        copyPathBtn.setTooltip(new Tooltip("复制选中文件或文件夹的完整路径到剪贴板"));

        // 打开文件 - 在应用内打开选中的文件
        Button openFileBtn = new Button("📄 打开文件");
        openFileBtn.setTooltip(new Tooltip("在应用程序内打开选中的文件进行编辑"));

        // 默认程序打开 - 使用系统默认程序打开文件
        Button openWithAppBtn = new Button("🚀 默认程序打开");
        openWithAppBtn.setTooltip(new Tooltip("使用系统默认关联程序打开选中的文件"));

        // 刷新目录 - 重新扫描目录结构
        Button refreshBtn = new Button("🔄 刷新目录");
        refreshBtn.setTooltip(new Tooltip("重新扫描目录结构,更新文件列表"));

        // ==================== 快捷操作按钮事件处理 ====================
        openLocationBtn.setOnAction(e -> example.revealSelection(leftMenu));
        copyPathBtn.setOnAction(e -> example.copySelectionPath(leftMenu));
        openFileBtn.setOnAction(e -> example.openSelectionInTab(leftMenu, tabPane));
        openWithAppBtn.setOnAction(e -> example.openSelectionWithDesktop(leftMenu));
        refreshBtn.setOnAction(e -> example.refreshTree(leftMenu));

        quickActions.getChildren().addAll(openLocationBtn, copyPathBtn, openFileBtn, openWithAppBtn, refreshBtn);

        // ==================== 左侧菜单选择监听器 ====================
        // 根据选中项的类型(文件夹/文件)动态启用或禁用快捷操作按钮
        leftMenu.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, selected) -> {
            boolean hasSelection = selected != null;
            // 只要有选中项,就可以打开位置和复制路径
            openLocationBtn.setDisable(!hasSelection);
            copyPathBtn.setDisable(!hasSelection);
            refreshBtn.setDisable(false);

            // 只有选中文件(叶子节点)时,才能打开文件
            boolean isFile = hasSelection && selected.getChildren().isEmpty();
            openFileBtn.setDisable(!isFile);
            openWithAppBtn.setDisable(!isFile);
        });

        // 初始状态:未选中任何项时,禁用相关按钮
        openLocationBtn.setDisable(true);
        copyPathBtn.setDisable(true);
        openFileBtn.setDisable(true);
        openWithAppBtn.setDisable(true);

        // 组装左侧面板
        leftControl.getChildren().add(quickActions);
        leftControl.getChildren().add(leftMenu);
        // 让菜单树占满可用空间
        VBox.setVgrow(leftMenu, Priority.ALWAYS);

        // ==================== 组装主界面 ====================
        // 添加左右分割面板
        splitPane.getItems().addAll(leftControl, rightControl);
        // 设置分割比例: 左侧30% / 右侧70%
        splitPane.setDividerPositions(0.3);
        // 让分割面板占满剩余空间
        VBox.setVgrow(splitPane, Priority.ALWAYS);
        root.getChildren().add(splitPane);

        // ==================== Tab页切换监听器 ====================
        // 当用户切换Tab时,自动刷新右侧内容区域
        tabPane.getSelectionModel().selectedItemProperty().addListener((observable1, oldTab, newTab) -> {
            if (newTab != null) {
                long startTime = System.currentTimeMillis();
                // 获取选中的 Tab 的名称
                String selectedTabName = newTab.getText();
                tabName.set(selectedTabName);
                log.info("切换到Tab: {} ", tabName);

                // 刷新右侧面板内容
                refreshRightControl(tabPane, newTab, rightControl);
                log.info("Tab切换耗时: {} ms", System.currentTimeMillis() - startTime);
            }
        });

        // ==================== 创建主场景并显示窗口 ====================
        Scene scene = new Scene(root, 1360, 660);
        primaryStage.setScene(scene);
        primaryStage.setTitle("DB_XML_TOOL - 数据库与XML转换工具");
        primaryStage.show();
        log.info("应用程序界面初始化完成");
    }

    /**
     * 刷新右侧内容面板
     * 当用户切换Tab页时,根据新Tab的内容重新加载和渲染右侧数据区域
     *
     * 刷新流程:
     * 1. 清空当前右侧面板的所有内容
     * 2. 根据新Tab获取对应的数据源
     * 3. 创建分页表格组件展示数据
     * 4. 设置表格占满可用空间
     *
     * @param tabPane Tab页容器
     * @param newTab 新选中的Tab页
     * @param rightControl 右侧内容面板
     */
    private void refreshRightControl(TabPane tabPane, Tab newTab, VBox rightControl) {
        // 清除右侧面板当前的所有内容
        rightControl.getChildren().clear();

        // 创建新的分页表格组件
        // 根据选中的Tab加载对应的数据并展示
        PaginatedTable paginatedTable = new PaginatedTable();

        // 让表格组件占满所有可用空间
        VBox.setVgrow(rightControl, Priority.ALWAYS);

        // 将新的分页表格添加到右侧面板
        rightControl.getChildren().add(paginatedTable.createVbox(tabPane, newTab));
    }


    /**
     * 创建主工具栏
     * 包含数据管理、查询工具、数据处理和安全管理四大功能模块
     * 所有按钮均配备图标和详细的工具提示，帮助用户快速理解功能
     *
     * @param primaryStage 主窗口
     * @param rightControl 右侧控制面板
     * @return 配置完成的工具栏
     */
    private ToolBar createToolBar(Stage primaryStage, VBox rightControl) {
        ToolBar toolBar = new ToolBar();

        // ==================== 数据管理模块 ====================
        // 提供基础的数据配置和目录管理功能

        // 映射关系配置按钮 - 打开数据库驱动的映射管理器
        Button confButton = new Button("🔗 数据对照");
        confButton.setTooltip(new Tooltip(
            "客户端服务端数据结构对照工具\n\n" +
            "🎯 核心功能:\n" +
            "• 自动识别client_*与server_*表的对应关系\n" +
            "• 字段级差异对比(类型/长度/注释/默认值)\n" +
            "• 双向数据同步(支持选择性字段同步)\n" +
            "• 内置枚举值查询统计功能\n\n" +
            "💡 适用场景:\n" +
            "→ 检查客户端与服务端表结构一致性\n" +
            "→ 快速定位配置差异导致的问题\n" +
            "→ 统一更新两端数据结构"
        ));

        // 字段关联分析按钮 - 专注于name字段的关联分析
        Button relationButton = new Button("🔍 关联分析");
        relationButton.setTooltip(new Tooltip(
            "智能分析name字段的关联关系\n\n" +
            "🎯 核心功能:\n" +
            "• 专注分析name字段的跨表引用\n" +
            "• 自动发现道具名、技能名、NPC名等关联\n" +
            "• 高精度匹配，避免噪音干扰\n\n" +
            "💡 适用场景:\n" +
            "→ 快速定位配置表间的名称引用关系\n" +
            "→ 排查无效名称引用(如拼写错误)\n" +
            "→ 理解游戏配置的核心对照关系\n\n" +
            "📊 分析范围: 仅分析name和*_name字段"
        ));

        // 目录管理按钮 - 管理数据文件存储目录
        Button addDirectoryBtn = new Button("📁 路径配置");
        addDirectoryBtn.setTooltip(new Tooltip(
            "配置数据文件存储路径\n\n" +
            "🎯 核心功能:\n" +
            "• 管理XML配置文件存放位置\n" +
            "• 设置导入导出根目录\n" +
            "• 配置多项目工作区\n\n" +
            "💡 适用场景:\n" +
            "→ 切换不同游戏版本的配置路径\n" +
            "→ 组织多个项目的数据文件\n" +
            "→ 配置团队共享的数据目录"
        ));

        // ==================== 查询工具模块 ====================
        // 提供各种数据查询和SQL处理功能

        // 新建查询按钮 - 创建自定义SQL查询
        Button newQueryBtn = new Button("⚡ SQL查询");
        newQueryBtn.setTooltip(new Tooltip(
            "多标签SQL查询编辑器\n\n" +
            "🎯 核心功能:\n" +
            "• 多标签页独立查询,互不干扰\n" +
            "• 支持SELECT/UPDATE/INSERT/DELETE\n" +
            "• 批量执行多条SQL(按分号分隔)\n" +
            "• 结果表格展示,支持导出\n\n" +
            "💡 适用场景:\n" +
            "→ 快速查询验证游戏配置数据\n" +
            "→ 批量修改测试数据\n" +
            "→ 统计分析游戏数值"
        ));

        // SQL转换按钮 - 不同数据库SQL语法转换
        Button sqlConverterBtn = new Button("🔄 数据转换");
        sqlConverterBtn.setTooltip(new Tooltip(
            "跨表/跨库数据转换工具\n\n" +
            "🎯 核心功能:\n" +
            "• 自动生成INSERT/UPDATE语句\n" +
            "• 支持字段映射和数据转换\n" +
            "• 批量选择ID进行转换\n" +
            "• 一键导出SQL脚本\n\n" +
            "💡 适用场景:\n" +
            "→ 客户端服务端数据互相转换\n" +
            "→ 测试数据迁移到正式环境\n" +
            "→ 不同数据库版本数据同步"
        ));

        // 数据同步按钮 - 数据库表之间的数据同步
        Button syncTableBtn = new Button("🔁 表同步");
        syncTableBtn.setTooltip(new Tooltip(
            "数据库表结构和数据同步\n\n" +
            "🎯 核心功能:\n" +
            "• 表结构差异对比\n" +
            "• 数据增量同步\n" +
            "• 支持跨库同步\n" +
            "• 同步日志追踪\n\n" +
            "💡 适用场景:\n" +
            "→ 开发库与测试库同步\n" +
            "→ 新增字段快速推全\n" +
            "→ 配置表批量更新"
        ));

        // ==================== 数据处理模块 ====================
        // 提供高级数据处理和批量操作功能

        // 搜索替换按钮 - 全局搜索和批量替换
        Button searchReplaceBtn = new Button("🔎 查找替换");
        searchReplaceBtn.setTooltip(new Tooltip(
            "全局搜索和批量替换工具\n\n" +
            "🎯 核心功能:\n" +
            "• 跨XML文件内容搜索\n" +
            "• 支持正则表达式匹配\n" +
            "• 批量替换预览确认\n" +
            "• 操作历史可回溯\n\n" +
            "💡 适用场景:\n" +
            "→ 批量修改配置项名称\n" +
            "→ 统一更新资源路径\n" +
            "→ 查找特定数值或ID"
        ));

        // 数据验证按钮 - 数据完整性和规则验证
        Button dataValidationBtn = new Button("✅ 数据校验");
        dataValidationBtn.setTooltip(new Tooltip(
            "智能数据完整性检查\n\n" +
            "🎯 核心功能:\n" +
            "• 检查必填字段缺失\n" +
            "• 验证数据类型和范围\n" +
            "• 检测外键引用错误\n" +
            "• 识别重复数据\n" +
            "• 生成问题清单报告\n\n" +
            "💡 适用场景:\n" +
            "→ 上线前配置完整性检查\n" +
            "→ 排查引用错误导致的Bug\n" +
            "→ 数据质量日常巡检"
        ));

        // 批量改写按钮 - 批量修改数据
        Button batchRewriteBtn = new Button("✏️ 批量编辑");
        batchRewriteBtn.setTooltip(new Tooltip(
            "条件批量数据修改工具\n\n" +
            "🎯 核心功能:\n" +
            "• 基于SQL条件筛选数据\n" +
            "• 支持公式和脚本计算\n" +
            "• 修改前预览影响范围\n" +
            "• 自动备份可一键回滚\n\n" +
            "💡 适用场景:\n" +
            "→ 游戏数值批量调整\n" +
            "→ 奖励配置统一修改\n" +
            "→ 测试数据快速清理"
        ));

        // ==================== 安全管理模块 ====================
        // 提供数据安全和灾难恢复功能

        // 紧急恢复按钮 - 数据紧急恢复工具
        Button emergencyRecoveryBtn = new Button("🚨 数据恢复");
        emergencyRecoveryBtn.setTooltip(new Tooltip(
            "数据紧急恢复中心\n\n" +
            "⚠️ 高危操作,请谨慎使用\n\n" +
            "🎯 核心功能:\n" +
            "• 从自动备份快速恢复\n" +
            "• 撤销危险批量操作\n" +
            "• 回滚到历史快照\n" +
            "• 恢复误删除数据\n\n" +
            "💡 适用场景:\n" +
            "→ 误删除重要配置数据\n" +
            "→ 批量操作导致数据错乱\n" +
            "→ 需要回退到某个版本"
        ));

        // 操作监控按钮 - 实时监控数据操作
        Button operationMonitorBtn = new Button("📊 操作日志");
        operationMonitorBtn.setTooltip(new Tooltip(
            "数据操作审计和监控\n\n" +
            "🎯 核心功能:\n" +
            "• 实时查看当前执行的操作\n" +
            "• SQL执行历史和性能统计\n" +
            "• 数据变更追踪记录\n" +
            "• 多人协作操作审计\n\n" +
            "💡 适用场景:\n" +
            "→ 追溯谁修改了某条数据\n" +
            "→ 分析慢查询性能问题\n" +
            "→ 监控团队操作规范性"
        ));

        // 备份管理按钮 - 数据备份管理
        Button backupManagerBtn = new Button("💾 备份中心");
        backupManagerBtn.setTooltip(new Tooltip(
            "数据备份策略管理\n\n" +
            "🎯 核心功能:\n" +
            "• 一键创建完整备份\n" +
            "• 配置自动备份计划\n" +
            "• 浏览备份历史版本\n" +
            "• 验证备份文件完整性\n" +
            "• 选择性恢复数据\n\n" +
            "💡 适用场景:\n" +
            "→ 重大版本更新前备份\n" +
            "→ 定期自动备份配置\n" +
            "→ 测试环境数据保护"
        ));

        // ==================== 按钮事件处理 ====================
        // 配置所有按钮的点击事件和业务逻辑

        // 映射关系 - 打开数据库驱动的映射管理器
        confButton.setOnAction(e -> {
            try {
                log.info("打开数据库映射管理器 - 自动加载所有client_*表");
                red.jiuzhou.ui.mapping.DatabaseMappingManager manager =
                    new red.jiuzhou.ui.mapping.DatabaseMappingManager(primaryStage);
                manager.show();
            } catch (Exception ex) {
                log.error("打开映射管理器失败", ex);
                showError("打开映射管理器失败: " + ex.getMessage());
            }
        });

        // 目录管理 - 打开目录配置对话框
        addDirectoryBtn.setOnAction(e -> {
            log.info("打开目录管理对话框");
            DirectoryManagerDialog dialog = new DirectoryManagerDialog(this::reloadAllDirectories);
            dialog.show(primaryStage);
        });

        // 字段关联 - 运行字段关联分析
        relationButton.setOnAction(event -> runRelationshipAnalysis(primaryStage, relationButton));

        // 新建查询 - 打开SQL查询编辑器
        newQueryBtn.setOnAction(e -> {
            try {
                log.info("打开SQL查询编辑器");
                new SqlQryApp().show();
            } catch (Exception ex) {
                log.error("打开SQL查询编辑器失败", ex);
                showError("打开SQL查询编辑器失败: " + ex.getMessage());
            }
        });

        // SQL转换 - 打开SQL转换工具
        sqlConverterBtn.setOnAction(e -> {
            try {
                log.info("打开SQL转换工具");
                new SQLConverterApp().show(primaryStage);
            } catch (Exception ex) {
                log.error("打开SQL转换工具失败", ex);
                showError("打开SQL转换工具失败: " + ex.getMessage());
            }
        });

        // 数据同步 - 打开表同步工具
        syncTableBtn.setOnAction(event -> {
            try {
                log.info("打开数据同步工具");
                new TableSyncApp(primaryStage).show();
            } catch (Exception e) {
                log.error("打开数据同步工具失败", e);
                showError("打开数据同步工具失败: " + e.getMessage());
            }
        });

        // 搜索替换 - 打开全局搜索替换工具
        searchReplaceBtn.setOnAction(event -> {
            try {
                log.info("打开搜索替换工具");
                new SearchReplaceDialog(primaryStage).show();
            } catch (Exception e) {
                log.error("打开搜索替换工具失败", e);
                showError("打开搜索替换工具失败: " + e.getMessage());
            }
        });

        // 数据验证 - 打开数据验证工具
        dataValidationBtn.setOnAction(event -> {
            try {
                log.info("打开数据验证工具");
                new DataValidationDialog(primaryStage).show();
            } catch (Exception e) {
                log.error("打开数据验证工具失败", e);
                showError("打开数据验证工具失败: " + e.getMessage());
            }
        });

        // 批量改写 - 打开批量数据改写工具
        batchRewriteBtn.setOnAction(event -> {
            try {
                log.info("打开批量改写工具");
                new BatchRewriteDialog(primaryStage).show();
            } catch (Exception e) {
                log.error("打开批量改写工具失败", e);
                showError("打开批量改写工具失败: " + e.getMessage());
            }
        });

        // 紧急恢复 - 打开紧急恢复工具(高危操作,谨慎使用)
        emergencyRecoveryBtn.setOnAction(event -> {
            try {
                log.warn("打开紧急恢复工具 - 高危操作");
                EmergencyRecoveryDialog.showRecovery(primaryStage);
            } catch (Exception e) {
                log.error("打开紧急恢复工具失败", e);
                showError("打开紧急恢复工具失败: " + e.getMessage());
            }
        });

        // 操作监控 - 打开操作监控面板
        operationMonitorBtn.setOnAction(event -> {
            try {
                log.info("打开操作监控面板");
                OperationMonitorPanel.showMonitor(primaryStage);
            } catch (Exception e) {
                log.error("打开操作监控面板失败", e);
                showError("打开操作监控面板失败: " + e.getMessage());
            }
        });

        // 备份管理 - 打开备份管理器
        backupManagerBtn.setOnAction(event -> {
            try {
                log.info("打开备份管理器");
                BackupManagerDialog.showManager(primaryStage);
            } catch (Exception e) {
                log.error("打开备份管理器失败", e);
                showError("打开备份管理器失败: " + e.getMessage());
            }
        });

        // ==================== 工具栏布局 ====================
        // 按照功能模块分组排列按钮,使用分隔符增强视觉层次

        // 创建状态标签 - 显示当前数据库连接状态
        Label statusLabel = new Label("📡 数据库: " + DatabaseUtil.getDbName());
        statusLabel.setStyle("-fx-padding: 0 10 0 10; -fx-font-size: 11px; -fx-text-fill: #666;");
        statusLabel.setTooltip(new Tooltip("当前连接的数据库名称"));

        // 创建弹性空间,将状态信息推到右侧
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // 组装工具栏:按功能模块分组
        // [数据管理] | [查询工具] | [数据处理] | [安全管理] ... [状态信息]
        toolBar.getItems().addAll(
            // 数据管理模块
            confButton, relationButton, addDirectoryBtn,
            new Separator(),
            // 查询工具模块
            newQueryBtn, sqlConverterBtn, syncTableBtn,
            new Separator(),
            // 数据处理模块
            searchReplaceBtn, dataValidationBtn, batchRewriteBtn,
            new Separator(),
            // 安全管理模块
            emergencyRecoveryBtn, operationMonitorBtn, backupManagerBtn,
            // 状态信息区域
            spacer, statusLabel
        );

        return toolBar;
    }

    /**
     * 构建映射配置文件的完整路径
     * 根据菜单项名称拼接出对应的JSON配置文件路径
     *
     * @param menuName 菜单项名称
     * @return 配置文件的完整路径
     */
    private String buildMenuPath(String menuName) {
        String basePath = YamlUtils.getProperty("file.homePath");
        return basePath + File.separator + menuName + ".json";
    }

    /**
     * 显示信息提示对话框
     * 用于向用户展示一般性信息或功能开发状态
     *
     * @param title 对话框标题
     * @param message 提示信息内容
     */
    public void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * 确保设计洞察窗口已初始化
     * 从功能注册表中查找并初始化设计洞察窗口,用于分析和可视化设计数据
     *
     * @param owner 父窗口
     * @return 设计洞察窗口实例,如果初始化失败则返回null
     */
    private DesignerInsightStage ensureDesignerInsightStage(Stage owner) {
        // 从功能注册表中查找分析类别的设计洞察功能
        FeatureDescriptor descriptor = featureRegistry
                .byCategory(FeatureCategory.ANALYTICS)
                .stream()
                .filter(d -> d.launcher() instanceof StageFeatureLauncher)
                .findFirst()
                .orElse(null);

        if (descriptor == null) {
            log.warn("设计洞察功能未注册");
            return null;
        }

        // 启动设计洞察窗口
        FeatureLauncher launcher = descriptor.launcher();
        StageFeatureLauncher stageLauncher = (StageFeatureLauncher) launcher;
        Stage stage = stageLauncher.ensureStage(owner);
        if (stage instanceof DesignerInsightStage) {
            return (DesignerInsightStage) stage;
        }

        log.warn("设计洞察窗口类型不匹配: {}", stage != null ? stage.getClass().getName() : "null");
        return null;
    }

    private Node buildFeatureCluster(FeatureCategory category, Stage owner) {
        List<FeatureDescriptor> descriptors = featureRegistry.byCategory(category);
        if (descriptors.isEmpty()) {
            return null;
        }

        HBox container = new HBox(6);
        container.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(category.displayName());
        label.setStyle("-fx-font-weight: bold;");
        container.getChildren().add(label);

        descriptors.stream()
                .map(descriptor -> createFeatureButton(descriptor, owner))
                .forEach(container.getChildren()::add);

        return container;
    }

    private Button createFeatureButton(FeatureDescriptor descriptor, Stage owner) {
        Button button = new Button(descriptor.displayName());
        button.setMnemonicParsing(false);
        button.getStyleClass().add("toolbar-feature-button");

        String description = descriptor.description();
        if (description != null && !description.trim().isEmpty()) {
            button.setTooltip(new Tooltip(description));
        }

        button.setOnAction(event -> descriptor.launcher().launch(owner));
        return button;
    }

    /**
     * 运行字段关联分析
     * 分析数据库中表与表之间、字段与字段之间的关联关系
     * 生成关联关系报告并可视化展示结果
     *
     * 功能特点:
     * - 自动检测外键关系
     * - 识别数据引用和依赖
     * - 支持取消长时间运行的分析
     * - 实时显示分析进度
     * - 生成详细的关系分析报告
     *
     * @param owner 父窗口
     * @param triggerButton 触发分析的按钮(用于在分析过程中禁用)
     */
    private void runRelationshipAnalysis(Stage owner, Button triggerButton) {
        // 禁用触发按钮,防止重复点击
        triggerButton.setDisable(true);

        // 创建进度指示器
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        Label messageLabel = new Label("正在分析字段关联，请稍候...");
        Label detailLabel = new Label("");
        detailLabel.setWrapText(true);
        detailLabel.setMaxWidth(320);
        Button cancelButton = new Button("取消");

        // 创建进度对话框
        VBox box = new VBox(12, indicator, messageLabel, detailLabel, cancelButton);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(18, 28, 18, 28));
        box.setMinWidth(300);

        Stage progressStage = new Stage();
        progressStage.initOwner(owner);
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setResizable(false);
        progressStage.setTitle("字段关联分析");
        progressStage.setScene(new Scene(box));

        // 取消标志,用于支持用户中断分析
        AtomicBoolean cancelFlag = new AtomicBoolean(false);

        Task<XmlRelationshipAnalyzer.RelationshipReport> task = new Task<XmlRelationshipAnalyzer.RelationshipReport>() {
            @Override
            protected XmlRelationshipAnalyzer.RelationshipReport call() {
                XmlRelationshipAnalyzer.AnalysisOptions options = XmlRelationshipAnalyzer.AnalysisOptions.create()
                    .withProgressCallback(path -> {
                        if (path != null) {
                            Platform.runLater(() -> detailLabel.setText(path.toString()));
                        }
                    })
                    .withCancellationSupplier(() -> cancelFlag.get());
                try {
                    return XmlRelationshipAnalyzer.analyzeCurrentDatabase(options);
                } catch (XmlRelationshipAnalyzer.AnalysisCancelledException ex) {
                    cancel(true);
                    throw new CancellationException("analysis_cancelled");
                }
            }
        };

        cancelButton.setOnAction(e -> {
            if (cancelFlag.compareAndSet(false, true)) {
                messageLabel.setText("正在取消，请稍候...");
                cancelButton.setDisable(true);
                task.cancel();
            }
        });

        progressStage.setOnCloseRequest(evt -> {
            if (task.isRunning()) {
                evt.consume();
                cancelButton.fire();
            }
        });

        task.setOnSucceeded(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            XmlRelationshipAnalyzer.RelationshipReport report = task.getValue();
            if (report.getRelationships().isEmpty()) {
                Alert alert = new Alert(AlertType.INFORMATION, "未检测到明显的字段关联。", ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            } else {
                RelationshipAnalysisStage stage = new RelationshipAnalysisStage(report);
                stage.initOwner(owner);
                stage.show();
            }
        });

        task.setOnFailed(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            Throwable ex = task.getException();
            if (ex instanceof CancellationException || (ex != null && ex.getCause() instanceof CancellationException)) {
                Alert alert = new Alert(AlertType.INFORMATION, "已取消字段关联分析。", ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            } else {
                log.error("字段关联分析失败", ex);
                Alert alert = new Alert(AlertType.ERROR,
                        "字段关联分析失败: " + (ex != null ? ex.getMessage() : "未知错误"), ButtonType.OK);
                alert.initOwner(owner);
                alert.showAndWait();
            }
        });

        task.setOnCancelled(evt -> {
            progressStage.close();
            triggerButton.setDisable(false);
            Alert alert = new Alert(AlertType.INFORMATION, "已取消字段关联分析。", ButtonType.OK);
            alert.initOwner(owner);
            alert.showAndWait();
        });

        Thread worker = new Thread(task, "xml-relationship-analyzer");
        worker.setDaemon(true);
        worker.start();

        progressStage.show();
    }

    /**
     * 重新加载所有目录配置
     * 当用户修改目录设置后,重新生成菜单配置JSON文件
     * 使目录结构的变更立即在左侧菜单中生效
     */
    private void reloadAllDirectories() {
        log.info("重新加载目录配置");
        IncrementalMenuJsonGenerator.createJsonIncrementally();
    }

    /**
     * 显示错误提示对话框
     * 用于向用户展示操作失败或异常信息
     *
     * @param message 错误信息内容
     */
    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("操作失败");
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void restartApplications() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "确定要重启应用吗？", ButtonType.YES, ButtonType.NO);
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.YES) {
                try {
                    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
                    File currentFile = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

                    // 检查文件是否为最新版本
                    long lastModified = currentFile.lastModified();
                    System.out.println("Current file last modified: " + new Date(lastModified));

                    List<String> command = new ArrayList<>();
                    command.add(javaBin);

                    if (currentFile.getName().endsWith(".jar")) {
                        // JAR 运行模式
                        command.add("-jar");
                        command.add(currentFile.getPath());
                    } else {
                        // IDE 运行模式
                        String mainClass = "red.jiuzhou.ui.Dbxmltool"; // 替换为你的 Main 类路径
                        command.add("-cp");
                        command.add(System.getProperty("java.class.path"));
                        command.add(mainClass);
                    }

                    // 启动新进程
                    Process process = new ProcessBuilder(command).inheritIO().start();

                    // 等待子进程启动完成
                    if (process.isAlive()) {
                        System.out.println("Application restarted successfully with latest code.");
                        System.exit(0); // 退出当前进程
                    } else {
                        throw new RuntimeException("Failed to start the new process.");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    Alert errorAlert = new Alert(Alert.AlertType.ERROR, "重启失败：" + e.getMessage(), ButtonType.OK);
                    errorAlert.showAndWait();
                }
            }
        });
    }

    /**
     * 递归构建映射关系菜单树
     * 根据JSON配置文件构建多层级的菜单结构,支持无限层级嵌套
     *
     * 菜单结构说明:
     * - 有子节点的项目显示为子菜单(Menu)
     * - 无子节点的项目显示为菜单项(MenuItem)
     * - 点击菜单项会打开对应的JSON配置编辑器
     *
     * @param menuItems 父菜单项列表(用于添加新的菜单项)
     * @param node 当前JSON节点(包含name和children属性)
     * @param fullPath 当前节点的完整路径(用于定位配置文件)
     */
    private void buildMenu(javafx.collections.ObservableList<MenuItem> menuItems, JSONObject node, String fullPath) {
        String name = node.getString("name");
        JSONArray children = node.getJSONArray("children");

        // 更新当前菜单的完整路径
        String currentPath = fullPath + File.separator + name;

        // 如果有子节点,创建子菜单并递归处理
        if (children != null && !children.isEmpty()) {
            Menu submenu = new Menu(name);
            for (int i = 0; i < children.size(); i++) {
                // 递归构建子菜单项
                buildMenu(submenu.getItems(), children.getJSONObject(i), currentPath);
            }
            menuItems.add(submenu);
        } else {
            // 叶子节点,创建可点击的菜单项
            MenuItem menuItem = new MenuItem(name);
            menuItem.setOnAction(event -> {
                // 打开JSON配置编辑器
                EditorStage.openJsonEditorWindow(YamlUtils.getProperty("file.homePath") + currentPath + ".json");
            });
            menuItems.add(menuItem);
        }
    }
}




