/*
 * Copyright (c) 2024 - The MegaMek Team. All Rights Reserved.
 *
 * This file is part of MegaMekLab.
 *
 * MegaMek is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MegaMek is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MegaMek. If not, see <http://www.gnu.org/licenses/>.
 */

package megameklab.ui;

import megamek.MegaMek;
import megamek.client.ui.swing.util.UIUtil;
import megamek.common.Entity;
import megamek.common.preference.PreferenceManager;
import megameklab.MMLConstants;
import megameklab.MegaMekLab;
import megameklab.ui.dialog.UiLoader;
import megameklab.ui.mek.BMMainUI;
import megameklab.ui.util.ExitOnWindowClosingListener;
import megameklab.ui.util.TabStateUtil;
import megameklab.util.CConfig;
import megameklab.util.MMLFileDropTransferHandler;
import megameklab.util.UnitUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Replaces {@link MegaMekLabMainUI} as the top-level window for MML.
 * Holds several {@link MegaMekLabMainUI}s as tabs, allowing many units to be open at once.
 */
public class MegaMekLabTabbedUI extends JFrame implements MenuBarOwner {
    private final List<MegaMekLabMainUI> editors = new ArrayList<>();

    private final ReopenTabStack closedEditors = new ReopenTabStack();

    private final JTabbedPane tabs = new JTabbedPane();

    private final MenuBar menuBar;

    /**
     * Constructs a new MegaMekLabTabbedUI instance, which serves as the main tabbed UI
     * for managing multiple MegaMekLabMainUI editors. Automatically initializes a default
     * BMMainUI instance if no entities are provided.
     *
     * @param entities A variable number of MegaMekLabMainUI instances that will be added
     *                 as tabs to the UI. If no entities are provided, a default BMMainUI
     *                 instance will be created and added.
     */
    public MegaMekLabTabbedUI(MegaMekLabMainUI... entities) {
        super("MegaMekLab");

        // If there are more tabs than can fit, show a scroll bar instead of stacking tabs in multiple rows
        // This is a matter of preference, I could be convinced to switch this.
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);


        // Add the given editors as tabs, then add the New Tab Button.
        for (MegaMekLabMainUI e : entities) {
            addTab(e);
        }
        addNewTabButton();

        setContentPane(tabs);

        menuBar = new MenuBar(this);
        setJMenuBar(menuBar);

        // Enable opening unit and mul files by drag-and-drop
        setTransferHandler(new MMLFileDropTransferHandler(this));

        // Remember the size and position of the window from last time MML was launched
        pack();
        restrictToScreenSize();
        setLocationRelativeTo(null);
        CConfig.getMainUiWindowSize(this).ifPresent(this::setSize);
        CConfig.getMainUiWindowPosition(this).ifPresent(this::setLocation);

        // ...and save that size and position on exit
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new ExitOnWindowClosingListener(this));
        setExtendedState(CConfig.getIntParam(CConfig.GUI_FULLSCREEN));
    }

    /**
     * Retrieves the currently selected editor from the tabbed user interface.
     *
     * @return The currently selected MegaMekLabMainUI instance, which represents the
     * active editor in the tabbed UI.
     */
    public MegaMekLabMainUI currentEditor() {
        return editors.get(tabs.getSelectedIndex());
    }


    /**
     * Updates the name of the currently selected tab in the tabbed user interface.
     * Should typically be called when the name of the unit being edited changes.
     *
     * @param tabName The new name to be set for the currently selected tab.
     */
    public void setTabName(String tabName) {
        // ClosableTab is a label with the unit name, and a close button.
        // If we didn't need that close button, this could be tabs.setTitleAt
        tabs.setTabComponentAt(tabs.getSelectedIndex(), new ClosableTab(tabName, currentEditor()) );
    }

    /**
     * Adds a new editor tab to the tabbed UI. This includes adding the editor
     * to the internal editor collection, refreshing it, setting the ownership,
     * and adding the tab to the tabs UI.
     *
     * If there already exists a New Tab button, the new tab is placed before it.
     *
     * @param editor The MegaMekLabMainUI instance to be added as a new tab.
     */
    private void addTab(MegaMekLabMainUI editor) {
        MegaMekLabMainUI newTab = null;
        NewTabButton newTabButton = null;
        if (tabs.getTabCount() > 0 && tabs.getTabComponentAt(tabs.getTabCount() - 1) instanceof NewTabButton ntb) {
            newTabButton = ntb;
            newTab = editors.get(editors.size() - 1);
            tabs.removeTabAt(tabs.getTabCount() - 1);
            editors.remove(editors.size() - 1);
        }

        editors.add(editor);
        editor.refreshAll();
        editor.setOwner(this);
        tabs.addTab(editor.getEntity().getDisplayName(), editor.getContentPane());
        // See ClosableTab later in this file for what's going on here.
        tabs.setTabComponentAt(tabs.getTabCount() - 1, new ClosableTab(editor.getEntity().getDisplayName(), editor));

        if (newTab != null) {
            tabs.addTab("+", newTab.getContentPane());
            tabs.setTabComponentAt(tabs.getTabCount() - 1, newTabButton);
            tabs.setEnabledAt(tabs.getTabCount() - 1, false);
            editors.add(newTab);
        }
    }

    /**
     * Similar to addTab above, this adds a blank Mek editor, but with the name "➕"
     * so that it looks like a button for creating a new tab.
     * <p>
     * The JTabbedPane doesn't come with any functionality for the user adding/removing tabs out of the box,
     * so this is how we fake it.
     */
    private void addNewTabButton() {
        var editor = new BMMainUI(false, false);
        editors.add(editor);
        editor.refreshAll();
        editor.setOwner(this);
        tabs.addTab("➕", editor.getContentPane());
        tabs.setTabComponentAt(tabs.getTabCount() - 1, new NewTabButton());
        tabs.setEnabledAt(tabs.getTabCount() - 1, false);
    }

    /**
     * The name is misleading, this is actually the Switch Unit Type operation!
     * Replaces the current editor with a new blank one of the given unit type.
     * Disposes of the old editor UI after the new one is initialized.
     *
     * @param type       the type of unit to load for the new editor UI
     * @param primitive  whether the unit is primitive
     * @param industrial whether the unit is an IndustrialMek
     */
    private void newUnit(long type, boolean primitive, boolean industrial) {
        var oldUi = editors.get(tabs.getSelectedIndex());
        var newUi = UiLoader.getUI(type, primitive, industrial);
        editors.set(tabs.getSelectedIndex(), newUi);
        tabs.setComponentAt(tabs.getSelectedIndex(), newUi.getContentPane());
        tabs.setTabComponentAt(tabs.getSelectedIndex(), new ClosableTab(newUi.getEntity().getDisplayName(), newUi));
        tabs.setEnabledAt(tabs.getSelectedIndex(), true);
        oldUi.dispose();
    }

    /**
     * The name is misleading, this is actually the Switch Unit Type operation!
     * Replaces the current editor with a new blank one of the given unit type.
     * Disposes of the old editor UI after the new one is initialized.
     *
     * @param type       the type of unit to load for the new editor UI
     * @param primitive  whether the unit is primitive
     */
    @Override
    public void newUnit(long type, boolean primitive) {
        newUnit(type, primitive, false);
    }


    /**
     * Adds a new tab with the given unit to the tabbed user interface.
     *
     * @param entity   The Entity object representing the unit to be added.
     * @param filename The name of the file associated with the unit being added.
     */
    public void addUnit(Entity entity, String filename) {
        // Create a new "new tab" button, since we're about to replace the existing one
        addNewTabButton();
        // Select the old "new tab" button...
        tabs.setSelectedIndex(tabs.getTabCount() - 2);
        // ...and replace it, since newUnit is actually the Switch Unit Type operation.
        newUnit(UnitUtil.getEditorTypeForEntity(entity), entity.isPrimitive(), entity.isIndustrialMek());

        currentEditor().setEntity(entity, filename);
        currentEditor().reloadTabs();
        currentEditor().refreshAll();
        // Set the tab name
        tabs.setTabComponentAt(tabs.getSelectedIndex(), new ClosableTab(entity.getDisplayName(), currentEditor()));
    }

    @Override
    public boolean exit() {
        if (!currentEditor().safetyPrompt()) {
            return false;
        }

        CConfig.setParam(CConfig.GUI_FULLSCREEN, Integer.toString(getExtendedState()));
        CConfig.setParam(CConfig.GUI_PLAF, UIManager.getLookAndFeel().getClass().getName());
        CConfig.writeMainUiWindowSettings(this);
        CConfig.saveConfig();
        PreferenceManager.getInstance().save();
        MegaMek.getMMPreferences().saveToFile(MMLConstants.MM_PREFERENCES_FILE);
        MegaMekLab.getMMLPreferences().saveToFile(MMLConstants.MML_PREFERENCES_FILE);

        if (CConfig.getStartUpType() == MMLStartUp.RESTORE_TABS) {
            try {
                TabStateUtil.saveTabState(editors.stream().limit(editors.size() - 1).toList());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    private void restrictToScreenSize() {
        DisplayMode currentMonitor = getGraphicsConfiguration().getDevice().getDisplayMode();
        int scaledMonitorW = UIUtil.getScaledScreenWidth(currentMonitor);
        int scaledMonitorH = UIUtil.getScaledScreenHeight(currentMonitor);
        int w = Math.min(getSize().width, scaledMonitorW);
        int h = Math.min(getSize().height, scaledMonitorH);
        setSize(new Dimension(w, h));
    }

    public void newTab() {
        tabs.setEnabledAt(tabs.getTabCount() - 1, true);
        tabs.setSelectedIndex(tabs.getTabCount() - 1);
        tabs.setTabComponentAt(
            tabs.getTabCount() - 1,
            new ClosableTab(currentEditor().getEntity().getDisplayName(), currentEditor())
        );

        addNewTabButton();
    }

    /**
     * Deletes the current tab.
     * This does not issue the safety prompt, it is up to the caller to do so!
     */
    public void closeCurrentTab() {
        closeTabAt(tabs.getSelectedIndex());
    }

    private void closeTabAt(int position) {
        if (tabs.getTabCount() <= 2) {
            MegaMekLabTabbedUI.this.dispatchEvent(new WindowEvent(MegaMekLabTabbedUI.this, WindowEvent.WINDOW_CLOSING));
        }

        var editor = editors.get(position);

        tabs.remove(editor.getContentPane());
        if (tabs.getSelectedIndex() == tabs.getTabCount() - 1) {
            tabs.setSelectedIndex(tabs.getSelectedIndex() - 1);
        }
        editors.remove(editor);
        closedEditors.push(editor);
        // Tell the menu bar to enable the "reopen tab" shortcut
        refreshMenuBar();
    }

    public void reopenTab() {
        var editor = closedEditors.pop();
        if (editor != null) {
            addTab(editor);
            tabs.setSelectedIndex(tabs.getTabCount() - 2);
            refreshMenuBar();
        }
    }

    public boolean hasClosedTabs() {
        return !closedEditors.isEmpty();
    }

    @Override
    public JFrame getFrame() {
        return this;
    }

    @Override
    public Entity getEntity() {
        return currentEditor().getEntity();
    }

    @Override
    public String getFileName() {
        return currentEditor().getFileName();
    }

    @Override
    public boolean hasEntityNameChanged() {
        return currentEditor().hasEntityNameChanged();
    }

    @Override
    public void refreshMenuBar() {
        menuBar.refreshMenuBar();
    }

    @Override
    public MenuBar getMMLMenuBar() {
        return menuBar;
    }


    /**
     * Represents a button used for creating new tabs in the MegaMekLabTabbedUI interface.
     * Used to mimic functionality for adding new tabs in a tabbed user interface.
     * Normally this tab should be disabled so it can't be navigated to, then when the + button is clicked
     * the tab is replaced with a normal {@link ClosableTab}.
     */
    private class NewTabButton extends JPanel {
        public NewTabButton() {
            setOpaque(false);
            var button = new JButton("➕");
            button.setForeground(Color.GREEN);
            button.setFont(Font.getFont("Symbola"));
            button.setFocusable(false);
            button.setBorder(BorderFactory.createEmptyBorder());

            button.addActionListener(e -> {
                newTab();
            });

            add(button);
        }
    }

    /**
     * Represents a custom tab component for use in a tabbed user interface, designed to display
     * the name of a unit and provide a close button for removing the associated tab.
     * The close button can be shift-clicked to skip the editor's safety prompt.
     * This class extends JPanel and is initialized with a unit name and its associated editor instance.
     */
    private class ClosableTab extends JPanel {
        JLabel unitName;
        JButton closeButton;
        MegaMekLabMainUI editor;

        public ClosableTab(String name, MegaMekLabMainUI mainUI) {
            unitName = new JLabel(name);
            editor = mainUI;

            setOpaque(false);

            closeButton = new JButton("❌");
            closeButton.setFont(Font.getFont("Symbola"));
            closeButton.setForeground(Color.RED);
            closeButton.setFocusable(false);
            closeButton.setBorder(BorderFactory.createEmptyBorder());
            closeButton.setToolTipText("Shift-click to skip the save confirmation dialog");
            add(unitName);
            add(closeButton);
            closeButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.isShiftDown() || editor.safetyPrompt()) {
                        closeTabAt(editors.indexOf(editor));
                    }
                }
            });
        }
    }

    /**
     * ReopenTabStack is a utility class that manages a fixed-capacity stack of closed
     * MegaMekLabMainUI editors. It allows for storing references to recently closed
     * editors and retrieving them in reverse order of their closure, resembling
     * a "reopen tab" functionality.
     * <p>
     * This stack maintains a circular buffer of references with a maximum capacity
     * defined by the constant STACK_CAPACITY. If the capacity is exceeded, the oldest
     * editor will be disposed of and removed to make room for new entries.
     */
    private static class ReopenTabStack {
        public static final int STACK_CAPACITY = 20;

        private final MegaMekLabMainUI[] closedEditors = new MegaMekLabMainUI[STACK_CAPACITY];
        private int size = 0;
        private int start = 0;

        public void push(MegaMekLabMainUI editor) {
            int pos = start + size % closedEditors.length;
            if (size == closedEditors.length) {
                closedEditors[pos].dispose();
                start++;
                start %= closedEditors.length;
            } else {
                size++;
            }
            closedEditors[pos] = editor;
        }

        public MegaMekLabMainUI pop() {
            if (size == 0) {
                return null;
            }
            int pos = start + size - 1 % closedEditors.length;
            var ret = closedEditors[pos];

            closedEditors[pos] = null;
            size--;

            return ret;
        }

        public boolean isEmpty() {
            return size == 0;
        }
    }
}