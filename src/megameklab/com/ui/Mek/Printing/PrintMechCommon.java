/*
 * MegaMekLab - Copyright (C) 2017 - The MegaMek Team
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 */
package megameklab.com.ui.Mek.Printing;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.kitfox.svg.Path;
import com.kitfox.svg.Rect;
import com.kitfox.svg.SVGDiagram;
import com.kitfox.svg.SVGElement;
import com.kitfox.svg.SVGElementException;
import com.kitfox.svg.SVGException;
import com.kitfox.svg.Text;
import com.kitfox.svg.Tspan;
import com.kitfox.svg.animation.AnimationElement;

import megamek.common.AmmoType;
import megamek.common.CriticalSlot;
import megamek.common.Engine;
import megamek.common.Entity;
import megamek.common.Mech;
import megamek.common.MiscType;
import megamek.common.Mounted;
import megamek.common.logging.LogLevel;
import megameklab.com.MegaMekLab;
import megameklab.com.util.ImageHelper;
import megameklab.com.util.RecordSheetEquipmentLine;
import megameklab.com.util.UnitUtil;

/**
 * @author Neoancient
 *
 */
public class PrintMechCommon implements Printable {
    
    private enum MechTextElements {
        TYPE ("type", m -> m.getChassis() + " " + m.getModel()),
        MP_WALK("mpWalk", m -> {
            if (m.hasTSM()) {
                return m.getWalkMP() + " [" + (m.getWalkMP() + 1) + "]";
            } else {
                return Integer.toString(m.getWalkMP());
            }
        }),
        MP_RUN("mpRun", m -> formatRunMp(m)),
        MP_JUMP("mpJump", m -> {
            if (m.hasUMU()) {
                return Integer.toString(m.getActiveUMUCount());
            } else {
                return Integer.toString(m.getJumpMP());
            }
        }),
        TONNAGE("tonnage", m -> Integer.toString((int) m.getWeight())),
        TECH_BASE("techBase", m -> formatTechBase(m)),
        RULES_LEVEL("rulesLevel", m -> formatRulesLevel(m)),
        ERA("era", m -> formatEra(m.getYear())),
        COST("cost", m -> formatCost(m)),
        BV("bv", m -> Integer.toString(m.calculateBattleValue())),
        PILOT_NAME("pilotName", m -> m.getCrew().getName(0),
                m -> !m.getCrew().getName().equalsIgnoreCase("unnamed")),
        GUNNERY_SKILL("gunnerySkill", m -> Integer.toString(m.getCrew().getGunnery(0)),
                m -> !m.getCrew().getName().equalsIgnoreCase("unnamed")),
        PILOTING_SKILL("pilotingSkill", m -> Integer.toString(m.getCrew().getPiloting(0)),
                m -> !m.getCrew().getName().equalsIgnoreCase("unnamed")),
        HEAT_SINK_TYPE("hsType", m -> formatHeatSinkType(m)),
        HEAT_SINK_COUNT("hsCount", m -> formatHeatSinkCount(m))
        ;
        
        private String elementName;
        private Function<Mech, String> provider;
        private Predicate<Mech> show;
        
        MechTextElements(String elementName, Function<Mech, String> provider) {
            this(elementName, provider, m -> true);
        }
        
        MechTextElements(String elementName, Function<Mech, String> provider, Predicate<Mech> show) {
            this.elementName = elementName;
            this.provider = provider;
            this.show = show;
        }
        
        public String getElementName() {
            return elementName;
        }
        
        public String getText(Mech mech) {
            return provider.apply(mech);
        }
        
        public boolean shouldWrite(Mech mech) {
            return show.test(mech);
        }
    }
    
    /**
     * IDs of fields to hide if there is an assigned pilot
     */
    private static final String[] CREW_BLANKS = {
            "blankPilotName", "blankGunnerySkill", "blankPilotingSkill"
    };
    
    /**
     * The current mech being printed.
     */
    private Mech mech = null;

    public PrintMechCommon(Mech mech) {
        this.mech = mech;
    }

    /**
     * 
     */
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
        Graphics2D g2d = (Graphics2D) graphics;
        // f.setPaper(this.paper);
        printImage(g2d, pageFormat);
        return Printable.PAGE_EXISTS;
    }

    /**
     * 
     * @param g2d
     * @param pageFormat
     */
    public void printImage(Graphics2D g2d, PageFormat pageFormat) {
        final String METHOD_NAME = "printImage(Graphics2D, PageFormat";
        
        if (g2d == null) {
            return;
        }

        String mechSheetSVG = "data/images/recordsheets/Biped_Mech_default.svg";
        
        SVGDiagram diagram;
        diagram = ImageHelper.loadSVGImage(new File(mechSheetSVG));
        if (null == diagram) {
            MegaMekLab.getLogger().log(getClass(), METHOD_NAME,
                    LogLevel.ERROR,
                    "Failed to open Mech SVG file! Path: data/images/recordsheets/" + mechSheetSVG);
            return;
        }
        diagram.setDeviceViewport(
                new Rectangle(0, 0, (int) pageFormat.getImageableWidth(), (int) pageFormat.getImageableHeight()));

        try {
            SVGElement element = null;
            
            element = diagram.getElement("tspanCopyright");
            if (null != element) {
                ((Tspan) element).setText(String.format(((Tspan) element).getText(),
                        Calendar.getInstance().get(Calendar.YEAR)));
                ((Text) element.getParent()).rebuild();
            }
            
            writeTextFields(diagram);
            drawArmor(diagram);
            SVGElement eqRect = diagram.getElement("inventory");
            if (null != eqRect) {
                writeEquipment((Rect) eqRect);
            }
            
            for (int loc = 0; loc < mech.locations(); loc++) {
                SVGElement critRect = diagram.getElement("crits_" + mech.getLocationAbbr(loc));
                if (null != critRect) {
                    writeLocationCriticals(loc, (Rect) critRect);
                }
            }
            
            if (mech.getGyroType() == Mech.GYRO_HEAVY_DUTY) {
                SVGElement pip = diagram.getElement("heavyDutyGyroPip");
                if ((null != pip) && pip.hasAttribute("visibility", AnimationElement.AT_XML)) {
                    pip.setAttribute("visibility", AnimationElement.AT_XML, "visible");
                }
                pip.updateTime(0);
            }
            
            SVGElement hsRect = diagram.getElement("heatSinkPips");
            if (null != hsRect) {
                drawHeatSinkPips((Rect) hsRect);
            }
            
            diagram.render(g2d);
        } catch (SVGException e) {
            e.printStackTrace();
        }
    }

    private void writeTextFields(SVGDiagram diagram) throws SVGException {
        if (mech.hasUMU()) {
            SVGElement svgEle = diagram.getElement("mpJumpLabel");
            if (null != svgEle) {
                ((Tspan) svgEle).setText("Underwater:");
                ((Text) svgEle.getParent()).rebuild();
            }
        }
        if (!mech.getCrew().getName().equalsIgnoreCase("unnamed")) {
            for (String fieldName : CREW_BLANKS) {
                SVGElement svgEle = diagram.getElement(fieldName);
                if (null != svgEle) {
                    svgEle.addAttribute("visibility", AnimationElement.AT_XML, "hidden");
                    svgEle.updateTime(0);
                }
            }
        }
        for (MechTextElements element : MechTextElements.values()) {
            SVGElement svgEle = diagram.getElement(element.getElementName());
            
            // Ignore elements that don't exist
            if (null == svgEle) {
                continue;
            }
            ((Text) svgEle).getContent().clear();
            if (element.shouldWrite(mech)) {
                ((Text) svgEle).appendText(element.getText(mech));
            }
            ((Text) svgEle).rebuild();
        }
    }
    
    private void drawArmor(SVGDiagram diagram) throws SVGException {
        final String FORMAT = "( %d )";
        SVGElement element = null;
        for (int loc = 0; loc < mech.locations(); loc++) {
            element = diagram.getElement("textArmor_" + mech.getLocationAbbr(loc));
            if (null != element) {
                ((Text) element).getContent().clear();
                ((Text) element).appendText(String.format(FORMAT, mech.getOArmor(loc)));
                ((Text) element).rebuild();
            }
            element = diagram.getElement("textIS_" + mech.getLocationAbbr(loc));
            if (null != element) {
                ((Text) element).getContent().clear();
                ((Text) element).appendText(String.format(FORMAT, mech.getOInternal(loc)));
                ((Text) element).rebuild();
            }
            element = diagram.getElement("armorPips" + mech.getLocationAbbr(loc));
            if (null != element) {
                addPips(element, mech.getOArmor(loc),
                        (loc == Mech.LOC_HEAD) || (loc == Mech.LOC_CT) || (loc == Mech.LOC_CLEG));
                //                        setArmorPips(element, mech.getOArmor(loc), true);
                //                      (loc == Mech.LOC_HEAD) || (loc == Mech.LOC_CT));
            }
            element.updateTime(0);
            if (mech.hasRearArmor(loc)) {
                element = diagram.getElement("textArmor_" + mech.getLocationAbbr(loc) + "R");
                if (null != element) {
                    ((Text) element).getContent().clear();
                    ((Text) element).appendText(String.format(FORMAT, mech.getOArmor(loc, true)));
                    ((Text) element).rebuild();
                }
            }
        }
    }
    
    private void writeEquipment(Rect svgRect) throws SVGException {
        Map<Integer, Map<RecordSheetEquipmentLine,Integer>> eqMap = new TreeMap<>();
        Map<String,Integer> ammo = new TreeMap<>();
        for (Mounted m : mech.getEquipment()) {
            if (m.getType() instanceof AmmoType) {
                if (m.getLocation() != Entity.LOC_NONE) {
                    String shortName = ((AmmoType) m.getType()).getShortName().replace("Ammo", "");
                    shortName = shortName.replace("(Clan)", "");
                    shortName = shortName.replace("-capable", "");
                    ammo.merge(shortName, m.getBaseShotsLeft(), Integer::sum);
                }
                continue;
            }
            if ((m.getType() instanceof AmmoType)
                    || (m.getLocation() == Entity.LOC_NONE)
                    || !UnitUtil.isPrintableEquipment(m.getType(), true)) {
                continue;
            }
            eqMap.putIfAbsent(m.getLocation(), new HashMap<>());
            RecordSheetEquipmentLine line = new RecordSheetEquipmentLine(m);
            eqMap.get(m.getLocation()).merge(line, 1, Integer::sum);
        }
        
        Rectangle2D bbox = svgRect.getBoundingBox();
        SVGElement canvas = svgRect.getRoot();
        int viewWidth = (int)bbox.getWidth();
        int viewHeight = (int)bbox.getHeight();
        int viewX = (int)bbox.getX();
        int viewY = (int)bbox.getY();
        
        int qtyX = (int) Math.round(viewX + viewWidth * 0.037);
        int nameX = (int) Math.round(viewX + viewWidth * 0.08);
        int locX = (int) Math.round(viewX + viewWidth * 0.41);
        int heatX = (int) Math.round(viewX + viewWidth * 0.48);
        int dmgX = (int) Math.round(viewX + viewWidth * 0.53);
        int minX = (int) Math.round(viewX + viewWidth * 0.72);
        int shortX = (int) Math.round(viewX + viewWidth * 0.8);
        int medX = (int) Math.round(viewX + viewWidth * 0.88);
        int longX = (int) Math.round(viewX + viewWidth * 0.96);
        
        int indent = (int) Math.round(viewWidth * 0.02);
        
        int currY = viewY + 10;
        
        double fontSize = viewHeight * 0.044;
        double lineHeight = getFontHeight(fontSize, canvas) * 1.2;
        
        addTextElement(canvas, qtyX, currY, "Qty", fontSize, "middle", "bold");
        addTextElement(canvas, nameX + indent, currY, "Type", fontSize, "start", "bold");
        addTextElement(canvas, locX,  currY, "Loc", fontSize, "middle", "bold");
        addTextElement(canvas, heatX, currY, "Ht", fontSize, "middle", "bold");
        addTextElement(canvas, dmgX, currY, "Dmg", fontSize, "start", "bold");
        addTextElement(canvas, minX, currY, "Min", fontSize, "middle", "bold");
        addTextElement(canvas, shortX, currY, "Sht", fontSize, "middle", "bold");
        addTextElement(canvas, medX, currY, "Med", fontSize, "middle", "bold");
        addTextElement(canvas, longX, currY, "Lng", fontSize, "middle", "bold");
        currY += lineHeight;

        for (Integer loc : eqMap.keySet()) {
            for (RecordSheetEquipmentLine line : eqMap.get(loc).keySet()) {
                for (int row = 0; row < line.nRows(); row++) {
                    if (row == 0) {
                        addTextElement(canvas, qtyX, currY, Integer.toString(eqMap.get(loc).get(line)), fontSize, "middle", "normal");
                        addTextElement(canvas, nameX, currY, line.getNameField(row), fontSize, "start", "normal");
                    } else {
                        addTextElement(canvas, nameX + indent, currY, line.getNameField(row), fontSize, "start", "normal");
                    }
                    addTextElement(canvas, locX,  currY, line.getLocationField(row), fontSize, "middle", "normal");
                    addTextElement(canvas, heatX, currY, line.getHeatField(row), fontSize, "middle", "normal");
                    addTextElement(canvas, dmgX, currY, line.getDamageField(row), fontSize, "start", "normal");
                    addTextElement(canvas, minX, currY, line.getMinField(row), fontSize, "middle", "normal");
                    addTextElement(canvas, shortX, currY, line.getShortField(row), fontSize, "middle", "normal");
                    addTextElement(canvas, medX, currY, line.getMediumField(row), fontSize, "middle", "normal");
                    addTextElement(canvas, longX, currY, line.getLongField(row), fontSize, "middle", "normal");
                    currY += lineHeight;
                }
            }
        }
        
        if (ammo.size() > 0) {
            List<String> lines = new ArrayList<>();
            String line = "Ammo: ";
            double commaLen = getTextLength(", ", fontSize, canvas);
            double currX = getTextLength(line, fontSize, canvas);

            boolean first = true;
            for (String name : ammo.keySet()) {
                String str = String.format("(%s) %d", name, ammo.get(name));
                double len = getTextLength(str, fontSize, canvas);
                if (!first) {
                    len += commaLen;
                }
                if (currX + len < viewWidth) {
                    if (!first) {
                        line += ", ";
                    } else {
                        first = false;
                    }
                    line += str;
                } else {
                    lines.add(line);
                    line = str;
                    currX = indent;
                }
            }
            lines.add(line);
            
            currY = (int) (viewY + viewHeight - lines.size() * lineHeight);
            for (String l : lines) {
                Text newText = new Text();
                newText.addAttribute("font-family", AnimationElement.AT_XML, "Eurostile");
                newText.addAttribute("font-size", AnimationElement.AT_XML, Double.toString(fontSize));
                newText.addAttribute("font-weight", AnimationElement.AT_XML, "normal");
                newText.addAttribute("text-anchor", AnimationElement.AT_CSS, "start");
                newText.addAttribute("x", AnimationElement.AT_XML, Double.toString(viewX));
                newText.addAttribute("y", AnimationElement.AT_XML, Double.toString(currY));
                newText.appendText(l);
                canvas.loaderAddChild(null, newText);
                newText.rebuild();
            }
        }

    }
    
    private void writeLocationCriticals(int loc, Rect svgRect) throws SVGException {
        Rectangle2D bbox = svgRect.getBoundingBox();
        SVGElement canvas = svgRect.getRoot();
        int viewWidth = (int)bbox.getWidth();
        int viewHeight = (int)bbox.getHeight();
        int viewX = (int)bbox.getX();
        int viewY = (int)bbox.getY();
        
        double rollX = viewX;
        double critX = viewX + viewWidth * 0.11;
        double gap = 0;
        if (mech.getNumberOfCriticals(loc) > 6) {
            gap = viewHeight * 0.05;
        }
        double lineHeight = (viewHeight - gap) / mech.getNumberOfCriticals(loc);
        double currY = viewY;
        double fontSize = lineHeight * 0.9;
        
        Mounted startingMount = null;
        double startingMountY = 0;
        double endingMountY = 0;
        double connWidth = viewWidth * 0.02;
        
        for (int slot = 0; slot < mech.getNumberOfCriticals(loc); slot++) {
            currY += lineHeight;
            if (slot == 6) {
                currY += gap;
            }
            addTextElement(canvas, rollX, currY, ((slot % 6) + 1) + ".", fontSize, "start", "bold");
            CriticalSlot crit = mech.getCritical(loc, slot);
            String style = "bold";
            String fill = "#000000";
            if ((null == crit)
                    || ((crit.getType() == CriticalSlot.TYPE_EQUIPMENT)
                            && (!crit.getMount().getType().isHittable()))) {
                style = "standard";
                fill = "#3f3f3f";
                addTextElement(canvas, critX, currY, formatCritName(crit), fontSize, "start", style, fill);
            } else if (crit.isArmored()) {
                SVGElement pip = createPip(critX, currY - fontSize * 0.8, fontSize * 0.4, 0.7);
                canvas.loaderAddChild(null, pip);
                canvas.updateTime(0);
                addTextElement(canvas, critX + fontSize, currY, formatCritName(crit), fontSize, "start", style, fill);
            } else if ((crit.getType() == CriticalSlot.TYPE_EQUIPMENT)
                    && (crit.getMount().getType() instanceof MiscType)
                    && (crit.getMount().getType().hasFlag(MiscType.F_MODULAR_ARMOR))) {
                String critName = formatCritName(crit);
                addTextElement(canvas, critX, currY, critName, fontSize, "start", style, fill);
                double x = critX + getTextLength(critName, fontSize, canvas);
                double remainingW = viewX + viewWidth - x;
                double spacing = remainingW / 6.0;
                double radius = spacing * 0.25;
                double y = currY - lineHeight + spacing;
                double y2 = currY - spacing;
                x += spacing;
                for (int i = 0; i < 10; i++) {
                    if (i == 5) {
                        x -= spacing * 5.5;
                        y = y2;
                    }
                    SVGElement pip = createPip(x, y, radius, 0.5);
                    canvas.loaderAddChild(null, pip);
                    canvas.updateTime(0);
                    x += spacing;
                }
            } else {
                addTextElement(canvas, critX, currY, formatCritName(crit), fontSize, "start", style, fill);
            }
            Mounted m = null;
            if ((null != crit) && (crit.getType() == CriticalSlot.TYPE_EQUIPMENT)
                    && (crit.getMount().getType().isHittable())
                    && (crit.getMount().getType().getCriticals(mech) > 1)) {
                m = crit.getMount();
            }
            if ((startingMount != null) && (startingMount != m)) {
                connectSlots(canvas, critX - 1, startingMountY, connWidth, endingMountY - startingMountY);
            }
            if (m != startingMount) {
                startingMount = m;
                if (null != m) {
                    startingMountY = currY - lineHeight * 0.6;
                }
            } else {
                endingMountY = currY;
            }
        }
        if ((null != startingMount) && (startingMount.getType().getCriticals(mech) > 1)) {
            connectSlots(canvas, critX - 1, startingMountY, connWidth, endingMountY - startingMountY);
        }
    }

    private void connectSlots(SVGElement canvas, double x, double y, double w,
            double h) throws SVGElementException, SVGException {
        Path p = new Path();
        p.addAttribute("d", AnimationElement.AT_XML,
                "M " + x + " " + y
                + " h " + (-w)
                + " v " + h
                + " h " + w);
        p.addAttribute("stroke", AnimationElement.AT_CSS, "black");
        p.addAttribute("stroke-width", AnimationElement.AT_CSS, "0.72");
        p.addAttribute("fill", AnimationElement.AT_CSS, "none");
        p.updateTime(0);
        canvas.loaderAddChild(null, p);
        canvas.updateTime(0);
    }
    
    private void drawHeatSinkPips(Rect svgRect) throws SVGException {
        Rectangle2D bbox = svgRect.getBoundingBox();
        SVGElement canvas = svgRect.getRoot();
        int viewWidth = (int)bbox.getWidth();
        int viewHeight = (int)bbox.getHeight();
        int viewX = (int)bbox.getX();
        int viewY = (int)bbox.getY();
        
        int hsCount = mech.heatSinks();

        // r = 3.5
        // spacing = 9.66
        // stroke width = 0.9
        double size = 9.66;
        int cols = (int) (viewWidth / size);
        int rows = (int) (viewHeight / size);
        
        // Use 10 pips/column unless there are too many sinks for the space.
        if (hsCount <= cols * 10) {
            rows = 10;
        }
        // The rare unit with this many heat sinks will require us to shrink the pips
        while (hsCount > rows * cols) {
            // Figure out how much we will have to shrink to add another column
            double nextCol = (cols + 1.0) / cols;
            // First check whether we can shrink them less than what is required for a new column
            if (cols * (int) (rows * nextCol) > hsCount) {
                rows = (int) Math.ceil((double) hsCount / cols);
                size = viewHeight / rows;
            } else {
                cols++;
                size = viewWidth / (cols * size);
                rows = (int) (viewHeight / size);
            }
        }
        double radius = size * 0.36;
        double strokeWidth = 0.9;
        for (int i = 0; i < hsCount; i++) {
            int row = i % rows;
            int col = i / rows;
            SVGElement pip = this.createPip(viewX + size * col, viewY + size * row, radius, strokeWidth);
            canvas.loaderAddChild(null, pip);
            canvas.updateTime(0);
        }
    }
    
    private double getFontHeight(double fontSize, SVGElement canvas) throws SVGException {
        Text newText = new Text();
        newText.appendText("Medium Laser");        
        newText.addAttribute("x", AnimationElement.AT_XML, "0");
        newText.addAttribute("y", AnimationElement.AT_XML, "0");
        newText.addAttribute("font-family", AnimationElement.AT_XML, "Eurostile");
        newText.addAttribute("font-size", AnimationElement.AT_XML, Double.toString(fontSize));
        canvas.loaderAddChild(null, newText);
        newText.rebuild();
        
        double textHeight = newText.getShape().getBounds().getHeight();

        canvas.removeChild(newText);
        return textHeight;
    }
    
    private double getTextLength(String text, double fontSize, SVGElement canvas) throws SVGException {
        Text newText = new Text();
        newText.appendText(text);        
        newText.addAttribute("x", AnimationElement.AT_XML, "0");
        newText.addAttribute("y", AnimationElement.AT_XML, "0");
        newText.addAttribute("font-family", AnimationElement.AT_XML, "Eurostile");
        newText.addAttribute("font-size", AnimationElement.AT_XML, Double.toString(fontSize));
        canvas.loaderAddChild(null, newText);
        newText.rebuild();
        
        double width = newText.getShape().getBounds().getWidth();

        canvas.removeChild(newText);
        return width;
    }
    
    private static String formatRunMp(Mech mech) {
        int mp = mech.getWalkMP();
        if (mech.hasTSM()) {
            mp++;
        }
        if ((mech.getMASC() != null) && (mech.getSuperCharger() != null)) {
            mp = (int) Math.ceil(mp * 2.5);
        } else if ((mech.getMASC() != null) || (mech.getSuperCharger() != null)) {
            mp *= 2;
        } else {
            mp = (int) Math.ceil(mp * 1.5);
        }
        if (mech.hasMPReducingHardenedArmor()) {
            mp--;
        }
        if (mp > mech.getRunMP()) {
            return mech.getRunMP() + " [" + mp + "]";
        } else {
            return Integer.toString(mech.getRunMP());
        }
    }
    
    private static String formatTechBase(Mech mech) {
        if (mech.isMixedTech()) {
            return "Mixed";
        } else if (mech.isClan()) {
            return "Clan";
        } else {
            return "Inner Sphere";
        }
    }
    
    private static String formatRulesLevel(Mech mech) {
        return mech.getStaticTechLevel().toString().substring(0, 1)
                + mech.getStaticTechLevel().toString().substring(1).toLowerCase();
    }
    
    private static String formatEra(int year) {
        if (year < 2571) {
            return "Age of War";
        } else if (year < 2781) {
            return "Star League";
        } else if (year < 2901) {
            return "Early Succession War";
        } else if (year < 3050) {
            return "Late Succession War";
        } else if (year < 3062) {
            return "Clan Invasion";
        } else if (year < 3068) {
            return "Civil War";
        } else if (year < 3086) {
            return "Jihad";
        } else if (year < 3101) {
            return "Early Republic";
        } else if (year < 3131) {
            return "Late Republic";
        } else {
            return "Dark Ages";
        }
    }
    
    private static String formatCost(Mech mech) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
        return nf.format(mech.getCost(true)) + " C-bills";
    }
    
    private static String formatHeatSinkType(Mech mech) {
        if (mech.hasLaserHeatSinks()) {
            return "Laser Heat Sinks:";
        } else if (mech.hasDoubleHeatSinks()) {
            return "Double Heat Sinks:";
        } else {
            return "Heat Sinks";
        }
    }
    
    private static String formatHeatSinkCount(Mech mech) {
        int hsCount = mech.heatSinks();
        if (mech.hasDoubleHeatSinks()) {
            return String.format("%d (%d)", hsCount, hsCount * 2);
        } else {
            return Integer.toString(hsCount);
        }
    }
    
    private String formatCritName(CriticalSlot cs) {
        if (null == cs) {
            return "Roll Again";
        } else if (cs.getType() == CriticalSlot.TYPE_SYSTEM) {
            if (cs.getIndex() == Mech.SYSTEM_ENGINE) {
                StringBuilder sb = new StringBuilder();
                if (mech.isPrimitive()) {
                    sb.append("Primitive ");
                }
                switch (mech.getEngine().getEngineType()) {
                    case Engine.COMBUSTION_ENGINE:
                        sb.append("I.C.E."); //$NON-NLS-1$
                        break;
                    case Engine.NORMAL_ENGINE:
                        sb.append("Fusion"); //$NON-NLS-1$
                        break;
                    case Engine.XL_ENGINE:
                        sb.append("XL Fusion"); //$NON-NLS-1$
                        break;
                    case Engine.LIGHT_ENGINE:
                        sb.append("Light Fusion"); //$NON-NLS-1$
                        break;
                    case Engine.XXL_ENGINE:
                        sb.append("XXL Fusion"); //$NON-NLS-1$
                        break;
                    case Engine.COMPACT_ENGINE:
                        sb.append("Compact Fusion"); //$NON-NLS-1$
                        break;
                    case Engine.FUEL_CELL:
                        sb.append("Fuel Cell"); //$NON-NLS-1$
                        break;
                    case Engine.FISSION:
                        sb.append("Fission"); //$NON-NLS-1$
                        break;
                }
                sb.append(" Engine");
                return sb.toString();
            } else {
                String name = mech.getSystemName(cs.getIndex()).replace("Standard ", "");
                if (((cs.getIndex() >= Mech.ACTUATOR_UPPER_ARM) && (cs.getIndex() <= Mech.ACTUATOR_HAND))
                        || ((cs.getIndex() >= Mech.ACTUATOR_UPPER_LEG) && (cs.getIndex() <= Mech.ACTUATOR_FOOT))) {
                    name += " Actuator";
                }
                return name;
            }
        } else {
            Mounted m = cs.getMount();
            StringBuffer critName = new StringBuffer(UnitUtil.getCritName(mech, m.getType()));

            if (UnitUtil.isTSM(m.getType())) {
                critName.setLength(0);
                critName.append("Triple-Strength Myomer");
            }

            if (m.isRearMounted()) {
                critName.append(" (R)");
            } else if (m.isMechTurretMounted()) {
                critName.append(" (T)");
            } else if ((m.getType() instanceof AmmoType) && (((AmmoType) m.getType()).getAmmoType() != AmmoType.T_COOLANT_POD)) {
                AmmoType ammo = (AmmoType) m.getType();

                critName = new StringBuffer("Ammo (");
                // Remove Text (Clan) from the name
                critName.append(ammo.getShortName().replace('(', '.').replace(')', '.').replaceAll(".Clan.", "").trim());
                // Remove any additional Ammo text.
                if (critName.toString().endsWith("Ammo")) {
                    critName.setLength(critName.length() - 5);
                    critName.trimToSize();
                }

                // Remove Capable with the name
                if (critName.indexOf("-capable") > -1) {
                    int startPos = critName.indexOf("-capable");
                    critName.delete(startPos, startPos + "-capable".length());
                    critName.trimToSize();
                }

                // Trim trailing spaces.
                while (critName.charAt(critName.length() - 1) == ' ') {
                    critName.setLength(critName.length() - 1);
                }
                critName.trimToSize();
                critName.append(") ");
                critName.append(m.getUsableShotsLeft());
            }

            if (cs.getMount2() != null) {
                critName.append(" | ");
                if (!(cs.getMount2().getType() instanceof AmmoType)) {
                    critName.append(UnitUtil.getCritName(mech, cs.getMount2().getType()));
                } else {
                    AmmoType ammo = (AmmoType)cs.getMount2().getType();
                    critName.append(ammo.getShortName().replace('(', '.').replace(')', '.').replaceAll(".Clan.", "").trim());
                    // Remove any additional Ammo text.
                    if (critName.toString().endsWith("Ammo")) {
                        critName.setLength(critName.length() - 5);
                        critName.trimToSize();
                    }

                    // Remove Capable with the name
                    if (critName.indexOf("-capable") > -1) {
                        int startPos = critName.indexOf("-capable");
                        critName.delete(startPos, startPos + "-capable".length());
                        critName.trimToSize();
                    }

                    // Trim trailing spaces.
                    while (critName.charAt(critName.length() - 1) == ' ') {
                        critName.setLength(critName.length() - 1);
                    }
                    critName.trimToSize();
                    critName.append(") ");
                    critName.append(m.getUsableShotsLeft());
                }
            }
            if (!mech.isMixedTech()) {
                int startPos = critName.indexOf("[Clan]");
                if (startPos >= 0) {
                    critName.delete(startPos, startPos + "[Clan]".length());
                    critName.trimToSize();
                }
            }
            return critName.toString();
        }
    }

    /**
     * Convenience method for creating a new SVG Text element and adding it to the parent.  The height of the text is
     * returned, to aid in layout.
     * 
     * @param parent    The SVG element to add the text element to.
     * @param x         The X position of the new element.
     * @param y         The Y position of the new element.
     * @param text      The text to display.
     * @param fontSize  Font size of the text.
     * @param anchor    Set the Text elements text-anchor.  Should be either start, middle, or end.
     * @param weight    The font weight, either normal or bold.
     *
     * @throws SVGException
     */
    private void addTextElement(SVGElement parent, double x, double y, String text,
            double fontSize, String anchor, String weight) throws SVGException {
        addTextElement(parent, x, y, text, fontSize, anchor, weight, "#000000");
    }
    
    private void addTextElement(SVGElement parent, double x, double y, String text,
            double fontSize, String anchor, String weight, String fill)
            throws SVGException {
        Text newText = new Text();
        newText.appendText(text);
        
        newText.addAttribute("x", AnimationElement.AT_XML, x + "");
        newText.addAttribute("y", AnimationElement.AT_XML, y + "");
        newText.addAttribute("font-family", AnimationElement.AT_XML, "Eurostile");
        newText.addAttribute("font-size", AnimationElement.AT_XML, fontSize + "px");
        newText.addAttribute("font-weight", AnimationElement.AT_XML, weight);
        newText.addAttribute("text-anchor", AnimationElement.AT_CSS, anchor);
        newText.addAttribute("fill", AnimationElement.AT_XML, fill);
        parent.loaderAddChild(null, newText);
        newText.rebuild();
    }
    
    private final static double CONST_C = 0.55191502449;
    private final static String FMT_CURVE = " c %f %f,%f %f,%f %f";
    
    /**
     * Approximates a circle using four bezier curves.
     * 
     * @param x      Position of left of bounding rectangle.
     * @param y      Position of top of bounding rectangle.
     * @param radius Radius of the circle
     * @return       A Path describing the circle
     * @throws SVGException
     */
    private Path createPip(double x, double y, double radius, double strokeWidth) throws SVGException {
        // c is the length of each control line
        double c = CONST_C * radius;
        Path path = new Path();
        path.addAttribute("fill", AnimationElement.AT_CSS, "none");
        path.addAttribute("stroke", AnimationElement.AT_CSS, "black");
        path.addAttribute("stroke-width", AnimationElement.AT_CSS, Double.toString(strokeWidth));
        
        // Move to start of circle, at (1, 0)
        StringBuilder d = new StringBuilder("M").append(x + radius * 2).append(",").append(y + radius);
        // Draw arcs anticlockwise. The coordinates are relative to the beginning of the arc.
        d.append(String.format(FMT_CURVE, 0.0, -c, c - radius, -radius, -radius, -radius));
        d.append(String.format(FMT_CURVE, -c, 0.0, -radius, radius - c, -radius, radius));
        d.append(String.format(FMT_CURVE, 0.0, c, radius - c, radius, radius, radius));
        d.append(String.format(FMT_CURVE, c, 0.0, radius, c - radius, radius, -radius));
        path.addAttribute("d", AnimationElement.AT_XML, d.toString());
        path.updateTime(0);
        return path;
    }
    
    private void addPips(SVGElement group, int armorVal, boolean symmetric) throws SVGException {
        final String METHOD_NAME = "addArmorPips(SVGElement,int)";
        double spacing = 6.15152;
        List<Rectangle2D> rows = new ArrayList<>();
        double left = Double.MAX_VALUE;
        double top = Double.MAX_VALUE;
        double right = 0;
        double bottom = 0;
        
        for (int i = 0; i < group.getNumChildren(); i++) {
            final SVGElement r = group.getChild(i);
            if (r instanceof Rect) {
                Rectangle2D bbox = ((Rect) r).getBoundingBox();
                spacing = Math.min(spacing, bbox.getHeight());
                if (bbox.getX() < left) {
                    left = bbox.getX();
                }
                if (bbox.getY() < top) {
                    top = bbox.getY();
                }
                if (bbox.getX() + bbox.getWidth() > right) {
                    right = bbox.getX() + bbox.getWidth();
                }
                if (bbox.getY() + bbox.getHeight() > bottom) {
                    bottom = bbox.getY() + bbox.getHeight();
                }
                rows.add(bbox);
            }
        }
        if (rows.isEmpty()) {
            MegaMekLab.getLogger().log(getClass(), METHOD_NAME, LogLevel.WARNING,
                    "No pip rows defined for region " + group.getId());
            return;
        }
        Collections.sort(rows, (r1, r2) -> (int) r1.getY() - (int) r2.getY());
        
        Rectangle2D bounds = new Rectangle2D.Double(left, top, right - left, bottom - top);
        double aspect = bounds.getWidth() / bounds.getHeight();
        double centerLine = rows.get(0).getX() + rows.get(0).getWidth() / 2.0;
        
        int maxWidth = 0;
        int totalPips = 0;
        // Maximum number of pips that can be displayed on each row
        int[] rowLength = new int[rows.size()];
        int[][] halfPipCount = new int[rows.size()][];
        
        double prevRowBottom = 0;
        int centerPip = 0;
        for (int i = 0; i < rows.size(); i++) {
            final Rectangle2D rect = rows.get(i);
            int halfPipsLeft = (int) ((centerLine - rect.getX()) / (spacing / 2));
            int halfPipsRight = (int) ((rect.getX() + rect.getWidth() - centerLine) / (spacing / 2));
            if ((i > 0) && (rect.getY() < prevRowBottom)) {
                centerPip = (1 - centerPip);
                if (halfPipsLeft %2 != centerPip) {
                    halfPipsLeft--;
                }
                if (halfPipsRight %2 != centerPip) {
                    halfPipsRight--;
                }
                rowLength[i] = (halfPipsLeft + halfPipsRight) / 2;
            } else {
                rowLength[i] = (halfPipsLeft + halfPipsRight) / 2;
                centerPip = rowLength[i] % 2;
            }
            if (rowLength[i] > maxWidth) {
                maxWidth = rowLength[i];
            }
            halfPipCount[i] = new int[] { halfPipsLeft, halfPipsRight };
            totalPips += rowLength[i];
            prevRowBottom = rect.getY() + spacing;
        }
        
        int nRows = adjustedRows(armorVal, rows.size(), maxWidth, aspect);
        
        // Now we need to select the rows to use. If the total pips available in those rows is
        // insufficient, add a row and try again.
        
        int available = 0;
        int minWidth = maxWidth;
        List<Integer> useRows = new ArrayList<>();
        while (available < armorVal) {
            int start = rows.size() / (nRows * 2);
            for (int i = 0; i < nRows; i++) {
                int r = start + i * rows.size() / nRows;
                useRows.add(r);
                available += rowLength[r];
                if (rowLength[r] < minWidth) {
                    minWidth = rowLength[r];
                }
            }
            if (available < armorVal) {
                nRows++;
                available = 0;
                useRows.clear();
                minWidth = maxWidth;
            }
        }
        
        // Sort the rows into the order pips should be added: longest rows first, then for rows of
        // equal length the one closest to the middle first
        Collections.sort(useRows, (r1, r2) -> {
            if (rowLength[r1] == rowLength[r2]) {
                return Math.abs(r1 - rows.size() / 2) - Math.abs(r2 - rows.size() / 2);
            } else {
                return rowLength[r2] - rowLength[r1];
            }
        });
        
        // Now we iterate through the rows and assign pips as many times as it takes to get all assigned.
        int[] pipsByRow = new int[rows.size()];
        int remaining = armorVal;
        while (remaining > 0) {
            for (int r : useRows) {
                int toAdd = Math.min(remaining,
                        Math.min(rowLength[r] / minWidth, rowLength[r] - pipsByRow[r]));
                pipsByRow[r] += toAdd;
                remaining -= toAdd;
            }
        }
        
        // Locations on the unit's center line require that rows with an even width don't get assigned
        // an odd number of pips.
        if (symmetric) {
            // First we remove all the odd pips in even rows
            remaining = 0;
            for (int r = 0; r < rows.size(); r++) {
                if ((rowLength[r] % 2 == 0) && (pipsByRow[r] % 2 == 1)) {
                    pipsByRow[r]--;
                    remaining++;
                }
            }
            // Now we go through all the selected rows and assign them; this time even rows can
            // only be assigned pips in pairs.
            int toAdd = 0;
            boolean added = false;
            do {
                for (int r : useRows) {
                    toAdd = 2 - rowLength[r] % 2;
                    if ((remaining >= toAdd) && (pipsByRow[r] + toAdd <= rowLength[r])) {
                        pipsByRow[r] += toAdd;
                        remaining -= toAdd;
                    }
                }
            } while ((remaining > 0) && added);
            
            // We may still have one or more left. At this point all rows are considered available.
            int centerRow = rows.size() / 2;
            while (remaining > 0) {
                for (int i = 0; i <= centerRow; i++) {
                    int r = centerRow - i;
                    toAdd = 2 - rowLength[r] % 2;
                    if (remaining < toAdd) {
                        continue;
                    }
                    if (rowLength[r] >= pipsByRow[r] + toAdd) {
                        pipsByRow[r] += toAdd;
                        remaining -= toAdd;
                    }
                    if (i > 0) {
                        r = centerRow + i;
                        if (r >= rows.size()) {
                            continue;
                        }
                        toAdd = 2 - rowLength[r] % 2;
                        if (remaining < toAdd) {
                            continue;
                        }
                        if (rowLength[r] >= pipsByRow[r] + toAdd) {
                            pipsByRow[r] += toAdd;
                            remaining -= toAdd;
                        }
                    }
                }
            }
        }
        
        // It's likely that there's extra spacing between rows, so we're going to check whether
        // we can increase horizontal spacing between pips to keep the approximate aspect ratio.
        
        int firstRow = 0;
        int lastRow = rows.size();
        int r = 0;
        while (r < rows.size()) {
            if (pipsByRow[r] > 0) {
                firstRow = r;
                break;
            }
            r++;
        }
        r = rows.size() - 1;
        while (r >= 0) {
            if (pipsByRow[r] > 0) {
                lastRow = r;
                break;
            }
            r--;
        }
        double targetWidth = aspect * (rows.get(lastRow).getY() + rows.get(lastRow).getHeight()
                - rows.get(firstRow).getY());
        double hSpacing = targetWidth / pipsByRow[firstRow];
        for (r = firstRow + 1; r <= lastRow; r++) {
            if (pipsByRow[r] > 0) {
                hSpacing = Math.min(hSpacing, Math.min(targetWidth, rows.get(r).getWidth()) / pipsByRow[r]);
            }
        }
        if (hSpacing < spacing) {
            hSpacing = spacing;
        }
        
        for (r = 0; r < pipsByRow.length; r++) {
            if (pipsByRow[r] > 0) {
                double radius = rows.get(r).getHeight() * 0.38;
                SVGElement pip = null;
                // Symmetric and this row is centered
                if (symmetric && (halfPipCount[r][0] == halfPipCount[r][1])) {
                    double leftX = centerLine - hSpacing;
                    double rightX = centerLine;
                    if (rowLength[r] % 2 == 1) {
                        leftX -= spacing / 2.0;
                        rightX += hSpacing - spacing / 2.0;
                        if (pipsByRow[r] % 2 == 1) {
                            pip = createPip(leftX + hSpacing, rows.get(r).getY(), radius, 0.5);
                            group.loaderAddChild(null, pip);
                            pipsByRow[r]--;
                        }
                    } else {
                        leftX += (hSpacing - spacing) / 2.0;
                        rightX += (hSpacing - spacing) / 2.0;
                    }
                    while (pipsByRow[r] > 0) {
                        pip = createPip(leftX, rows.get(r).getY(), radius, 0.5);
                        group.loaderAddChild(null, pip);
                        pip = createPip(rightX, rows.get(r).getY(), radius, 0.5);
                        group.loaderAddChild(null, pip);
                        leftX -= hSpacing;
                        rightX += hSpacing;
                        pipsByRow[r] -= 2;
                    }
                } else {
                    // If the location is symmetric but the middle of the current row is to the left
                    // of the centerline, right justify. If non-symmetric, balance the extra space at the
                    // ends of the rows with any odd space going on the right margin.
                    double x = centerLine - halfPipCount[r][0] * spacing / 2.0;
                    if (symmetric && halfPipCount[r][0] > halfPipCount[r][1]) {
                        x += (rowLength[r] - pipsByRow[r]) * hSpacing;
                    } else if (!symmetric) {
                        x += ((rowLength[r] - pipsByRow[r]) / 2) * hSpacing;
                    }
                    while (pipsByRow[r] > 0) {
                        pip = createPip(x, rows.get(r).getY(), radius, 0.5);
                        group.loaderAddChild(null, pip);
                        pipsByRow[r]--;
                        x += hSpacing;
                    }
                }
            }
        }
        group.updateTime(0);
    }
    
    /**
     * Calculate how many rows to use to give the pip pattern the approximate aspect ratio of the region
     * 
     * @param pipCount  The number of pips to display
     * @param maxRows   The maximum number of rows in the region
     * @param maxWidth  The number of pips in the longest row
     * @param aspect    The aspect ratio of the region (w/h)
     * @return          The number of rows to use in the pattern
     */
    private int adjustedRows(int pipCount, int maxRows, int maxWidth, double aspect) {
        double nRows = Math.min(pipCount,  maxRows);
        double width = Math.ceil(pipCount / nRows);
        double pipAspect = width / nRows;
        double sqrAspect = aspect * aspect;
        if (aspect <= 1) {
            while ((width < maxWidth) && (nRows > 1)) {
                double tmpWidth = width + 1;
                double tmpRows = Math.ceil(pipCount / tmpWidth);
                double tmpAspect = tmpWidth / tmpRows;
                if (pipAspect * tmpAspect / sqrAspect < 2) {
                    width = tmpWidth;
                    nRows = tmpRows;
                    pipAspect = tmpAspect;
                } else {
                    break;
                }
            }
        } else {
            while ((nRows < maxRows) && (width > 1)) {
                double tmpRows = nRows + 1;
                double tmpWidth = Math.ceil(pipCount / tmpRows);
                double tmpAspect = tmpWidth / tmpRows;
                if (pipAspect * tmpAspect / sqrAspect > 2) {
                    width = tmpWidth;
                    nRows = tmpRows;
                    pipAspect = tmpAspect;
                } else {
                    break;
                }
            }
        }
        return (int) nRows;
    }
    
    private void setArmorPips(SVGElement group, int armorVal, boolean symmetric) throws SVGException {
        final String METHOD_NAME = "setArmorPips(SVGElement,int)";
        // First sort pips into rows. We can't rely on the pips to be in order, so we use
        // maps to allow non-sequential loading.
        Map<Integer,Map<Integer,SVGElement>> rowMap = new TreeMap<>();
        int pipCount = 0;
        for (int i = 0; i < group.getNumChildren(); i++) {
            final SVGElement pip = group.getChild(i);
            try {
                int index = pip.getId().indexOf(":");
                String[] coords = pip.getId().substring(index + 1).split(",");
                int r = Integer.parseInt(coords[0]);
                rowMap.putIfAbsent(r, new TreeMap<>());
                rowMap.get(r).put(Integer.parseInt(coords[1]), pip);
                pipCount++;
            } catch (Exception ex) {
                MegaMekLab.getLogger().log(getClass(), METHOD_NAME, LogLevel.ERROR,
                        "Malformed id for SVG armor pip element: " + pip.getId());
            }
        }
        if (pipCount < armorVal) {
            MegaMekLab.getLogger().log(getClass(), METHOD_NAME, LogLevel.ERROR,
                    "Armor pip group " + group.getId() + " does not contain enough pips for " + armorVal + " armor");
            return;
        } else if (pipCount == armorVal) {
            // Simple case; leave as is
            return;
        }
        // Convert map into array for easier iteration in both directions. This will also skip
        // over gaps in the numbering.
        SVGElement[][] rows = new SVGElement[rowMap.size()][];
        int row = 0;
        for (Map<Integer,SVGElement> r : rowMap.values()) {
            rows[row] = new SVGElement[r.size()];
            int i = 0;
            for (SVGElement e : r.values()) {
                rows[row][i] = e;
                i++;
            }
            row++;
        }
        
        // Get the ratio of the number of pips to show to the total number of pips
        // and distribute the number of pips proportionally to each side
        double saturation = Math.min(1.0, (double) armorVal / pipCount);
        
        // Now we find the center row, which is the row that has the same number of pips above
        // and below it as nearly as possible.
        
        int centerRow = rows.length / 2;
        int pipsAbove = 0;
        for (int r = 0; r < rows.length; r++) {
            pipsAbove += rows[r].length;
            if (pipsAbove > pipCount / 2) {
                centerRow = r;
                break;
            }
        }
        int showAbove = (int) Math.round(pipsAbove * saturation);
        int showBelow = armorVal - showAbove;
        // keep a running total of the number to hide in each row
        int[] showByRow = new int[rows.length];
        double remaining = pipsAbove;
        for (int i = centerRow; i >= 0; i--) {
            showByRow[i] = (int) Math.round(rows[i].length * showAbove / remaining);
            if (symmetric && (showByRow[i] > 0) && (showByRow[i] % 2) != (rows[i].length % 2)) {
                if ((showByRow[i] < showAbove) && (showByRow[i] < rows[i].length)) {
                    showByRow[i]++;
                } else {
                    showByRow[i]--;
                }
            }
            showAbove -= showByRow[i];
            remaining -= rows[i].length;
        }
        // We may have some odd ones left over due to symmetry imposed on middle pip of the row
        showBelow += showAbove;
        remaining = pipCount - pipsAbove;
        for (int i = centerRow + 1; i < rows.length; i++) {
            showByRow[i] = (int) Math.round(rows[i].length * showBelow / remaining);
            if (symmetric && (showByRow[i] > 0) && (showByRow[i] % 2) != (rows[i].length % 2)) {
                if ((showByRow[i] < showBelow) && (showByRow[i] < rows[i].length)) {
                    showByRow[i]++;
                } else {
                    showByRow[i]--;
                }
            }
            showBelow -= showByRow[i];
            remaining -= rows[i].length;
        }
        
        // Now we need to deal with leftovers, starting in the middle and adding one or two at a time
        // (depending on whether there are an odd or even number of pips in the row) moving out toward
        // the top and bottom and repeating until they are all placed.
        
        remaining = showBelow;
        while (remaining > 0) {
            for (int i = 0; i <= centerRow; i++) {
                row = centerRow - i;
                int toAdd = symmetric? (2 - rows[row].length % 2) : 1;
                if (remaining < toAdd) {
                    continue;
                }
                if (rows[row].length >= showByRow[row] + toAdd) {
                    showByRow[row] += toAdd;
                    remaining -= toAdd;
                }
                if (i > 0) {
                    row = centerRow + i;
                    if (row >= rows.length) {
                        continue;
                    }
                    toAdd = symmetric? (2 - rows[row].length % 2) : 1;
                    if (remaining < toAdd) {
                        continue;
                    }
                    if (rows[row].length >= showByRow[row] + toAdd) {
                        showByRow[row] += toAdd;
                        remaining -= toAdd;
                    }
                }
            }
        }
        
        // Now select which pips in each row to hide
        for (row = 0; row < rows.length; row++) {
            int toHide = rows[row].length - showByRow[row];
            if (toHide == 0) {
                continue;
            }
            double ratio = (double) toHide / rows[row].length;
            int length = rows[row].length;
            if (symmetric) {
                length /= 2;
                if (toHide % 2 == 1) {
                    hideElement(rows[row][length]);
                    toHide--;
                    ratio = (double) toHide / (rows[row].length - 1);
                }
            }
            Set<Integer> indices = new HashSet<>();
            double accum = 0.0;
            for (int i = length % 2; i < length; i += 2) {
                accum += ratio;
                if (accum >= 1 -saturation) {
                    indices.add(i);
                    accum -= 1.0;
                    toHide--;
                    if (symmetric) {
                        indices.add(rows[row].length - 1 - i);
                        toHide--;
                    }
                }
                if (toHide == 0) {
                    break;
                }
            }
            if (toHide > 0) {
                for (int i = length - 1; i >= 0; i -= 2) {
                    accum += ratio;
                    if (accum >= saturation) {
                        indices.add(i);
                        accum -= 1.0;
                        toHide--;
                        if (symmetric) {
                            indices.add(rows[row].length - 1 - i);
                            toHide--;
                        }
                    }
                    if (toHide == 0) {
                        break;
                    }
                }
            }
            int i = 0;
            while (toHide > 0) {
                if (!indices.contains(i)) {
                    indices.add(i);
                    toHide--;
                    if (symmetric) {
                        indices.add(rows[row].length - 1 - i);
                        toHide--;
                    }
                }
                i++;
            }
            for (int index : indices) {
                hideElement(rows[row][index]);
            }
        }
    }
    
    private void hideElement(SVGElement element) throws SVGException {
        if (element.hasAttribute("visibility", AnimationElement.AT_XML)) {
            element.setAttribute("visibility", AnimationElement.AT_XML, "hidden");
        } else {
            element.addAttribute("visibility", AnimationElement.AT_XML, "hidden");
        }
        element.updateTime(0);
    }
}
