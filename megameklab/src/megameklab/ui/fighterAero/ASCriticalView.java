/*
 * MegaMekLab - Copyright (C) 2008
 * Copyright (c) 2021 - The MegaMek Team. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later  version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License
 * for more details.
 */
package megameklab.ui.fighterAero;

import java.util.ArrayList;
import java.util.Vector;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;

import megamek.common.Aero;
import megamek.common.CriticalSlot;
import megamek.common.LocationFullException;
import megamek.common.Mounted;
import megamek.common.WeaponType;
import megamek.common.verifier.TestAero;
import megamek.logging.MMLogger;
import megameklab.ui.EntitySource;
import megameklab.ui.PopupMessages;
import megameklab.ui.util.BAASBMDropTargetCriticalList;
import megameklab.ui.util.CritCellUtil;
import megameklab.ui.util.IView;
import megameklab.ui.util.RefreshListener;
import megameklab.util.UnitUtil;

/**
 * The Crit Slots view for a Fighter (Aerospace and Conventional)
 *
 * Original author - jtighe (torren@users.sourceforge.net)
 *
 * @author arlith
 * @author neoancient
 * @author Simon (Juliez)
 */
public class ASCriticalView extends IView {
    private static final MMLogger logger = MMLogger.create(ASCriticalView.class);

    private BAASBMDropTargetCriticalList<String> noseCrits;
    private BAASBMDropTargetCriticalList<String> leftWingCrits;
    private BAASBMDropTargetCriticalList<String> rightWingCrits;
    private BAASBMDropTargetCriticalList<String> aftCrits;
    private BAASBMDropTargetCriticalList<String> fuselageCrits;

    private final JLabel noseSpace = new JLabel();
    private final JLabel leftSpace = new JLabel();
    private final JLabel rightSpace = new JLabel();
    private final JLabel aftSpace = new JLabel();

    private final JButton btnCopyLW = new JButton("Copy from Left Wing");
    private final JButton btnCopyRW = new JButton("Copy from Right Wing");

    private RefreshListener refreshListener;

    public ASCriticalView(EntitySource eSource, RefreshListener refreshListener) {
        super(eSource);
        this.refreshListener = refreshListener;
        setUI();
        fillSlots();
    }

    private void setUI() {
        noseCrits = new BAASBMDropTargetCriticalList<>(
                new ArrayList<>(), eSource, refreshListener, true, this);
        noseCrits.setPrototypeCellValue(CritCellUtil.CRITCELL_WIDTH_STRING);
        leftWingCrits = new BAASBMDropTargetCriticalList<>(
                new ArrayList<>(), eSource, refreshListener, true, this);
        leftWingCrits.setPrototypeCellValue(CritCellUtil.CRITCELL_WIDTH_STRING);
        rightWingCrits = new BAASBMDropTargetCriticalList<>(
                new ArrayList<>(), eSource, refreshListener, true, this);
        rightWingCrits.setPrototypeCellValue(CritCellUtil.CRITCELL_WIDTH_STRING);
        aftCrits = new BAASBMDropTargetCriticalList<>(
                new ArrayList<>(), eSource, refreshListener, true, this);
        aftCrits.setPrototypeCellValue(CritCellUtil.CRITCELL_WIDTH_STRING);
        fuselageCrits = new BAASBMDropTargetCriticalList<>(
                new ArrayList<>(), eSource, refreshListener, true, this);
        fuselageCrits.setPrototypeCellValue(CritCellUtil.CRITCELL_WIDTH_STRING);

        Box mainPanel = Box.createHorizontalBox();
        Box leftPanel = Box.createVerticalBox();
        Box middlePanel = Box.createVerticalBox();
        Box rightPanel = Box.createVerticalBox();
        Box leftWingPanel = Box.createVerticalBox();
        Box rightWingPanel = Box.createVerticalBox();
        Box nosePanel = Box.createVerticalBox();
        Box aftPanel = Box.createVerticalBox();
        Box fuselagePanel = Box.createVerticalBox();

        noseSpace.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        leftSpace.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        rightSpace.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        aftSpace.setAlignmentX(JComponent.CENTER_ALIGNMENT);

        nosePanel.setBorder(CritCellUtil.locationBorder("Nose"));
        leftWingPanel.setBorder(CritCellUtil.locationBorder("Left Wing"));
        rightWingPanel.setBorder(CritCellUtil.locationBorder("Right Wing"));
        aftPanel.setBorder(CritCellUtil.locationBorder("Aft"));
        fuselagePanel.setBorder(CritCellUtil.locationBorder("Fuselage"));

        nosePanel.add(noseCrits);
        nosePanel.add(noseSpace);
        leftWingPanel.add(leftWingCrits);
        leftWingPanel.add(leftSpace);
        leftWingPanel.add(btnCopyRW);
        rightWingPanel.add(rightWingCrits);
        rightWingPanel.add(rightSpace);
        rightWingPanel.add(btnCopyLW);
        aftPanel.add(aftCrits);
        aftPanel.add(aftSpace);
        fuselagePanel.add(fuselageCrits);

        btnCopyLW.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        btnCopyRW.setAlignmentX(JComponent.CENTER_ALIGNMENT);
        btnCopyLW.addActionListener(ev -> copyLocation(Aero.LOC_LWING, Aero.LOC_RWING));
        btnCopyRW.addActionListener(ev -> copyLocation(Aero.LOC_RWING, Aero.LOC_LWING));

        leftPanel.add(leftWingPanel);
        middlePanel.add(nosePanel);
        middlePanel.add(fuselagePanel);
        middlePanel.add(aftPanel);
        rightPanel.add(rightWingPanel);

        mainPanel.add(leftPanel);
        mainPanel.add(middlePanel);
        mainPanel.add(rightPanel);
        add(mainPanel);
    }

    private void fillSlots() {
        for (int location = 0; location < getAero().locations(); location++) {
            if (location == Aero.LOC_WINGS) {
                continue;
            }
            Vector<String> critNames = new Vector<>(5);
            int numWeapons = 0;
            for (int slot = 0; slot < getAero().getNumberOfCriticals(location); slot++) {
                CriticalSlot cs = getAero().getCritical(location, slot);
                if ((cs != null) && (cs.getType() == CriticalSlot.TYPE_EQUIPMENT)) {
                    Mounted<?> mounted = cs.getMount();
                    if (mounted == null) {
                        // Critical didn't get removed. Remove it now.
                        getAero().setCritical(location, slot, null);
                        logger.warn(getAero().getLocationName(location) +
                                " equipment in slot " + slot + " had not been cleanly removed!");
                        continue;
                    }
                    if (mounted.isWeaponGroup()) {
                        continue;
                    }
                    if (mounted.getType() instanceof WeaponType) {
                        numWeapons++;
                    }
                    critNames.add(mounted.getName() + ":" + slot);
                }
            }

            if (critNames.isEmpty()) {
                // In a completely empty location, display a single empty slot
                critNames.add(CritCellUtil.EMPTY_CRITCELL_TEXT);
            }

            critListFor(location).setListData(critNames);
            critListFor(location).setVisibleRowCount(critNames.size());
            critListFor(location).setName(location + "");

            String usedCritText = "Weapons: " + numWeapons + " / " + availableSpace(location);
            if (location == Aero.LOC_NOSE) {
                noseSpace.setText(usedCritText);
            } else if (location == Aero.LOC_LWING) {
                leftSpace.setText(usedCritText);
            } else if (location == Aero.LOC_RWING) {
                rightSpace.setText(usedCritText);
            } else if (location == Aero.LOC_AFT) {
                aftSpace.setText(usedCritText);
            }
        }
    }

    public void updateRefresh(RefreshListener refresh) {
        this.refreshListener = refresh;
        noseCrits.setRefresh(refresh);
        leftWingCrits.setRefresh(refresh);
        rightWingCrits.setRefresh(refresh);
        aftCrits.setRefresh(refresh);
        fuselageCrits.setRefresh(refresh);
    }

    public void refresh() {
        removeAll();
        setUI();
        fillSlots();
    }

    private String availableSpace(int location) {
        if (location == Aero.LOC_FUSELAGE) {
            return "unlimited";
        }
        int[] availSpace = TestAero.availableSpace(getAero());
        try {
            return availSpace[location] + "";
        } catch (Exception ex) {
            logger.error("Couldn't determine available crit space!", ex);
            return "?";
        }
    }

    private BAASBMDropTargetCriticalList<String> critListFor(int location) {
        switch (location) {
            case Aero.LOC_NOSE:
                return noseCrits;
            case Aero.LOC_LWING:
                return leftWingCrits;
            case Aero.LOC_RWING:
                return rightWingCrits;
            case Aero.LOC_AFT:
                return aftCrits;
            default:
                return fuselageCrits;
        }
    }

    private void copyLocation(int from, int to) {
        try {
            UnitUtil.copyLocationEquipment(getAero(), from, to);
        } catch (LocationFullException ex) {
            PopupMessages.showLocationFullError(null);
        }
        refreshListener.scheduleRefresh();
    }
}
