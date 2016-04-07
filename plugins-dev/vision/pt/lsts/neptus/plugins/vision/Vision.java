/*
 * Copyright (c) 2004-2016 Universidade do Porto - Faculdade de Engenharia
 * Laboratório de Sistemas e Tecnologia Subaquática (LSTS)
 * All rights reserved.
 * Rua Dr. Roberto Frias s/n, sala I203, 4200-465 Porto, Portugal
 *
 * This file is part of Neptus, Command and Control Framework.
 *
 * Commercial Licence Usage
 * Licencees holding valid commercial Neptus licences may use this file
 * in accordance with the commercial licence agreement provided with the
 * Software or, alternatively, in accordance with the terms contained in a
 * written agreement between you and Universidade do Porto. For licensing
 * terms, conditions, and further information contact lsts@fe.up.pt.
 *
 * European Union Public Licence - EUPL v.1.1 Usage
 * Alternatively, this file may be used under the terms of the EUPL,
 * Version 1.1 only (the "Licence"), appearing in the file LICENCE.md
 * included in the packaging of this file. You may not use this work
 * except in compliance with the Licence. Unless required by applicable
 * law or agreed to in writing, software distributed under the Licence is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific
 * language governing permissions and limitations at
 * https://www.lsts.pt/neptus/licence.
 *
 * For more information please see <http://lsts.fe.up.pt/neptus>.
 *
 * Author: Pedro Gonçalves
 * Apr 4, 2015
 */
package pt.lsts.neptus.plugins.vision;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalityType;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.VideoCapture;
import org.opencv.imgproc.Imgproc;

import com.google.common.eventbus.Subscribe;

import foxtrot.AsyncTask;
import foxtrot.AsyncWorker;

import net.miginfocom.swing.MigLayout;

import pt.lsts.imc.CcuEvent;
import pt.lsts.imc.EstimatedState;
import pt.lsts.imc.MapFeature;
import pt.lsts.imc.MapPoint;
import pt.lsts.neptus.NeptusLog;
import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.console.ConsolePanel;
import pt.lsts.neptus.i18n.I18n;
import pt.lsts.neptus.plugins.NeptusProperty;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.Popup;
import pt.lsts.neptus.plugins.Popup.POSITION;
import pt.lsts.neptus.renderer2d.LayerPriority;
import pt.lsts.neptus.types.coord.LocationType;
import pt.lsts.neptus.types.map.AbstractElement;
import pt.lsts.neptus.types.map.MapGroup;
import pt.lsts.neptus.types.map.MapType;
import pt.lsts.neptus.types.map.MarkElement;
import pt.lsts.neptus.types.mission.MapMission;
import pt.lsts.neptus.types.mission.MissionType;
import pt.lsts.neptus.util.FileUtil;
import pt.lsts.neptus.util.GuiUtils;
import pt.lsts.neptus.util.ImageUtils;
import pt.lsts.neptus.util.SearchOpenCv;
import pt.lsts.neptus.util.UtilCv;
import pt.lsts.neptus.util.conf.ConfigFetch;

/**
 * Neptus Plugin for Video Stream and tag frame/object
 * 
 * @author pedrog
 * @category Vision
 */
@SuppressWarnings("serial")
@Popup(pos = POSITION.RIGHT, width=640, height=480, accelerator = 'R')
@LayerPriority(priority=0)
@PluginDescription(name="Video Stream", version="1.3", author="Pedro Gonçalves", description="Plugin for View video Stream TCP-Ip/IPCam", icon="pt/lsts/neptus/plugins/IPCam/camera.png")
public class Vision extends ConsolePanel implements ItemListener{

    private static final String BASE_FOLDER_FOR_IMAGES = "log/images";
    private static final String BASE_FOLDER_FOR_URLINI = "ipUrl.ini";

    @NeptusProperty(name = "Axis Camera RTPS URL", editable = false)
    private String camRtpsUrl = "rtsp://10.0.20.207:554/live/ch01_0";
    
    @NeptusProperty(name = "HOST IP for TCP-RasPiCam", editable = false)
    private String ipHost = "10.0.20.130";

    @NeptusProperty(name = "Port Number for TCP-RasPiCam", editable = false)
    private int portNumber = 2424;
    
    @NeptusProperty(name = "Cam Tilt Deg Value", editable = true)
    private double camTiltDeg = 45.0f;//this value may be in configuration
   
    //Opencv library name
    private Socket clientSocket = null;
    //Send data for sync 
    private PrintWriter out = null; 
    //Buffer for data image
    private InputStream is = null;
    //Buffer for info of data image
    private BufferedReader in = null;
    //Flag state of TCP connection
    private boolean tcpOK = false;
    //Strut Video Capture Opencv
    private VideoCapture capture;
    private VideoCapture captureSave;
    //Width size of image
    private int widthImgRec;
    //Height size of image
    private int heightImgRec;
    //Width size of Console
    private int widhtConsole = 640;
    //Height size of Console
    private int heightConsole = 480;
    //Black Image
    private Scalar black = new Scalar(0);
    //flag for state of neptus logo
    private boolean noVideoLogoState = false;
    //Scale factor of x pixel
    private float xScale;
    //Scale factor of y pixel
    private float yScale;
    //x pixel cord
    private int xPixel;
    //y pixel cord
    private int yPixel;
    //read size of pack compress
    private String line;
    //Buffer for data receive from DUNE over TCP
    private String duneGps;
    //Size of image received
    private int lengthImage;
    //buffer for save data receive
    private byte[] data;
    //Buffer image for JFrame/showImage
    private BufferedImage temp;
    //Flag - start acquired image
    private boolean raspiCam = false;
    //Flag - Lost connection to the vehicle
    private boolean state = false;
    //Flag - Show/hide Menu JFrame
    private boolean show_menu = false;
    //Flag state of IP CAM
    private boolean ipCam = false;
    //Save image tag flag
    private boolean captureFrame = false;
    //Close comTCP state
    private boolean closeComState = false;
    //Url of IPCam
    private String[][] dataUrlIni;
    
    private boolean closingPanel = false;
    
    //JLabel for image
    private JLabel picLabel;
    //JPanel for display image
    private JPanel panelImage;
    //JPanel for info and config values
    private JPanel config;
    //JText info of data receive
    private JLabel txtText;
    //JText of data receive over IMC message
    private JLabel txtData;
    //JText of data warning message
    private JLabel warningText;
    //JText of data receive over DUNE TCP message
    private JLabel txtDataTcp;
    //JFrame for menu options
    private JDialog menu;
    //CheckBox to save image to HD
    private JCheckBox saveToDiskCheckBox;
    //JPopup Menu
    private JPopupMenu popup;
    //Flag to enable/disable zoom 
    private boolean zoomMask = false;
    
    //String for the info treatment 
    private String info;
    //String for the info of Image Size Stream
    private String infoSizeStream;
    //Data system
    private Date date = new Date();
    //Location of log folder
    private String logDir;
    //Decompress data received 
    Inflater decompresser = new Inflater(false);
    //Create an expandable byte array to hold the decompressed data
    ByteArrayOutputStream bos;
    //Image resize
    private Mat matResize;
    //Image receive
    private Mat mat;
    //Image receive to save
    private Mat matSaveImg;
    //Size of output frame
    private Size size = null;
    //Counter for image tag
    private int cntTag = 1;

    //counter for frame tag ID
    private short frameTagID = 1;
    //lat, lon: frame Tag pos to be marked as POI
    private double lat,lon;

    //Flag for IPCam Ip Check
    boolean statePingOk = false;
    //JPanel for color state of ping to host IPCam
    private JPanel colorStateIPCam;
    //JDialog for IPCam Select
    private JDialog ipCamPing;
    //JPanel for IPCam Select (MigLayout)
    private JPanel ipCamCheck = new JPanel(new MigLayout());
    //JButton to confirm IPCam
    private JButton selectIPCam;
    //JComboBox for list of IPCam in ipUrl.ini
    private JComboBox<String> ipCamList;
    //row select from string matrix of IPCam List
    private int rowSelect;
    //JLabel for text IPCam Ping
    private JLabel jlabel;
    //JTextField for IPCam name
    private JTextField fieldName = new JTextField(I18n.text("Name"));
    //JTextField for IPCam ip
    private JTextField fieldIP = new JTextField(I18n.text("IP"));
    //JTextField for IPCam url
    private JTextField fieldUrl = new JTextField(I18n.text("URL"));
    //state of ping to host
    private boolean statePing;
    //JPanel for zoom point
    private JPanel zoomImg = new JPanel();
    //Buffer image for zoom Img Cut
    private BufferedImage zoomImgCut;
    //JLabel to show zoom image
    private JLabel zoomLabel = new JLabel();
    //Graphics2D for zoom image scaling
    private Graphics2D graphics2D;
    //BufferedImage for zoom image scaling
    private BufferedImage scaledCutImage;
    //Buffer image for zoom image temp
    private BufferedImage zoomTemp;
    //PopPup zoom Image
    private JPopupMenu popupzoom;
    //cord x for zoom
    private int zoomX = 100;
    //cord y for zoom
    private int zoomY = 100;
    
    //check ip for Host - TCP
    //JFormattedTextField for host ip
    private JFormattedTextField hostIP;
    //JDialog to check host connection
    private JDialog ipHostPing;
    //JPanel for host ip check
    private JPanel ipHostCheck;
    //Flag of ping state to host
    private boolean pingHostOk = false;
    //Flag for Histogram image
    private boolean histogramflag = false;
    //Flag to save snapshot
    private boolean saveSnapshot = false;
    
    //*** TEST FOR SAVE VIDEO **/
    private File outputfile;
    private boolean flagBuffImg = false;
    private int cnt = 0;
    private int FPS = 10;
    //*************************/
    
    //worker thread designed to acquire the data packet from DUNE
    private Thread updater = null; 
    //worker thread designed to save image do HD
    private Thread saveImg = null;
    //worker thread create ipUrl.ini in conf folder
    private Thread createIPUrl = null;
    
    public Vision(ConsoleLayout console) {
        super(console);
        
        if(findOpenCV()) {
            //clears all the unused initializations of the standard ConsolePanel
            removeAll();
            //Resize Console
            this.addComponentListener(new ComponentAdapter() {  
                public void componentResized(ComponentEvent evt) {
                    Component c = evt.getComponent();
                    widhtConsole = c.getSize().width;
                    heightConsole = c.getSize().height;
                    widhtConsole = widhtConsole - 22;
                    heightConsole = heightConsole - 22;
                    xScale = (float)widhtConsole/widthImgRec;
                    yScale = (float)heightConsole/heightImgRec;
                    size = new Size(widhtConsole, heightConsole);
                    matResize = new Mat(heightConsole, widhtConsole, CvType.CV_8UC3);
                    if(!raspiCam && !ipCam)
                        initImage();
                }
            });
            
            //Mouse click
            mouseListenerInit();
            
            //Detect key-pressed
            this.addKeyListener(new KeyListener() {            
                @Override
                public void keyReleased(KeyEvent e) {
                    if(e.getKeyCode() == KeyEvent.VK_Z && zoomMask) {
                        zoomMask = false;
                        popupzoom.setVisible(false);
                    }
                }
                @Override
                public void keyPressed(KeyEvent e) {
                    if((e.getKeyCode() == KeyEvent.VK_Z) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0) && !zoomMask) {
                        if(raspiCam || ipCam) {
                            zoomMask = true;
                            popupzoom.add(zoomImg);
                        }
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_I) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        checkIPCam();
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_R) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        checkHostIp();
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_X) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        NeptusLog.pub().info("Clossing all Video Stream...");
                        raspiCam = false;
                        state = false;
                        ipCam = false;
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_C) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        menu.setVisible(true);
                    }
                    else if(e.getKeyChar() == 'z' && zoomMask) {
                        int xLocMouse = MouseInfo.getPointerInfo().getLocation().x - getLocationOnScreen().x - 11;
                        int yLocMouse = MouseInfo.getPointerInfo().getLocation().y - getLocationOnScreen().y - 11;
                        if(xLocMouse < 0)
                            xLocMouse = 0;
                        if(yLocMouse < 0)
                            yLocMouse = 0;

                        if(xLocMouse + 52 < panelImage.getSize().getWidth() && xLocMouse - 52 > 0 && yLocMouse + 60 < panelImage.getSize().getHeight() && yLocMouse - 60 > 0){
                            zoomX = xLocMouse;
                            zoomY = yLocMouse;
                            popupzoom.setLocation(MouseInfo.getPointerInfo().getLocation().x - 150, MouseInfo.getPointerInfo().getLocation().y - 150);
                        }
                        else {
                            popupzoom.setVisible(false);
                        }
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_H) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        histogramflag = !histogramflag;
                    }
                    else if((e.getKeyCode() == KeyEvent.VK_S) && ((e.getModifiers() & KeyEvent.ALT_MASK) != 0)) {
                        saveSnapshot = true;
                    }
                }
                @Override
                public void keyTyped(KeyEvent e) {
                }
            });
            this.setFocusable(true);
        }
        else {
            NeptusLog.pub().error("Opencv not found.");
            closingPanel = true;
            setBackground(Color.BLACK);
            //JLabel for image
            this.setLayout(new MigLayout("filly"));
            //JLabel info
            warningText = new JLabel("  " + I18n.text("Please install OpenCV 2.4 and its dependencies.") + "  ");
            warningText.setForeground(new Color(252, 68, 35));
            warningText.setFont(new Font("Courier New", Font.ITALIC, 18));
            this.add(warningText); 
        }
        return;
    }
    
    //Mouse click Listener
    private void mouseListenerInit() {
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1){
                    if(raspiCam || ipCam) {
                        xPixel = (int) ((e.getX() - 11) / xScale);  //shift window bar
                        yPixel = (int) ((e.getY() - 11) / yScale) ; //shift window bar
                        if(raspiCam && !ipCam && tcpOK) {
                            if (xPixel >= 0 && yPixel >= 0 && xPixel <= widthImgRec && yPixel <= heightImgRec)
                                out.printf("%d#%d;\0", xPixel,yPixel);
                        }
                        //place mark on map as POI
                        placeLocationOnMap();
                    }
                }
                if (e.getButton() == MouseEvent.BUTTON3) {
                    popup = new JPopupMenu();
                    JMenuItem item1;
                    popup.add(item1 = new JMenuItem(I18n.text("Start")+" RasPiCam", ImageUtils.createImageIcon(String.format("images/menus/raspicam.png")))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            checkHostIp();
                        }
                    });
                    item1.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, ActionEvent.ALT_MASK));
                    JMenuItem item2;
                    popup.add(item2 = new JMenuItem(I18n.text("Close all connections"), ImageUtils.createImageIcon(String.format("images/menus/exit.png")))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            NeptusLog.pub().info("Clossing all Video Stream...");
                            noVideoLogoState = false;
                            if(raspiCam && tcpOK) {
                                try {
                                    clientSocket.close();
                                }
                                catch (IOException e1) {
                                    e1.printStackTrace();
                                }
                            }
                            raspiCam = false;
                            state = false;
                            ipCam = false;
                        }
                    });
                    item2.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_X, ActionEvent.ALT_MASK));
                    JMenuItem item3;  
                    popup.add(item3 = new JMenuItem(I18n.text("Start IPCam"), ImageUtils.createImageIcon("images/menus/camera.png"))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            checkIPCam();        
                        }
                    });
                    popup.addSeparator();
                    item3.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_I, ActionEvent.ALT_MASK));
                    JMenuItem item4;
                    popup.add(item4 = new JMenuItem(I18n.text("Config"), ImageUtils.createImageIcon(String.format("images/menus/configure.png")))).addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            menu.setVisible(true);
                        }
                    });
                    item4.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, ActionEvent.ALT_MASK));
                    JMenuItem item5;
                    popup.add(item5 = new JMenuItem(I18n.text("Histogram filter on/off"), ImageUtils.createImageIcon("images/menus/histogram.png"))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            histogramflag = !histogramflag;
                        }
                    });
                    item5.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, ActionEvent.ALT_MASK));
                    JMenuItem item6;
                    popup.add(item6 = new JMenuItem(I18n.text("Snapshot"), ImageUtils.createImageIcon("images/menus/snapshot.png"))).addActionListener(new ActionListener() {
                        public void actionPerformed(ActionEvent e) {
                            saveSnapshot = true;
                        }
                    });
                    item6.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, ActionEvent.ALT_MASK));
                    popup.addSeparator();
                    JLabel infoZoom = new JLabel(I18n.text("For zoom use Alt-Z"));
                    popup.add(infoZoom, JMenuItem.CENTER_ALIGNMENT);
                    popup.show((Component) e.getSource(), e.getX(), e.getY());
                }
            }
        });
    }

    //Check ip given by user
    private void checkHostIp() {
        ipHostPing = new JDialog(SwingUtilities.getWindowAncestor(Vision.this), I18n.text("Host IP")+" - RasPiCam");
        ipHostPing.setModalityType(ModalityType.DOCUMENT_MODAL);
        ipHostPing.setSize(340, 80);
        ipHostPing.setLocationRelativeTo(Vision.this);
        ImageIcon imgIPCam = ImageUtils.createImageIcon(String.format("images/menus/raspicam.png"));
        ipHostPing.setIconImage(imgIPCam.getImage());
        ipHostPing.setResizable(false);
        ipHostPing.setBackground(Color.GRAY);
        ipHostCheck = new JPanel(new MigLayout());
        JLabel infoHost = new JLabel(I18n.text("Host Ip: "));
        ipHostCheck.add(infoHost, "cell 0 4 3 1");
        hostIP = new JFormattedTextField();
        hostIP.setValue(new String());
        hostIP.setColumns(8);
        hostIP.setValue(ipHost);
        hostIP.addPropertyChangeListener("value", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                ipHost = new String((String) evt.getNewValue());
            }
        });
        ipHostCheck.add(hostIP);
        colorStateIPCam = new JPanel();
        jlabel = new JLabel(I18n.text("OFF"));
        jlabel.setFont(new Font("Verdana",1,14));
        colorStateIPCam.setBackground(Color.RED);
        colorStateIPCam.add(jlabel);
        ipHostCheck.add(colorStateIPCam,"h 30!, w 30!");
        selectIPCam = new JButton(I18n.text("Check"));
        selectIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                statePing = false;
                colorStateIPCam.setBackground(Color.LIGHT_GRAY);
                jlabel.setText("---");
                AsyncTask task = new AsyncTask() {
                    @Override
                    public Object run() throws Exception {
                        statePing = pingIPCam(ipHost);
                        return null;
                    }
                    @Override
                    public void finish() {
                        if(statePing) {
                            colorStateIPCam.setBackground(Color.GREEN);
                            jlabel.setText("ON");
                            pingHostOk = true;
                            selectIPCam.setEnabled(true);
                        }
                        else {
                            colorStateIPCam.setBackground(Color.RED);
                            jlabel.setText("OFF");
                            pingHostOk = false;
                            selectIPCam.setEnabled(false);
                        }
                        selectIPCam.validate(); selectIPCam.repaint();
                    }
                };
                AsyncWorker.getWorkerThread().postTask(task); 
            }
        });
        ipHostCheck.add(selectIPCam,"h 30!");
        selectIPCam = new JButton(I18n.text("OK"));
        selectIPCam.setEnabled(false);
        selectIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(pingHostOk) {
                    ipHostPing.setVisible(false);
                    if(!ipCam) {
                        raspiCam = true;
                        ipCam = false;
                        closeComState = false;
                    }
                    else {
                        NeptusLog.pub().info("Clossing IPCam Stream...");
                        closeComState = false;
                        raspiCam = true;
                        state = false;
                        ipCam = false;
                    }
                }
            }
        });
        ipHostCheck.add(selectIPCam,"h 30!");
        ipHostPing.add(ipHostCheck);
        ipHostPing.setVisible(true);
    }
    
    //Read ipUrl.ini to find IPCam ON
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void checkIPCam() {
        dataUrlIni = readIPUrl();
        int sizeDataUrl = dataUrlIni.length;
        String nameIPCam[] = new String[sizeDataUrl];
        for (int i=0; i < sizeDataUrl; i++)
            nameIPCam[i] = dataUrlIni[i][0];
        
        ipCamPing = new JDialog(SwingUtilities.getWindowAncestor(Vision.this), I18n.text("Select IPCam"));
        ipCamPing.setResizable(true);
        ipCamPing.setModalityType(ModalityType.DOCUMENT_MODAL);
        ipCamPing.setSize(440, 200);
        ipCamPing.setLocationRelativeTo(Vision.this);
        ipCamCheck = new JPanel(new MigLayout());
        ImageIcon imgIPCam = ImageUtils.createImageIcon("images/menus/camera.png");
        ipCamPing.setIconImage(imgIPCam.getImage());
        ipCamPing.setResizable(false);
        ipCamPing.setBackground(Color.GRAY);                  
        ipCamList = new JComboBox(nameIPCam);
        ipCamList.setSelectedIndex(0);
        ipCamList.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JComboBox cb = (JComboBox)e.getSource();
                rowSelect = cb.getSelectedIndex();
                if(rowSelect >= 0) {
                    colorStateIPCam.setBackground(Color.LIGHT_GRAY);
                    jlabel.setText("---");
                    statePingOk = false;
                    fieldName.setText(I18n.text(dataUrlIni[rowSelect][0]));
                    fieldName.validate(); fieldName.repaint();
                    fieldIP.setText(I18n.text(dataUrlIni[rowSelect][1]));
                    fieldIP.validate(); fieldIP.repaint();
                    fieldUrl.setText(I18n.text(dataUrlIni[rowSelect][2]));
                    fieldUrl.validate(); fieldUrl.repaint();
                    statePing = false;
                    AsyncTask task = new AsyncTask() {
                        @Override
                        public Object run() throws Exception {
                            statePing = pingIPCam(dataUrlIni[rowSelect][1]);
                            return null;
                        }
                        @Override
                        public void finish() {
                            if(statePing) {
                                selectIPCam.setEnabled(true);
                                camRtpsUrl = dataUrlIni[rowSelect][2];
                                colorStateIPCam.setBackground(Color.GREEN);
                                jlabel.setText("ON");
                            }
                            else {
                                selectIPCam.setEnabled(false);
                                colorStateIPCam.setBackground(Color.RED);
                                jlabel.setText("OFF");
                            }
                            selectIPCam.validate(); selectIPCam.repaint();
                        }
                    };
                    AsyncWorker.getWorkerThread().postTask(task);
                }
                else {
                    statePingOk = false;
                    colorStateIPCam.setBackground(Color.RED);
                    jlabel.setText("OFF");
                }
            }
        });
        ipCamCheck.add(ipCamList,"split 3, width 50:250:250, center");
        
        colorStateIPCam = new JPanel();
        jlabel = new JLabel(I18n.text("OFF"));
        jlabel.setFont(new Font("Verdana",1,14));
        colorStateIPCam.setBackground(Color.RED);
        colorStateIPCam.add(jlabel);
        ipCamCheck.add(colorStateIPCam,"h 30!, w 30!");
        
        selectIPCam = new JButton(I18n.text("Select IPCam"), imgIPCam);
        selectIPCam.setEnabled(false);
        selectIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if(statePingOk) {
                    NeptusLog.pub().info("IPCam Select: "+dataUrlIni[rowSelect][0]);
                    ipCamPing.setVisible(false);
                    if(!raspiCam) {
                        ipCam = true;
                        raspiCam = false;
                        state = false;
                    }
                    else {
                        NeptusLog.pub().info("Clossing RasPiCam Stream...");
                        ipCam = true;
                        raspiCam = false;
                        state = false;
                        closeComState = true;
                    }
                }
            }
        });
        ipCamCheck.add(selectIPCam,"h 30!, wrap");
        
        JButton addNewIPCam = new JButton(I18n.text("Add New IPCam"));
        addNewIPCam.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                //Execute when button is pressed
                writeToFile(String.format("\n%s (%s)#%s#%s", fieldName.getText(), fieldIP.getText(), fieldIP.getText(), fieldUrl.getText()));
                AsyncTask task = new AsyncTask() {
                    @Override
                    public Object run() throws Exception {
                        dataUrlIni = readIPUrl();
                        return null;
                    }
                    @Override
                    public void finish() {
                        int sizeDataUrl = dataUrlIni.length;
                        String nameIPCam[] = new String[sizeDataUrl];
                        for (int i=0; i < sizeDataUrl; i++)
                            nameIPCam[i] = dataUrlIni[i][0];
                        
                        ipCamList.removeAllItems();
                        for (int i = 0; i < nameIPCam.length; i++) {
                            String sample = nameIPCam[i];
                            ipCamList.addItem(sample);
                        }
                    }
                };
                AsyncWorker.getWorkerThread().postTask(task);
            }
        });
        
        ipCamCheck.add(fieldName, "w 410!, wrap");
        ipCamCheck.add(fieldIP, "w 410!, wrap");
        ipCamCheck.add(fieldUrl, "w 410!, wrap");
        ipCamCheck.add(addNewIPCam, "w 120!, center");
        
        ipCamPing.add(ipCamCheck);
        ipCamPing.pack();
        ipCamPing.setVisible(true);
    }
    
    //Write to file
    private void writeToFile(String textString){
        String iniRsrcPath = FileUtil.getResourceAsFileKeepName(BASE_FOLDER_FOR_URLINI);
        File confIni = new File(ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URLINI);
        if (!confIni.exists()) {
            FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
        }
        FileUtil.saveToFile(confIni.getAbsolutePath(), textString);
    }
    
    //Ping CamIp
    private boolean pingIPCam (String host) {
        statePingOk = UtilVision.pingIp(host);
        return statePingOk;
    }
    
    //Read file
    private String[][] readIPUrl() {
        String iniRsrcPath = FileUtil.getResourceAsFileKeepName(BASE_FOLDER_FOR_URLINI);
        File confIni = new File(ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URLINI);
        if (!confIni.exists()) {
            FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
        }
        return UtilVision.readIpUrl(confIni);
    }
    
    private String timestampToReadableHoursString(long timestamp){
        Date date = new Date(timestamp);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
        return format.format(date);
    }

    /**
     * Adapted from ContactMarker.placeLocationOnMap()
     */
    private void placeLocationOnMap() {
        if (getConsole().getMission() == null)
            return;

        double lat = this.lat;
        double lon = this.lon;
        long timestamp = System.currentTimeMillis();
        String id = I18n.text("FrameTag") + "-" + frameTagID + "-" + timestampToReadableHoursString(timestamp);

        boolean validId = false;
        while (!validId) {
            id = JOptionPane.showInputDialog(getConsole(), I18n.text("Please enter new mark name"), id);
            if (id == null)
                return;
            AbstractElement elems[] = MapGroup.getMapGroupInstance(getConsole().getMission()).getMapObjectsByID(id);
            if (elems.length > 0) {
                GuiUtils.errorMessage(getConsole(), I18n.text("Add mark"),
                        I18n.text("The given ID already exists in the map. Please choose a different one"));
            }
            else {
                validId = true;
            }
        }
        frameTagID++;//increment ID

        MissionType mission = getConsole().getMission();
        LinkedHashMap<String, MapMission> mapList = mission.getMapsList();
        if (mapList == null)
            return;
        if (mapList.size() == 0)
            return;
        // MapMission mapMission = mapList.values().iterator().next();
        MapGroup.resetMissionInstance(getConsole().getMission());
        MapType mapType = MapGroup.getMapGroupInstance(getConsole().getMission()).getMaps()[0];// mapMission.getMap();
        // NeptusLog.pub().info("<###>MARKER --------------- " + mapType.getId());
        MarkElement contact = new MarkElement(mapType.getMapGroup(), mapType);

        contact.setId(id);
        contact.setCenterLocation(new LocationType(lat,lon));
        mapType.addObject(contact);
        mission.save(false);
        MapPoint point = new MapPoint();
        point.setLat(lat);
        point.setLon(lon);
        point.setAlt(0);
        MapFeature feature = new MapFeature();
        feature.setFeatureType(MapFeature.FEATURE_TYPE.POI);
        feature.setFeature(Arrays.asList(point));
        CcuEvent event = new CcuEvent();
        event.setType(CcuEvent.TYPE.MAP_FEATURE_ADDED);
        event.setId(id);
        event.setArg(feature);
        this.getConsole().getImcMsgManager().broadcastToCCUs(event);
        NeptusLog.pub().info("placeLocationOnMap: " + id + " - Pos: lat: " + this.lat + " ; lon: " + this.lon);
        captureFrame = true;
    }

    //Print Image to JPanel
    private void showImage(BufferedImage image) {
        picLabel.setIcon(new ImageIcon(image));
        panelImage.revalidate();
        panelImage.add(picLabel, BorderLayout.CENTER);
        repaint();
    }
        
    //Config Layout
    private void configLayout() {
        //Create Buffer (type MAT) for Image resize
        matResize = new Mat(heightConsole, widhtConsole, CvType.CV_8UC3);
        
        //Config JFrame zoom img
        zoomImg.setSize(300, 300);
        popupzoom = new JPopupMenu();
        popupzoom.setSize(300, 300);
        //Create folder to save image data
        //Create folder image in log if don't exist
        File dir = new File(String.format(BASE_FOLDER_FOR_IMAGES));
        dir.mkdir();
        //Create folder Image to save data received
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s", date.toString().replace(":", "-")));
        dir.mkdir();
        //Create folder Image Tag
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s/imageTag", date.toString().replace(":", "-")));
        dir.mkdir();
        //Create folder Image Save
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s/imageSave", date.toString().replace(":", "-")));
        dir.mkdir();
        //Create folder Image Snapshot Save
        dir = new File(String.format(BASE_FOLDER_FOR_IMAGES + "/%s/snapshotImage", date.toString().replace(":", "-")));
        dir.mkdir();
        logDir = String.format(BASE_FOLDER_FOR_IMAGES + "/%s", date.toString().replace(":", "-"));
        
        //JLabel for image
        picLabel = new JLabel();
        //JPanel for Image
        panelImage = new JPanel();
        panelImage.setBackground(Color.LIGHT_GRAY);
        panelImage.setSize(this.getWidth(), this.getHeight());
        this.setLayout(new MigLayout());
        this.add(panelImage, BorderLayout.CENTER);
        
        //JPanel for info and config values      
        config = new JPanel(new MigLayout());

        //JCheckBox save to HD
        saveToDiskCheckBox = new JCheckBox(I18n.text("Save Image to Disk"));
        saveToDiskCheckBox.setMnemonic(KeyEvent.VK_C);
        saveToDiskCheckBox.setSelected(false);
        saveToDiskCheckBox.addItemListener(this);
        config.add(saveToDiskCheckBox,"width 160:180:200, h 40!, wrap");
        
        //JLabel info Data received
        txtText = new JLabel();
        txtText.setToolTipText(I18n.text("Info of Frame Received"));
        info = String.format("Img info");
        txtText.setText(info);
        config.add(txtText, "cell 0 4 3 1, wrap");
        
        //JLabel info Data GPS received TCP
        txtDataTcp = new JLabel();
        txtDataTcp.setToolTipText(I18n.text("Info of GPS Received over TCP (Raspi)"));
        info = String.format("GPS TCP");
        txtDataTcp.setText(info);
        config.add(txtDataTcp, "cell 0 5 3 1, wrap");
        
        //JLabel info
        txtData = new JLabel();
        txtData.setToolTipText(I18n.text("Info of GPS Received over IMC"));
        info = String.format("GPS IMC");
        txtData.setText(info);
        config.add(txtData, "cell 0 6 3 1, wrap");
                 
        menu = new JDialog(SwingUtilities.getWindowAncestor(Vision.this), I18n.text("Menu Config"));
        menu.setResizable(false);
        menu.setModalityType(ModalityType.DOCUMENT_MODAL);
        menu.setSize(450, 350);
        menu.setLocationRelativeTo(Vision.this);
        menu.setVisible(show_menu);
        ImageIcon imgMenu = ImageUtils.createImageIcon(String.format("images/menus/configure.png"));
        menu.setIconImage(imgMenu.getImage());
        menu.add(config);
    }
    
    @Override
    public void itemStateChanged(ItemEvent e) {
        //checkbox listener
        Object source = e.getItemSelectable();
        if (source == saveToDiskCheckBox) {
            if ((raspiCam == true || ipCam == true) && saveToDiskCheckBox.isSelected() == true) {
                flagBuffImg = true;
            }
            if ((raspiCam == false && ipCam == false) || saveToDiskCheckBox.isSelected() == false) {
                flagBuffImg=false;
            }
        }
    }
        
    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        if(raspiCam){
            NeptusLog.pub().info("Closing TCP connection to RaspiCam ");
            if(raspiCam && tcpOK)
                closeTcpCom();
        }
        closingPanel = true;
    }
    
    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        if(findOpenCV()){
            getConsole().getImcMsgManager().addListener(this);
            configLayout();
            createIPUrl = createFile();
            createIPUrl.start();
            updater = updaterThread();
            updater.start();
            saveImg = updaterThreadSave();
            saveImg.start();
        }
        else {
            NeptusLog.pub().error("Opencv not found.");
            closingPanel = true;
            return;
        }
    }
    
    private Thread createFile() {
        Thread ipUrl = new Thread("Create file IPUrl Thread") {
            @Override
            public void run() {
                String iniRsrcPath = FileUtil.getResourceAsFileKeepName(BASE_FOLDER_FOR_URLINI);
                File confIni = new File(ConfigFetch.getConfFolder() + "/" + BASE_FOLDER_FOR_URLINI);
                if (!confIni.exists()) {
                    FileUtil.copyFileToDir(iniRsrcPath, ConfigFetch.getConfFolder());
                }
            }
        };
        ipUrl.setDaemon(true);
        return ipUrl;
    }

    //Find OPENCV JNI in host PC
    private boolean findOpenCV() {
        return SearchOpenCv.searchJni();
    }
    
    //Get size of image over TCP
    private void initSizeImage() {
        //Width size of image
        try {
            widthImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException | IOException e) {
            e.printStackTrace();
        }
        //Height size of image
        try {
            heightImgRec = Integer.parseInt(in.readLine());
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        xScale = (float) widhtConsole / widthImgRec;
        yScale = (float) heightConsole / heightImgRec;
        //Create Buffer (type MAT) for Image receive
        mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
    }
    
    //Thread to handle data receive
    private Thread updaterThread() {
        Thread ret = new Thread("Video Stream Thread") {
            @Override
            public void run() {
                initImage();
                while(true) {
                    if (closingPanel) {
                        raspiCam = false;
                        state = false;
                        ipCam = false;
                    }
                    else if (raspiCam && !ipCam ) {
                        if(state == false) {
                            //connection
                            if(tcpConnection()) {
                                //receive info of image size
                                initSizeImage();
                                state = true;
                            }
                        }
                        else {
                            //receive data image
                            if(!closeComState)
                                receivedDataImage();
                            else
                                closeTcpCom();
                            if(!raspiCam && !state)
                                closeTcpCom();
                        }
                    }
                    else if (!raspiCam && ipCam) {
                        if (state == false){
                            //Create Buffer (type MAT) for Image receive
                            mat = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
                            capture = new VideoCapture();
                            capture.open(camRtpsUrl);
                            if (capture.isOpened()) {
                                state = true;
                                cntTag = 1;
                                NeptusLog.pub().info("Video Strem from IPCam is captured");
                            }
                            else {
                                ipCam = false;
                                NeptusLog.pub().info("Video Strem from IPCam is not captured");
                            }
                        }
                        //IPCam Capture
                        else if(!raspiCam && ipCam && state) {
                            long startTime = System.currentTimeMillis();
                            capture.grab();
                            capture.read(mat);
                            while(mat.empty()) {
                                NeptusLog.pub().error(I18n.text("ERROR capturing img of raspicam"));
                                capture.read(mat);
                            }
                            xScale = (float) widhtConsole / mat.cols();
                            yScale = (float) heightConsole / mat.rows();
                            Imgproc.resize(mat, matResize, size);
                            //Convert Mat to BufferedImage
                            temp = UtilCv.matToBufferedImage(matResize);
                            //Display image in JFrame
                            long stopTime = System.currentTimeMillis();
                            long fpsResult = stopTime - startTime;
                            if(fpsResult != 0)
                                infoSizeStream = String.format("Size(%d x %d) | Scale(%.2f x %.2f) | FPS:%d |\t\t\t", mat.cols(), mat.rows(),xScale,yScale,(int)(1000/fpsResult));

                            txtText.setText(infoSizeStream);
                            if(histogramflag) {
                                if(zoomMask) {
                                    zoomTemp = temp;
                                    getCutImage(UtilCv.histogramCv(zoomTemp), zoomX, zoomY);
                                    popupzoom.setVisible(true);
                                }
                                else
                                    popupzoom.setVisible(false);

                                if (saveSnapshot) {
                                    UtilCv.saveSnapshot(
                                            UtilCv.addText(UtilCv.histogramCv(temp), I18n.text("Histogram - On"),
                                                    Color.GREEN, temp.getWidth() - 5, 20),
                                            String.format(logDir + "/snapshotImage"));
                                    saveSnapshot = false;
                                }
                                showImage(UtilCv.addText(UtilCv.histogramCv(temp), I18n.text("Histogram - On"),
                                        Color.GREEN, temp.getWidth() - 5, 20));
                            }
                            else {
                                if(zoomMask) {
                                    getCutImage(temp, zoomX, zoomY);
                                    popupzoom.setVisible(true);
                                }
                                else
                                    popupzoom.setVisible(false);
                                
                                if (saveSnapshot) {
                                    UtilCv.saveSnapshot(UtilCv.addText(temp, I18n.text("Histogram - Off"), Color.RED,
                                            temp.getWidth() - 5, 20), String.format(logDir + "/snapshotImage"));
                                    saveSnapshot = false;
                                }
                                showImage(UtilCv.addText(temp, I18n.text("Histogram - Off"), Color.RED,
                                        temp.getWidth() - 5, 20));
                            }

                            //save image tag to disk
                            if( captureFrame ) {
                                xPixel = xPixel - widhtConsole/2;
                                yPixel = -(yPixel - heightConsole/2);
                                String imageTag = null;
                                if(info.length() < 12)
                                    imageTag = String.format("%s/imageTag/(%d)_(IMC) ERROR_X=%d_Y=%d.jpeg", logDir, cntTag, xPixel, yPixel);
                                else
                                    imageTag = String.format("%s/imageTag/(%d)_%s_X=%d_Y=%d.jpeg", logDir, cntTag, info, xPixel, yPixel);
                                
                                outputfile = new File(imageTag);
                                try {
                                    ImageIO.write(temp, "jpeg", outputfile);
                                }
                                catch (IOException e) {
                                    e.printStackTrace();
                                }
                                captureFrame = false;
                                cntTag++;
                            }
                        }
                    }
                    else{
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        initImage();
                    }
                    if (closingPanel)
                        break;
                }
            }
        };
        ret.setDaemon(true);
        return ret;
    }

    //Thread to handle save image
    private Thread updaterThreadSave() {
        Thread si = new Thread("Save Image") {
            @Override
            public void run() {
                matSaveImg = new Mat(heightImgRec, widthImgRec, CvType.CV_8UC3);
                boolean stateSetUrl = false;
                
                while(true){
                    if(ipCam && !stateSetUrl){
                        captureSave = new VideoCapture();
                        captureSave.open(camRtpsUrl);
                        if (captureSave.isOpened()) {
                            cntTag = 1;
                            stateSetUrl = true;
                        }
                    }
                    if(raspiCam){
                        stateSetUrl = false;
                        if(flagBuffImg == true) {
                            long startTime = System.currentTimeMillis();
                            String imageJpeg = null; 
                            try {
                                if(histogramflag){
                                    imageJpeg = String.format("%s/imageSave/%d_H.jpeg",logDir,cnt);
                                    outputfile = new File(imageJpeg);
                                    ImageIO.write(UtilCv.histogramCv(temp), "jpeg", outputfile);
                                }
                                else{
                                    imageJpeg = String.format("%s/imageSave/%d.jpeg",logDir,cnt);
                                    outputfile = new File(imageJpeg);
                                    ImageIO.write(temp, "jpeg", outputfile);
                                }
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                            cnt++;
                            long stopTime = System.currentTimeMillis();
                            while((stopTime - startTime) < (1000/FPS)) {
                                stopTime = System.currentTimeMillis();
                            } 
                        }
                        else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    if(ipCam && stateSetUrl) {
                        if(flagBuffImg == true) {
                            long startTime = System.currentTimeMillis();
                            captureSave.grab();
                            captureSave.read(matSaveImg);
                            if(!matSaveImg.empty()) {
                                String imageJpeg = null; 
                                try {
                                    if(histogramflag){
                                        imageJpeg = String.format("%s/imageSave/%d_H.jpeg",logDir,cnt);
                                        outputfile = new File(imageJpeg);
                                        ImageIO.write(UtilCv.histogramCv(UtilCv.matToBufferedImage(matSaveImg)), "jpeg", outputfile);
                                    }
                                    else{
                                        imageJpeg = String.format("%s/imageSave/%d.jpeg",logDir,cnt);
                                        outputfile = new File(imageJpeg);
                                        ImageIO.write(UtilCv.matToBufferedImage(matSaveImg), "jpeg", outputfile);
                                    }
                                }
                                catch (IOException e) {
                                    e.printStackTrace();
                                }
                                cnt++;
                                long stopTime = System.currentTimeMillis();
                                while((stopTime - startTime) < (1000/FPS)) {
                                    stopTime = System.currentTimeMillis();
                                }
                            } 
                        }
                        else {
                            try {
                                TimeUnit.MILLISECONDS.sleep(100);
                            }
                            catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }     
                    }
                    else {
                        try {
                            TimeUnit.MILLISECONDS.sleep(1000);
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (closingPanel)
                        break;
                }
           }
        };
        si.setDaemon(true);
        return si;
    }
    
    //IMC handle
    @Subscribe
    public void consume(EstimatedState msg) {   
        //System.out.println("Source Name "+msg.getSourceName()+"ID "+getMainVehicleId());
        if(msg.getSourceName().equals(getMainVehicleId()) && findOpenCV()) {
            try {
                // update the position of target
                //LAT and LON rad
                double latRad = msg.getLat();
                double lonRad = msg.getLon();
                //LAT and LON deg
                double latDeg = Math.toDegrees(latRad);
                double lonDeg = Math.toDegrees(lonRad);

                LocationType locationType = new LocationType(latDeg,lonDeg);

                //Offset (m)
                double offsetN = msg.getX();
                double offsetE = msg.getY();
                
                //Height of Vehicle
                double heightRelative = msg.getHeight() - msg.getZ();//absolute altitude - zero of that location
                locationType.setOffsetNorth(offsetN);
                locationType.setOffsetEast(offsetE);
                locationType.setHeight(heightRelative);

                info = String.format("(IMC) LAT: %f # LON: %f # ALT: %.2f m", lat, lon, heightRelative);
                LocationType tagLocationType = calcTagPosition(locationType.convertToAbsoluteLatLonDepth(), Math.toDegrees(msg.getPsi()), camTiltDeg);
                this.lat = tagLocationType.convertToAbsoluteLatLonDepth().getLatitudeDegs();
                this.lon = tagLocationType.convertToAbsoluteLatLonDepth().getLongitudeDegs();
                txtData.setText(info);
            }
            catch (Exception e) {
                NeptusLog.pub().error(e.getMessage(), e);
            }
        }
    }

    /**
     *
     * @param locationType
     * @param orientationDegrees
     * @param camTiltDeg
     * @return tagLocationType
     */
    private LocationType calcTagPosition(LocationType locationType, double orientationDegrees, double camTiltDeg) {
        double altitude = locationType.getHeight();
        double dist = Math.tan(Math.toRadians(camTiltDeg)) * (Math.abs(altitude));// hypotenuse
        double offsetN = Math.cos(Math.toRadians(orientationDegrees)) * dist;// oposite side
        double offsetE = Math.sin(Math.toRadians(orientationDegrees)) * dist;// adjacent side

        LocationType tagLocationType = locationType.convertToAbsoluteLatLonDepth();
        tagLocationType.setOffsetNorth(offsetN);
        tagLocationType.setOffsetEast(offsetE);
        return tagLocationType.convertToAbsoluteLatLonDepth();
    }

    //Fill cv::Mat image with zeros
    private void initImage() {
        if(!noVideoLogoState) {
            if(ImageUtils.getImage("images/novideo.png") == null) {
                matResize.setTo(black);
                temp = UtilCv.matToBufferedImage(matResize);
            }
            else { 
                temp = UtilVision.resizeBufferedImage(
                        ImageUtils.toBufferedImage(ImageUtils.getImage("images/novideo.png")), size);
            }
            
            if(temp != null){
                showImage(temp);
                noVideoLogoState = true;
            }
        }
    }
    
    //Received data Image
    private void receivedDataImage() {
        long startTime = System.currentTimeMillis();
        try {
            line = in.readLine();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }
        if (line == null){
            GuiUtils.errorMessage(panelImage, I18n.text("Connection error"), I18n.text("Lost connection with vehicle"),
                    ModalityType.DOCUMENT_MODAL);
            raspiCam = false;
            state = false;
            // closeTcpCom();
            try {
                clientSocket.close();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        else {        
            lengthImage = Integer.parseInt(line);
            //buffer for save data receive
            data = new byte[lengthImage];
            //Send 1 for server for sync data send
            out.println("1\0");
            //read data image (ZP)
            int read = 0;
            while (read < lengthImage) {
                int readBytes = 0;
                try {
                    readBytes = is.read(data, read, lengthImage-read);
                }
                catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
                if (readBytes < 0) {
                    System.err.println("stream ended");
                    closeTcpCom();
                    return;
                }
                read += readBytes;
            }           
            //Receive data GPS over tcp DUNE
            try {
                duneGps = in.readLine();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
            //Decompress data received 
            decompresser = new Inflater(false);
            decompresser.setInput(data,0,lengthImage);
            //Create an expandable byte array to hold the decompressed data
            bos = new ByteArrayOutputStream(data.length);
            //Decompress the data
            byte[] buf = new byte[(widthImgRec * heightImgRec * 3)];
            while (!decompresser.finished()) {
                try {
                    int count = decompresser.inflate(buf);                  
                    bos.write(buf, 0, count);
                } 
                catch (DataFormatException e) {
                    break;
                }
            }
            try {
                bos.close();
            } 
            catch (IOException e) {
            }
            // Get the decompressed data
            byte[] decompressedData = bos.toByteArray();
            
            //Transform byte data to cv::Mat (for display image)
            mat.put(0, 0, decompressedData);
            //Resize image to console size
            Imgproc.resize(mat, matResize, size);
            
            //Display image in JFrame
            if(histogramflag) {
                if (saveSnapshot) {
                    UtilCv.saveSnapshot(UtilCv.addText(UtilCv.histogramCv(temp), I18n.text("Histogram - On"),
                            Color.GREEN, temp.getWidth() - 5, 20), String.format(logDir + "/snapshotImage"));
                    saveSnapshot = false;
                }
                showImage(UtilCv.addText(UtilCv.histogramCv(temp), I18n.text("Histogram - On"), Color.GREEN,
                        temp.getWidth() - 5, 20));
            }
            else {
                if (saveSnapshot) {
                    UtilCv.saveSnapshot(
                            UtilCv.addText(temp, I18n.text("Histogram - Off"), Color.RED, temp.getWidth() - 5, 20),
                            String.format(logDir + "/snapshotImage"));
                    saveSnapshot = false;
                }
                showImage(UtilCv.addText(temp, I18n.text("Histogram - Off"), Color.RED, temp.getWidth() - 5, 20));
            }

            if (histogramflag) {
                showImage(UtilCv.addText(UtilCv.histogramCv(UtilCv.matToBufferedImage(matResize)),
                        I18n.text("Histogram - On"), Color.GREEN, matResize.cols() - 5, 20));
                if (saveSnapshot) {
                    UtilCv.saveSnapshot(
                            UtilCv.addText(UtilCv.histogramCv(UtilCv.matToBufferedImage(matResize)),
                                    I18n.text("Histogram - On"), Color.GREEN, matResize.cols() - 5, 20),
                            String.format(logDir + "/snapshotImage"));
                    saveSnapshot = false;
                }
            }
            else {
                showImage(UtilCv.addText(UtilCv.matToBufferedImage(matResize), I18n.text("Histogram - Off"), Color.RED,
                        matResize.cols() - 5, 20));
                if (saveSnapshot) {
                    UtilCv.saveSnapshot(UtilCv.addText(UtilCv.matToBufferedImage(matResize),
                            I18n.text("Histogram - On"), Color.RED, matResize.cols() - 5, 20),
                            String.format(logDir + "/snapshotImage"));
                    saveSnapshot = false;
                }
            }
            
            xScale = (float) widhtConsole / widthImgRec;
            yScale = (float) heightConsole / heightImgRec;
            long stopTime = System.currentTimeMillis();
            while((stopTime - startTime) < (1000/FPS))
                stopTime = System.currentTimeMillis();
            
            info = String.format("Size(%d x %d) | Scale(%.2f x %.2f) | FPS:%d | Pak:%d (KiB:%d)", widthImgRec,
                    heightImgRec, xScale, yScale, (int) 1000 / (stopTime - startTime), lengthImage, lengthImage / 1024);
            txtText.setText(info);
            txtDataTcp.setText(duneGps);
        }     
    }
    
    //Close TCP COM
    private void closeTcpCom() {
        try {
            is.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        try {
            in.close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        out.close();
        try {
            clientSocket.close();
        }
        catch (IOException e1) {
            e1.printStackTrace();
        }
    }
    
    //Create Socket service
    private boolean tcpConnection() {
        //Socket Config    
        NeptusLog.pub().info("Waiting for connection from RasPiCam...");
        try { 
            clientSocket = new Socket(ipHost, portNumber);
            if(clientSocket.isConnected());
                tcpOK=true;
        } 
        catch (IOException e) { 
            //NeptusLog.pub().error("Accept failed...");
            try {
                TimeUnit.MILLISECONDS.sleep(1000);
            }
            catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            tcpOK = false; 
        }
        if(tcpOK){
            NeptusLog.pub().info("Connection successful from Server: " + clientSocket.getInetAddress() + ":"
                    + clientSocket.getLocalPort());
            NeptusLog.pub().info("Receiving data image from RasPiCam...");
                
            //Send data for sync 
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }

            //Buffer for data image
            try{
                is = clientSocket.getInputStream();
            }
            catch (IOException e1) {
                e1.printStackTrace();
            }
            //Buffer for info of data image
            in = new BufferedReader( new InputStreamReader( is ));

            return true;
        }
        else {
            return false;
        }
    }

    //Zoom in
    private void getCutImage(BufferedImage imageToCut, int w, int h) {
        zoomImgCut = new BufferedImage (100, 100, BufferedImage.TYPE_3BYTE_BGR);
        for( int i = -50; i < 50; i++ ) {
            for( int j = -50; j < 50; j++ )
                zoomImgCut.setRGB(i + 50, j + 50, imageToCut.getRGB( w+i, h+j));
        }

        // Create new (blank) image of required (scaled) size
        scaledCutImage = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
        // Paint scaled version of image to new image
        graphics2D = scaledCutImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.drawImage(zoomImgCut, 0, 0, 300, 300, null);
        // clean up
        graphics2D.dispose();
        //draw image
        zoomLabel.setIcon(new ImageIcon(scaledCutImage));
        zoomImg.revalidate();
        zoomImg.add(zoomLabel);
    }    
}
