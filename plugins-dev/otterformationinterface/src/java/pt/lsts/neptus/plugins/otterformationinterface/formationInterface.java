/*
 * Copyright (c) 2004-2023 
 * Author: Nikolai Lauvås
 */
package pt.lsts.neptus.plugins.otterformationinterface;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import java.util.Vector;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingWorker;
import javax.vecmath.Point3d;


import pt.lsts.imc.otterFormation;
import pt.lsts.imc.def.SpeedUnits;
import pt.lsts.neptus.types.vehicle.VehiclesHolder;



import pt.lsts.imc.PlanProbSpec;
import pt.lsts.imc.PolygonVertex;

import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.gui.PropertiesEditor;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.NeptusProperty.DistributionEnum;
import pt.lsts.neptus.plugins.NeptusProperty.LEVEL;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.SimpleRendererInteraction;
import pt.lsts.neptus.renderer2d.Renderer2DPainter;
import pt.lsts.neptus.renderer2d.StateRenderer2D;
import pt.lsts.neptus.renderer2d.StateRendererInteraction;
import pt.lsts.neptus.types.map.PathElement;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.util.GuiUtils;

/**
 * @author Nikolai Lauvås
 * 
 */
@PluginDescription(name = "Otter Formation Interface", icon = "pt/lsts/neptus/plugins/formationInterface/triangle.png")
public class formationInterface extends SimpleRendererInteraction implements Renderer2DPainter,
        StateRendererInteraction {

    //protected EDITION_STATES state = EDITION_STATES.NONE;

    protected boolean isActive;

    @NeptusProperty(name = "Minimum Speed")
    public double minSpeed = 0.0;

    @NeptusProperty(name = "Maximum Speed")
    public double maxSpeed = 1.0;

    @NeptusProperty(name = "Speed Units", userLevel = LEVEL.REGULAR)
    public SpeedUnits speed_units = SpeedUnits.METERS_PS;

    @NeptusProperty(name = "Minimum Radius")
    public double minRadius = 40.0;

    @NeptusProperty(name = "Maximum Radius")
    public double maxRadius = 120.0;

    @NeptusProperty(name = "Source To Follow")
    public String target = "";

    @NeptusProperty(name = "Formation Leader Vehicle")
    public int leader = 0x2810;

    @NeptusProperty(name = "Formation Participants")
    public String participants = "ntnu-otter-01,ntnu-otter-02,ntnu-otter-03";

    @NeptusProperty(name = "Rotate Distance On Estimate", category="Custom Parameters")
    public double rotation_dist = 0.0;//Math.PI/8;

    @NeptusProperty(name = "Minimum Tag Interval", category="Custom Parameters")
    public double minTagInterval = 30.0;

    @NeptusProperty(name = "Maximum Tag Interval", category="Custom Parameters")
    public double maxTagInterval = 90.0;

    @NeptusProperty(name = "FollowRef Timeout", category="Custom Parameters")
    public double timeout = 60.0;

    @NeptusProperty(name = "FollowRef Transmitt Interval", category="Custom Parameters")
    public double FollowRefInterval = 5.0;

    @NeptusProperty(name = "Custom Parameters", category="Custom Parameters")
    public String customparameters = "";

    /**
     * @param console
     */
    public formationInterface(ConsoleLayout console) {
        super(console);
    }

    @Override
    public boolean isExclusive() {
        return true;
    }

    @Override
    public void setActive(boolean mode, StateRenderer2D source) {
        isActive = mode;
    }

    @Override
    public void mouseClicked(MouseEvent event, StateRenderer2D source) {

        final Point2D mousePosition = event.getPoint();
        final StateRenderer2D renderer = source;

        if (event.getButton() == MouseEvent.BUTTON3) {
        JPopupMenu menu = new JPopupMenu();
        menu.add("Start Formation Controller").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startFormationMessage();
            }
        });
        menu.add("End Formation Controller").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                endFormationMessage();
            }
        });

        menu.addSeparator();

        menu.add("Settings").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                PropertiesEditor.editProperties(formationInterface.this, true);

            }
        });

        menu.add("Update Custom Parameters").addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {

                sendCustomParameters();

            }
        });
        menu.addSeparator();

            menu.show(source, (int) mousePosition.getX(), (int) mousePosition.getY());
        }

        //else {
            // Leftclick
        //}
    }
    protected void endFormationMessage() {
        otterFormation toSend = new otterFormation();
        toSend.setDst(leader);
        toSend.setMsgType(otterFormation.MSG_TYPE.STOP);
        send(toSend);
        NeptusLog.pub().info("Sent stop formation controller request to vehicle");
    }
    protected void startFormationMessage() {
        otterFormation toSend = new otterFormation();
        toSend.setDst(leader);
        toSend.setMsgType(otterFormation.MSG_TYPE.START);
        toSend.setMinSpeed(minSpeed);
        toSend.setMaxSpeed(maxSpeed);
        toSend.setMinRadius(minRadius);
        toSend.setMaxRadius(maxRadius);
        toSend.setSpeedUnits(speed_units);
        toSend.setTarget(target);
        toSend.setParticipants(participants);

        if(customparameters.isEmpty()) {
            customparameters += "r=" + rotation_dist + ";";
            customparameters += "i=" + minTagInterval + ";";
            customparameters += "x=" + maxTagInterval + ";";
            customparameters += "t=" + timeout + ";";
            customparameters += "f=" + FollowRefInterval + ";";
            toSend.setCustom(customparameters);
            customparameters = "";
        } else {
            toSend.setCustom(customparameters);
        }

        
        send(toSend);
        NeptusLog.pub().info("Sent start formation controller request to vehicle");
    }

    protected void sendCustomParameters() {
        otterFormation toSend = new otterFormation();
        toSend.setDst(leader);
        toSend.setMsgType(otterFormation.MSG_TYPE.PARAM_CHANGE);
        if(customparameters.isEmpty()) {
            customparameters += "r=" + rotation_dist + ";";
            customparameters += "i=" + minTagInterval + ";";
            customparameters += "x=" + maxTagInterval + ";";
            customparameters += "t=" + timeout + ";";
            customparameters += "f=" + FollowRefInterval + ";";
        }
        toSend.setCustom(customparameters);
        send(toSend);
        NeptusLog.pub().info("Sent start formation controller request to vehicle");        
    }
    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer) {


        g.setColor(Color.green.darker());
        g.setStroke(new BasicStroke(4.0f));

        g.setColor(Color.black);
        g.setStroke(new BasicStroke(1.0f));

    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.plugins.SimpleSubPanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see pt.lsts.neptus.plugins.SimpleSubPanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        // TODO Auto-generated method stub

    }

}
