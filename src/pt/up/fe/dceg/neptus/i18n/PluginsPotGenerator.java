/*
 * Copyright (c) 2004-2013 Universidade do Porto - Faculdade de Engenharia
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
 * Author: José Pinto
 * Sep 11, 2012
 */
package pt.up.fe.dceg.neptus.i18n;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Vector;

import pt.up.fe.dceg.neptus.console.events.ConsoleEventVehicleStateChanged;
import pt.up.fe.dceg.neptus.console.events.ConsoleEventVehicleStateChanged.STATE;
import pt.up.fe.dceg.neptus.console.plugins.SystemsList;
import pt.up.fe.dceg.neptus.gui.MissionBrowser;
import pt.up.fe.dceg.neptus.gui.PropertiesEditor;
import pt.up.fe.dceg.neptus.gui.system.SystemDisplayComparator;
import pt.up.fe.dceg.neptus.imc.Announce;
import pt.up.fe.dceg.neptus.imc.Goto;
import pt.up.fe.dceg.neptus.imc.Loiter;
import pt.up.fe.dceg.neptus.mp.ManeuverLocation.Z_UNITS;
import pt.up.fe.dceg.neptus.plugins.NeptusProperty;
import pt.up.fe.dceg.neptus.plugins.PluginDescription;
import pt.up.fe.dceg.neptus.plugins.acoustic.LBLRangeDisplay.HideOrFadeRangeEnum;
import pt.up.fe.dceg.neptus.plugins.map.MapEditor;
import pt.up.fe.dceg.neptus.plugins.planning.MapPanel;
import pt.up.fe.dceg.neptus.plugins.web.NeptusServlet;
import pt.up.fe.dceg.neptus.types.map.AbstractElement;
import pt.up.fe.dceg.neptus.types.vehicle.VehicleType.SystemTypeEnum;
import pt.up.fe.dceg.neptus.types.vehicle.VehicleType.VehicleTypeEnum;
import pt.up.fe.dceg.neptus.util.FileUtil;
import pt.up.fe.dceg.neptus.util.comm.manager.imc.ImcSystem;

import com.l2fprod.common.propertysheet.DefaultProperty;

/**
 * @author zp
 * 
 */
public class PluginsPotGenerator {

    protected static final String outFile = I18n.I18N_BASE_LOCALIZATION + "/neptus.pot";
    protected static final String inFile = "dev-scripts/i18n/empty.pot";

    public static Vector<AbstractElement> mapElements() {
        try {
            return new MapEditor(null).getElements();
        }
        catch (Exception e) {
            e.printStackTrace();
            return new Vector<>();
        }
    }

    // add here more enumerations that are used in the GUI
    public static Vector<Class<?>> enums() {
        Vector<Class<?>> enums = new Vector<>();
        enums.add(Announce.SYS_TYPE.class);
        //enums.add(ConsoleEventVehicleStateChanged.STATE.class);
        enums.add(ImcSystem.IMCAuthorityState.class);
        enums.add(Goto.SPEED_UNITS.class);
        enums.add(Loiter.TYPE.class);
        enums.add(MapPanel.PlacementEnum.class);
        //enums.add(STATE.class);
        enums.add(SystemDisplayComparator.OrderOptionEnum.class);
        enums.add(SystemsList.SortOrderEnum.class);
        enums.add(SystemsList.MilStd2525SymbolsFilledEnum.class);
        enums.add(SystemTypeEnum.class);
        enums.add(VehicleTypeEnum.class);
        enums.add(Z_UNITS.class);
        enums.add(HideOrFadeRangeEnum.class);
        enums.add(ConsoleEventVehicleStateChanged.STATE.class);
        return enums;
    }

    public static List<String> customStrings() {
        List<String> strs = new ArrayList<>();
        strs.add("Normal");
        strs.add("Map");
        strs.add("Infinite Rectangle");
        strs.add("Rows Plan");
        return strs;
    }

    public static void listClasses(Vector<Class<?>> classes, String packageName, File dir) {
        for (File f : dir.listFiles()) {
            if (f.isDirectory()) {
                if (packageName.isEmpty())
                    listClasses(classes, f.getName(), f);
                else
                    listClasses(classes, packageName + "." + f.getName(), f);
            }
            else if (FileUtil.getFileExtension(f).equals("java")) {
                String className = (packageName.isEmpty() ? "" : packageName + ".")
                        + f.getName().substring(0, f.getName().indexOf("."));
                try {
                    classes.add(Class.forName(className, false, Thread.currentThread().getContextClassLoader()));
                }
                catch (Exception e) {
                    // e.printStackTrace();
                }
                catch (Error e) {
                    // e.printStackTrace();
                }
                // System.err.println(className);
            }
        }
    }

    public static Vector<Class<?>> getAllClasses() {
        Vector<Class<?>> classes = new Vector<Class<?>>();
        listClasses(classes, "", new File("src"));

        File pluginsDir = new File("plugins-dev");
        for (File f : pluginsDir.listFiles())
            listClasses(classes, "", f);
        return classes;
    }

    public static LinkedHashMap<String, DefaultProperty> getProperties(Class<?> clazz) {
        LinkedHashMap<String, DefaultProperty> props = new LinkedHashMap<String, DefaultProperty>();

        for (Field f : clazz.getFields()) {

            NeptusProperty a = f.getAnnotation(NeptusProperty.class);

            if (a != null) {
                String name = a.name();
                String desc = a.description();
                String category = a.category();
                if (a.name().length() == 0) {
                    name = f.getName();
                }
                if (a.description().length() == 0) {
                    desc = name;
                }
                if (category == null || category.length() == 0) {
                    category = "Base";
                }
                props.put(name, PropertiesEditor.getPropertyInstance(name, category, String.class, "", true, desc));
            }
        }
        return props;
    }

    public static String escapeQuotes(String text) {
        text = text.replaceAll("\"", "'");
        text = text.replaceAll("\n", "\\\\n");
        // text = text.replaceAll("\\", "\\\\");
        return text;
    }

    public static void main(String[] args) throws Exception {

        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(outFile)));

        BufferedReader reader = new BufferedReader(new FileReader(new File(inFile)));

        String line;
        while ((line = reader.readLine()) != null)
            writer.write(line + "\n");

        reader.close();

        Vector<Class<?>> classes = getAllClasses();

        for (Class<?> c : classes) {
            try {
                PluginDescription pd = c.getAnnotation(PluginDescription.class);
                if (!pd.name().isEmpty()) {
                    writer.write("#: Name of plugin " + c.getName() + "\n");
                    writer.write("msgid \"" + escapeQuotes(pd.name()) + "\"\n");
                    writer.write("msgstr \"\"\n\n");
                }
                if (!pd.description().isEmpty()) {
                    writer.write("#: Description of plugin " + c.getName() + "\n");
                    writer.write("msgid \"" + escapeQuotes(pd.description()) + "\"\n");
                    writer.write("msgstr \"\"\n\n");
                }
            }
            catch (Exception e) {
                // e.printStackTrace();
            }

            try {
                NeptusServlet pd = c.getAnnotation(NeptusServlet.class);
                if (!pd.name().isEmpty()) {
                    writer.write("#: Name of servlet " + c.getName() + "\n");
                    writer.write("msgid \"" + escapeQuotes(pd.name()) + "\"\n");
                    writer.write("msgstr \"\"\n\n");
                }
                if (!pd.description().isEmpty()) {
                    writer.write("#: Description of servlet " + c.getName() + "\n");
                    writer.write("msgid \"" + escapeQuotes(pd.description()) + "\"\n");
                    writer.write("msgstr \"\"\n\n");
                }
            }
            catch (Exception e) {
                // e.printStackTrace();
            }

            try {
                LinkedHashMap<String, DefaultProperty> props = getProperties(c);

                for (DefaultProperty dp : props.values()) {
                    writer.write("#: Property from " + c.getName() + "\n");
                    writer.write("msgid \"" + escapeQuotes(dp.getName()) + "\"\n");
                    writer.write("msgstr \"\"\n\n");

                    if (!dp.getShortDescription().equals(dp.getName())) {
                        writer.write("#: Description of property '" + dp.getDisplayName() + "' from " + c.getName()
                                + "\n");
                        writer.write("msgid \"" + escapeQuotes(dp.getShortDescription()) + "\"\n");
                        writer.write("msgstr \"\"\n\n");
                    }
                }
            }
            catch (Exception e) {
                // e.printStackTrace();
            }
            catch (Error e) {
                // TODO: handle exception
            }
        }

        for (AbstractElement elem : mapElements()) {
            writer.write("#: Map element type (class " + elem.getClass().getSimpleName() + ")\n");
            writer.write("msgid \"" + elem.getType() + "\"\n");
            writer.write("msgstr \"\"\n\n");
        }

        for (Class<?> enumClass : enums()) {
            for (Object o : enumClass.getEnumConstants()) {
                String name = enumClass.getSimpleName();
                if (enumClass.getEnclosingClass() != null) {
                    name = enumClass.getEnclosingClass().getSimpleName() + "." + name;
                }
                writer.write("#: Field from " + name + " enumeration\n");
                writer.write("msgid \"" + o + "\"\n");
                writer.write("msgstr \"\"\n\n");
            }
        }
        for (String string : customStrings()) {
            writer.write("#: Custom String \n");
            writer.write("msgid \"" + string + "\"\n");
            writer.write("msgstr \"\"\n\n");
        }

        writer.close();
    }

}
