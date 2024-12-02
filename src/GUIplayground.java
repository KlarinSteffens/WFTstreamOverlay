import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GUIplayground extends JFrame{

    public static int ScoreTeamA = 0;
    public static int ScoreTeamB = 0;
    public static void main(String[] args) {
        String NameTeamA = "TeamA";
        String NameTeamB = "TeamB";

        ImageIcon frameIcon = new ImageIcon("pulli.png");

        JFrame frame = new JFrame("WFT_OBS_Manager");
        frame.setSize(400, 200);  // Set the window size
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(frameIcon.getImage());
        frame.setLayout(new GridLayout(5,1));
        
        // Create a label and a button
        JLabel NameTeamAlabel = new JLabel(NameTeamA + " ");
        JLabel NameTeamBLabel = new JLabel(" " + NameTeamB);
        JLabel ScoreAlabel = new JLabel(String.valueOf(ScoreTeamA + ":"));
        JLabel ScoreBlabel = new JLabel(String.valueOf(ScoreTeamB));
        JButton increaseScoreAbutton = new JButton("+");
        JButton decreaseScoreAbutton = new JButton("-");
        JButton increaseScoreBbutton = new JButton("+");
        JButton decreaseScoreBbutton = new JButton("-");

        
        increaseScoreAbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ScoreTeamA += 1;
                ScoreAlabel.setText(String.valueOf(ScoreTeamA));
            }
        });
        decreaseScoreAbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (ScoreTeamA != 0){
                    ScoreTeamA -= 1;
                    ScoreAlabel.setText(String.valueOf(ScoreTeamA));
                }
            }
        });
        increaseScoreBbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                ScoreTeamB += 1;
                ScoreBlabel.setText(String.valueOf(ScoreTeamB));
            }
        });
        decreaseScoreBbutton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (ScoreTeamB != 0){
                    ScoreTeamB -= 1;
                    ScoreBlabel.setText(String.valueOf(ScoreTeamB));
                }
            }
        });

         JPanel PanelScoreA = new JPanel();
         PanelScoreA.setLayout(new GridLayout(3,1));
         PanelScoreA.add(increaseScoreAbutton);
         PanelScoreA.add(ScoreAlabel);
         PanelScoreA.add(decreaseScoreAbutton);

         JPanel PanelScoreB = new JPanel();
         PanelScoreB.setLayout(new GridLayout(3,1));
         PanelScoreB.add(increaseScoreBbutton);
         PanelScoreB.add(ScoreBlabel);
         PanelScoreB.add(decreaseScoreBbutton);

         JPanel ScorePanel = new JPanel();
         ScorePanel.setLayout(new GridBagLayout());
         //ScorePanel.add(Box.createHorizontalGlue());
         ScorePanel.add(NameTeamAlabel);
         ScorePanel.add(PanelScoreA);
         ScorePanel.add(PanelScoreB);
         ScorePanel.add(NameTeamBLabel);
         

         frame.add(ScorePanel);
         frame.setBackground(Color.blue);
         

         frame.setVisible(true);
    }
}