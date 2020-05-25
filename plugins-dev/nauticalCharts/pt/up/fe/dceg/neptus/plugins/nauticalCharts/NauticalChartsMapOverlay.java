package pt.up.fe.dceg.neptus.plugins.nauticalCharts;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import pt.lsts.neptus.console.ConsoleLayout;
import pt.lsts.neptus.plugins.PluginDescription;
import pt.lsts.neptus.plugins.SimpleRendererInteraction;
import pt.lsts.neptus.renderer2d.StateRenderer2D;

/**
 * @author nikolai
 *
 */
@PluginDescription(name = "Nautical Charts plugin",
description = "")
public class NauticalChartsMapOverlay extends SimpleRendererInteraction {

    /**
     * @param console
     */
    public NauticalChartsMapOverlay(ConsoleLayout console) {
        super(console);
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.renderer2d.StateRendererInteraction#isExclusive()
     */
    private static final long serialVersionUID = 1L;
    protected String text = "inactive";
    @Override
    public boolean isExclusive() {
        // TODO Auto-generated method stub
        return true;
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#cleanSubPanel()
     */
    @Override
    public void cleanSubPanel() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.console.ConsolePanel#initSubPanel()
     */
    @Override
    public void initSubPanel() {
        // TODO Auto-generated method stub

    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.plugins.SimpleRendererInteraction#setActive(boolean, pt.lsts.neptus.renderer2d.StateRenderer2D)
     */
    @Override
    public void setActive(boolean mode, StateRenderer2D source) {
        // TODO Auto-generated method stub
        super.setActive(mode, source);
        if(mode)
            text = "active";
        else
            text = "inactive";
    }

    /* (non-Javadoc)
     * @see pt.lsts.neptus.plugins.SimpleRendererInteraction#paint(java.awt.Graphics2D, pt.lsts.neptus.renderer2d.StateRenderer2D)
     */
    @Override
    public void paint(Graphics2D g, StateRenderer2D renderer) {
        // TODO Auto-generated method stub
        super.paint(g, renderer);
        g.drawString(text, 10, 10);
    }
    
}
