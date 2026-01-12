import java.awt.*;
import javax.swing.*;
import javax.swing.UIDefaults;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.metal.MetalLookAndFeel;


public class WFTstyleLaF extends MetalLookAndFeel {

    @Override
    public String getName() {
        return "WFT Modern Greyscale";
    }

    @Override
    public String getDescription() {
        return "Modern greyscale Look & Feel for OBS control software";
    }

    @Override
    protected void initComponentDefaults(UIDefaults table) {
        super.initComponentDefaults(table);

        table.put("Button.background", new Color(60, 60, 60));
        table.put("Button.foreground", new Color(220, 220, 220));
        //table.put("Button.border", new RoundedBorder(12));
        table.put("Button.focus", new Color(0, 0, 0, 0));

        table.put("Panel.background", new Color(30, 30, 30));
        table.put("Label.foreground", new Color(220, 220, 220));
        table.put("Slider.foreground", new Color(200, 200, 200));
        table.put("Slider.background", new Color(30, 30, 30));

        table.put("TextField.background", new Color(45, 45, 45));
        table.put("TextField.foreground", new Color(220, 220, 220));
        //table.put("TextField.border", new RoundedBorder(10));

        table.put("ScrollBar.thumb", new Color(90, 90, 90));
    }
}

