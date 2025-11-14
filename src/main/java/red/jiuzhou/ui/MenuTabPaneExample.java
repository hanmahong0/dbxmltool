package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @className: red.jiuzhou.ui.MenuTabPaneExample.java
 * @description: 菜单栏
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class MenuTabPaneExample {

    private static final Logger log = LoggerFactory.getLogger(MenuTabPaneExample.class);

    // 创建 TabPane
    public TabPane createTopPane() {
        TabPane tabPane = new TabPane();

        // 右键菜单
        ContextMenu contextMenu = new ContextMenu();

        // 关闭当前
        MenuItem closeCurrent = new MenuItem("关闭当前");
        closeCurrent.setOnAction(event -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                tabPane.getTabs().remove(selectedTab);
            }
        });

        // 关闭所有
        MenuItem closeAll = new MenuItem("关闭所有");
        closeAll.setOnAction(event -> {
            tabPane.getTabs().clear();
        });

        // 关闭其他
        MenuItem closeOthers = new MenuItem("关闭其他");
        closeOthers.setOnAction(event -> {
            Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
            if (selectedTab != null) {
                tabPane.getTabs().retainAll(selectedTab);
            }
        });

        // 添加菜单项
        contextMenu.getItems().addAll(closeCurrent, closeOthers, closeAll);

        // 右键点击时显示菜单
        tabPane.setOnContextMenuRequested(event -> {
            if (!tabPane.getTabs().isEmpty()) {
                contextMenu.hide(); // 先隐藏已有菜单，防止多个菜单同时存在
                contextMenu.show(tabPane, event.getScreenX(), event.getScreenY());
            }
        });
        return tabPane;
    }

    // 创建左侧菜单
    public TreeView<String> createLeftMenu(String json, TabPane tabPane) {
        JSONObject rootNode = JSONObject.parseObject(json);

        TreeItem<String> rootItem = new TreeItem<>(rootNode.getString("name"));
        TreeView<String> treeView = new TreeView<>(rootItem);
        treeView.setShowRoot(false);
        // 递归创建菜单项
        if (rootNode.containsKey("children")) {
            createMenuItems(rootNode.getJSONArray("children"), rootItem, treeView);
        }

        // 设置选择事件
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                // 如果是叶子节点，才创建Tab
                if (newValue.getChildren().isEmpty()) {
                    createTab(tabPane, newValue.getValue(), getTabFullPath(newValue));
                }
            }
        });

        return treeView;
    }


    // 递归创建菜单项并为每个 TreeItem 添加右键菜单
    private void createMenuItems(JSONArray children, TreeItem<String> parentItem, TreeView<String> treeView) {
        for (int i = 0; i < children.size(); i++) {
            JSONObject childNode = children.getJSONObject(i);
            TreeItem<String> item = new TreeItem<>(childNode.getString("name"));
            parentItem.getChildren().add(item);
            // 递归调用
            if (childNode.containsKey("children")) {
                createMenuItems(childNode.getJSONArray("children"), item, treeView);
            }

        }
    }
    // 创建新的Tab
    private void createTab(TabPane tabPane, String menuItem, String fullPath) {
        boolean tabExists = false;
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().equals(menuItem)) {
                tabExists = true;
                tab.setUserData(fullPath);
                tabPane.getSelectionModel().select(tab);
                break;
            }
        }

        if (!tabExists) {
            Tab newTab = new Tab(menuItem, new Label(""));
            newTab.setUserData(fullPath);
            tabPane.getTabs().add(newTab);
            tabPane.getSelectionModel().select(newTab);

        }
    }
    private String getTabFullPath(TreeItem<String> treeItem) {
        return  getParetnPath(treeItem, treeItem.getValue());
    }

    private String getParetnPath(TreeItem<String> treeItem, String cpath){
        TreeItem<String> parentTreeItem = treeItem.getParent();
        if(parentTreeItem != null){
            String path = parentTreeItem.getValue();
            cpath = path + File.separator + cpath;
            return getParetnPath(parentTreeItem, cpath);

        }
        return cpath.replace("Root" + File.separator, "");
    }

    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("发生异常");
        alert.setContentText(message);
        alert.showAndWait();
    }
}
