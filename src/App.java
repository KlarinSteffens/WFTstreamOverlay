import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import java.awt.*;
import java.awt.RenderingHints.Key;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONTokener;
import java.awt.event.*;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import java.io.*;

import javax.sound.sampled.*;


public class App extends WebSocketClient {
//////////////////////////////////////////////Variables\\\\\\\\\\\\\\\\\\\\\\\
    //websocketConnection
    //private String password;
    private String challenge;
    private String salt; 
    public int requestID = 0;
    private boolean isAuthenticated = false;
    //config
    public static String serverUri;
    public static String ip;
    public static String port;
    public static String password;
    public static String goalSongPath;
    ///////////////Scores&Names&timer
    public static int ScoreTeamA = 0;
    public static int ScoreTeamB = 0;
    private int remainingTime = 360;  // Default: 10 minutes
    private int initialTime = 360;    // Default initial time (10 minutes)
    private Timer timer;
    private boolean isTimerRunning = false;
    public static boolean isPaused = false; // Flag to track if the timer is paused
    public static String NameTeamA = "TeamA";
    public static String NameTeamB = "TeamB";
    //////////////OBS_Scenes&Sources
    public String replayPath = "";
    public String currentScene = "";
    public String previousScene = "";
    public Boolean changeSceneBack = false;
    public Boolean songPlaying = false;
    public static Boolean isUpdatingText = true;
    public static Boolean wasMakeActiveButton = false;
    public static JSONArray matchesArray;
    public static JSONObject teamArray;
    public static JSONObject teamVolumeArray;
    public static JSONObject configVariables;
    public static String jsonFilePath;
    
    public static List<JSONObject> matches = new ArrayList<>();
    public static int currentMatchIndex = 0;
    public static String MatchTitle = "";

//////////////////////////////////////////////Websocket initializing\\\\\\

    public App(URI serverUri, String password) {
        super(serverUri);
        App.password = password;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connected to OBS WebSocket");
    }

    @Override
    public void onMessage(String message) {
        JSONObject json = new JSONObject(message);

        if (json.getInt("op") != 5) {
            System.out.println("Received message: " + message);
        }

        if (json.has("op") && json.getInt("op") == 5 && json.getJSONObject("d").has("eventType") && json.getJSONObject("d").getJSONObject("eventData").has("sceneName")) {
            if (json.getJSONObject("d").getString("eventType").equals("CurrentProgramSceneChanged") && !json.getJSONObject("d").getJSONObject("eventData").getString("sceneName").equals("ReplayScene")) {
                changeSceneBack = false;
            }
        }
        if (json.has("op") && json.getInt("op") == 0) {
            JSONObject authData = json.getJSONObject("d").getJSONObject("authentication");
            this.challenge = authData.getString("challenge");
            this.salt = authData.getString("salt");

            sendAuthenticationRequest();
        }

        if (json.has("op") && json.getInt("op") == 2) {
            handleAuthenticationResponse(json);
        }
//////////////////////////////////////////////handle custom websocket recieving\\\\\\
        if (json.has("d")){
            JSONObject jsonD = json.getJSONObject("d");
            if (jsonD.has("eventData")) {
                JSONObject eventData = jsonD.getJSONObject("eventData");
                if (eventData.has("savedReplayPath")) {
                    replayPath = eventData.getString("savedReplayPath");
                    setReplayScene();
                }
            }
            else if(jsonD.has("requestType")){
                if (jsonD.getString("requestType").equals("GetCurrentProgramScene")) {
                    if (jsonD.has("responseData")){
                        currentScene = jsonD.getJSONObject("responseData").getString("currentProgramSceneName");
                    }
                }
            }
            if (jsonD.has("eventType")) {
                if(jsonD.getString("eventType").equals("MediaInputPlaybackEnded")){
                    if(changeSceneBack == true){
                        setCurrentScene(currentScene);
                        changeSceneBack = false;
                    }
                }
            }
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected from OBS WebSocket: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    private void sendAuthenticationRequest() {
        if (password != null && challenge != null && salt != null) {
            String authResponse = getAuthHash(password, challenge, salt);

            JSONObject authRequest = new JSONObject();
            authRequest.put("op", 1);
            authRequest.put("d", new JSONObject()
                    .put("rpcVersion", 1)
                    .put("authentication", authResponse)
            );

            send(authRequest.toString());
        }
    }

    private String getAuthHash(String password, String challenge, String salt) {
        try {
            String secret = Base64.getEncoder().encodeToString(sha256(password + salt));
            String authResponse = Base64.getEncoder().encodeToString(sha256(secret + challenge));
            return authResponse;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private byte[] sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(base.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void handleAuthenticationResponse(JSONObject json) {
        if (json.getJSONObject("d").has("error")) {
            System.out.println("Authentication failed: " + json.getJSONObject("d").getString("error"));
            close();
        } else {
            System.out.println("Authenticated successfully!");
            isAuthenticated = true;
        }
    }

//////////////////////////////////////////////send Websocketrequests\\\\\\
    public void setTextInputContent(String inputName, String newTextContent) {
        requestID++;
        JSONObject request = new JSONObject();
        JSONObject requestContent = new JSONObject();
        requestContent.put("requestType", "SetInputSettings");

        JSONObject inputSettings = new JSONObject();
        inputSettings.put("text", newTextContent);

        JSONObject requestData = new JSONObject();
        requestData.put("inputName", inputName);
        requestContent.put("requestId", requestID);
        requestData.put("inputSettings", inputSettings);
        requestData.put("overlay", true);

        requestContent.put("requestData", requestData);
        request.put("d", requestContent);
        request.put("op", 6);

        send(request.toString());
    }

    public void SaveReplayBuffer() {
        if (isAuthenticated) {
            requestID++;
            JSONObject saveBufferRequest = new JSONObject();
            saveBufferRequest.put("op",6);
            JSONObject saveBufferRequestContent = new JSONObject();
            saveBufferRequestContent.put("requestType", "SaveReplayBuffer");
            saveBufferRequestContent.put("requestId", requestID);
            saveBufferRequest.put("d", saveBufferRequestContent);
            send(saveBufferRequest.toString());
            System.out.println(saveBufferRequest.toString());
        } else {
            System.out.println("WebSocket is not authenticated yet.");
        }
    }
    public void setReplayScene(){
        if(isAuthenticated){
            requestID++;
            getCurrentScene();
            setCurrentScene("ReplayScene");
            JSONObject setReplaySceneRequest = new JSONObject();
            setReplaySceneRequest.put("op", 6);
            JSONObject setReplaySceneRequestContent = new JSONObject();
            JSONObject setReplaySceneRequestData = new JSONObject();
            setReplaySceneRequestData.put("inputName", "InstantReplay");
            setReplaySceneRequestContent.put("requestType", "SetInputSettings");
            setReplaySceneRequestContent.put("requestId", requestID);

            JSONObject inputSettings = new JSONObject();
            inputSettings.put("local_file", replayPath);

            setReplaySceneRequestData.put("inputSettings", inputSettings);
            setReplaySceneRequestContent.put("requestData", setReplaySceneRequestData);
            setReplaySceneRequest.put("d", setReplaySceneRequestContent);

            send(setReplaySceneRequest.toString());
            System.out.println(setReplaySceneRequest.toString());
            changeSceneBack = true;
        }
        else {
            System.out.println("WebSocket is not authenticated yet.");
        }
    }
    public void getCurrentScene(){
        if(isAuthenticated){
            requestID++;
            JSONObject getCurrentScene = new JSONObject();
            getCurrentScene.put("op", 6);
            JSONObject getCurrentSceneContent = new JSONObject();
            getCurrentSceneContent.put("requestType", "GetCurrentProgramScene");
            getCurrentSceneContent.put("requestId", requestID);
            getCurrentScene.put("d", getCurrentSceneContent);

            send(getCurrentScene.toString());
        }
    }
    public void setCurrentScene(String SceneName){
        requestID++;
        JSONObject setCurrentScene = new JSONObject();
        setCurrentScene.put("op", 6);
        JSONObject setCurrentSceneContent = new JSONObject();
        setCurrentSceneContent.put("requestType", "SetCurrentProgramScene");
        setCurrentSceneContent.put("requestId", requestID);
        JSONObject setCurrentSceneData = new JSONObject();
        setCurrentSceneData.put("sceneName", SceneName);
        setCurrentSceneContent.put("requestData", setCurrentSceneData);
        setCurrentScene.put("d", setCurrentSceneContent);

        send(setCurrentScene.toString());
        System.out.println(setCurrentScene.toString());
    }
    public void playGoalSong(String scoreGainedTeam, JSlider volumeSlider, JSlider mainSlider, JProgressBar vuMeter) {
        if (songPlaying) return;

        try {
            Robot robot = new Robot();
            robot.keyPress(KeyEvent.VK_PAUSE);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_PAUSE);
        } catch (AWTException e) {
            e.printStackTrace();
        }

        new Thread(() -> {
            songPlaying = true;
            SourceDataLine line = null;
            ChangeListener volumeUpdater = null;

            try {
                FileInputStream fis = new FileInputStream("Torsongs/" + scoreGainedTeam + ".mp3");
                Bitstream bitstream = new Bitstream(fis);
                Decoder decoder = new Decoder();
                Header header;

                while ((header = bitstream.readFrame()) != null) {
                    SampleBuffer output = (SampleBuffer) decoder.decodeFrame(header, bitstream);

                    if (line == null) {
                        AudioFormat format = new AudioFormat(output.getSampleFrequency(), 16, output.getChannelCount(), true, false);
                        line = AudioSystem.getSourceDataLine(format);
                        line.open(format);
                        line.start();

                        if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                            FloatControl volumeControl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                            volumeUpdater = e -> {
                                float songVol = volumeSlider.getValue() / 100f;
                                float mainVol = mainSlider.getValue() / 100f;
                                float combinedFactor = songVol * mainVol;
                                float dB = (float) (20.0 * Math.log10(combinedFactor > 0.0001 ? combinedFactor : 0.0001));
                                
                                if (dB < volumeControl.getMinimum()) dB = volumeControl.getMinimum();
                                if (dB > volumeControl.getMaximum()) dB = volumeControl.getMaximum();
                                volumeControl.setValue(dB);
                            };
                            volumeUpdater.stateChanged(null);
                            volumeSlider.addChangeListener(volumeUpdater);
                            mainSlider.addChangeListener(volumeUpdater);
                        }
                    }

                    short[] samples = output.getBuffer();
                    byte[] buffer = new byte[output.getBufferLength() * 2];
                    float maxAmplitude = 0;

                    // SINGLE LOOP: Convert to bytes AND calculate VU level at the same time
                    for (int i = 0; i < output.getBufferLength(); i++) {
                        short sample = samples[i];
                        
                        buffer[i * 2] = (byte) (sample & 0xff);
                        buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);

                        float absValue = Math.abs(sample) / 32768f;
                        if (absValue > maxAmplitude) maxAmplitude = absValue;
                    }

                    // Update UI
                    float finalLevel = maxAmplitude;
                    SwingUtilities.invokeLater(() -> {
                        int value = (int) (finalLevel * 100);
                        vuMeter.setValue(value);
                        if (value < 75) vuMeter.setForeground(Color.GREEN);
                        else if (value < 90) vuMeter.setForeground(Color.YELLOW);
                        else vuMeter.setForeground(Color.RED);
                    });

                    // SINGLE WRITE: This sends the data to the speakers once
                    line.write(buffer, 0, buffer.length);
                    
                    bitstream.closeFrame();
                }
                fis.close();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (volumeUpdater != null) {
                    volumeSlider.removeChangeListener(volumeUpdater);
                    mainSlider.removeChangeListener(volumeUpdater);
                }
                if (line != null) {
                    line.drain();
                    line.close();
                }
                songPlaying = false;
                SwingUtilities.invokeLater(() -> vuMeter.setValue(0));
            }
        }).start();
    }
    public void updateMatchLabels(JLabel NameTeamAlabel, JLabel NameTeamBLabel, JLabel ScoreAlabel, JLabel ScoreBlabel, JLabel NameTeamALabelMusic, JLabel NameTeamBLabelMusic, JPanel goalSongTeamAExists, JPanel goalSongTeamBExists, JSlider teamASlider, JSlider teamBSlider) {
        if (currentMatchIndex < matches.size()) {
            JSONObject nextMatch = matches.get(currentMatchIndex);
            NameTeamA = nextMatch.getString("home");
            NameTeamB = nextMatch.getString("away");
            MatchTitle = nextMatch.getString("title");

            File goalSongTeamA = new File("Torsongs/" + NameTeamA + ".mp3");
            File goalSongTeamB = new File("Torsongs/" + NameTeamB + ".mp3");
            if(goalSongTeamA.exists()){
                goalSongTeamAExists.setBackground(Color.green);
            }   
            else{
                goalSongTeamAExists.setBackground(Color.white);
            }
            if(goalSongTeamB.exists()){
                goalSongTeamBExists.setBackground(Color.green);
            }
            else{
                goalSongTeamBExists.setBackground(Color.white);
            }
            NameTeamAlabel.setText(NameTeamA);
            NameTeamALabelMusic.setText(NameTeamA);
            NameTeamBLabel.setText(NameTeamB);
            NameTeamBLabelMusic.setText(NameTeamB);
            ScoreAlabel.setText(String.valueOf(0));
            ScoreBlabel.setText(String.valueOf(0));
            for (int i = 0; i < teamArray.length(); i++) {
                    if(teamArray.getString("team" + i).equals(NameTeamA)){
                        teamASlider.setValue(teamVolumeArray.getInt("teamVolume" + i));
                    }
            }
            for (int i = 0; i < teamArray.length(); i++) {
                    if(teamArray.getString("team" + i).equals(NameTeamB)){
                        teamBSlider.setValue(teamVolumeArray.getInt("teamVolume" + i));
                    }
            }
            currentMatchIndex++;
        } else {
            NameTeamAlabel.setText("No more matches");
            NameTeamBLabel.setText("");
        }
    }

    public static void main(String[] args) throws IOException{
//////////////////////////////////////////////Config Paramters\\\\\\\\\\\\        
        readConfigData();
        JDialog config = new JDialog((Frame) null, "Configuration", true);
        config.setSize(400, 300);
        config.setLayout(new GridLayout(6, 2, 5, 5));
        config.setLocationRelativeTo(null);

        JLabel ipAddressLabel = new JLabel("IP-Addresse des OBS-Ziels");
        JLabel portLabel = new JLabel("Port des OBS-Websocketservers");
        JLabel passwordLabel = new JLabel("OBS-Websocketserver Passwort");
        JLabel matchesPathLabel = new JLabel("Name der zu verwendenden matches.json");
        JTextField ipAddressInput = new JTextField(ip, 50);
        JTextField portInput = new JTextField(port, 50);
        JTextField passwordInput = new JTextField(password, 50);
        JTextField matchesPathInput = new JTextField(jsonFilePath.replace("src/",""), 50);
        JButton saveLoadButton = new JButton("save current values & load config");

        config.add(ipAddressLabel);
        config.add(ipAddressInput);
        config.add(portLabel);
        config.add(portInput);
        config.add(passwordLabel);
        config.add(passwordInput);
        config.add(matchesPathLabel);
        config.add(matchesPathInput);
        config.add(saveLoadButton);

        saveLoadButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent a){
                writeConfigData(ipAddressInput.getText(), portInput.getText(), passwordInput.getText(), matchesPathInput.getText());
                try {
                    readConfigData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    readConfigData();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                config.dispose();
            }
        });
        config.setVisible(true);

//////////////////////////////////////////////Actual App\\\\\\\\\\\\

        try {
            App client = new App(new URI(serverUri), password);
            client.connectBlocking();

            // Load matches from JSON
            client.loadJSONFile();
            client.loadTeamArray();

            JFrame frame = new JFrame("WFT_OBS_Manager");
            frame.setSize(1130, 780);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(null); 
            frame.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    saveJSONData();
                }
            });

            JLabel NameTeamALabel = new JLabel(NameTeamA, SwingConstants.CENTER);
            JLabel NameTeamBLabel = new JLabel(NameTeamB, SwingConstants.CENTER);
            JLabel ScoreALabel = new JLabel(String.valueOf(ScoreTeamA), SwingConstants.CENTER);
            JLabel ScoreBLabel = new JLabel(String.valueOf(ScoreTeamB), SwingConstants.CENTER);
            JLabel timerLabel = new JLabel("00:00", SwingConstants.CENTER);  // Timer starts at 10:00
            JButton increaseScoreAbutton = new JButton("+");
            JButton decreaseScoreAbutton = new JButton("-");
            JButton increaseScoreBbutton = new JButton("+");
            JButton decreaseScoreBbutton = new JButton("-");
            JButton triggerReplayButton = new JButton("Replay");
            JButton nextGameButton = new JButton("Next Game");
            JTextField minutesInput = new JTextField("6", 2);
            minutesInput.setColumns(20);
            JTextField secondsInput = new JTextField("0", 2);
            secondsInput.setColumns(20);
            JTextField homeField = new JTextField(20);
            homeField.setColumns(20);
            JTextField awayField = new JTextField(20);
            awayField.setColumns(20);
            JPopupMenu teamSuggestions = new JPopupMenu();
            JButton saveButton = new JButton("Save");
            JComboBox<String> matchDropdown = new JComboBox<>();
            //JButton sub15 = new JButton("-15");
            JButton sub5 = new JButton("-5");
            JButton startButton = new JButton("Start");
            JButton pauseButton = new JButton("Pause");
            JButton resetButton = new JButton("Reset");
            JButton add5 = new JButton("+5");
            //JButton add15 = new JButton("+15");

            JLabel matchIndexLabel[] = new JLabel[matchesArray.length()];
            JLabel matchTitelLabel[] = new JLabel[matchesArray.length()];
            JLabel teamALabelArray[] = new JLabel[matchesArray.length()];
            JLabel teamBLabelArray[] = new JLabel[matchesArray.length()];
            JLabel scoreTeamALabelArray[] = new JLabel[matchesArray.length()];
            JLabel scoreTeamBLabelArray[] = new JLabel[matchesArray.length()];
            JButton activeButton[] = new JButton[matchesArray.length()];

            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject match = matchesArray.getJSONObject(i);
                matchDropdown.addItem(match.getInt("id") + ": " + match.getString("title"));
            }
            for(int i = 0; i < matchesArray.length(); i++){
                final int ButtonIndex = i;
                matchIndexLabel[i] = new JLabel();
                matchTitelLabel[i] = new JLabel();
                teamALabelArray[i] = new JLabel();
                scoreTeamALabelArray[i] = new JLabel();
                scoreTeamBLabelArray[i] = new JLabel();
                teamBLabelArray[i] = new JLabel();
                activeButton[i] = new JButton("activate");
                activeButton[i].addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e){
                            if(currentMatchIndex>0){
                                saveGameResult();
                                activeButton[currentMatchIndex-1].setBackground(Color.white);
                                activeButton[currentMatchIndex-1].setText("activate");
                            }
                            try {
                                loadMatchesToList(matchIndexLabel, matchTitelLabel, teamALabelArray, scoreTeamALabelArray, scoreTeamBLabelArray , teamBLabelArray, currentMatchIndex-1);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                            wasMakeActiveButton = true;
                            currentMatchIndex = ButtonIndex;
                            nextGameButton.doClick();
                    }
                });

            }
            JLabel NameTeamALabelMusic = new JLabel(NameTeamA, SwingConstants.CENTER);
            JLabel NameTeamBLabelMusic = new JLabel(NameTeamB, SwingConstants.CENTER);
            JPanel songTeamAExistsPanel = new JPanel();
            JPanel songTeamBExistsPanel = new JPanel();
            JSlider goalSongTeamAFader = new JSlider(JSlider.HORIZONTAL, 0, 100, 100);
            JSlider goalSongTeamBFader = new JSlider(JSlider.HORIZONTAL, 0, 100, 100);
            JSlider goalSongMainFader = new JSlider(JSlider.HORIZONTAL, 0, 150, 100);
            JProgressBar vuMeter = new JProgressBar(0, 100);


            increaseScoreAbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ScoreTeamA += 1;
                    ScoreALabel.setText(String.valueOf(ScoreTeamA));
                    if (client.isAuthenticated) {
                        client.setTextInputContent("ScoreTeamA", String.valueOf(ScoreTeamA));
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                    client.playGoalSong(NameTeamA, goalSongTeamAFader, goalSongMainFader, vuMeter);
                }
            });
            decreaseScoreAbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (ScoreTeamA > 0){
                        ScoreTeamA -= 1;
                    }
                    ScoreALabel.setText(String.valueOf(ScoreTeamA));

                    if (client.isAuthenticated) {
                        client.setTextInputContent("ScoreTeamA", String.valueOf(ScoreTeamA));
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                }
            });
            increaseScoreBbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ScoreTeamB += 1;
                    ScoreBLabel.setText(String.valueOf(ScoreTeamB));

                    if (client.isAuthenticated) {
                        client.setTextInputContent("ScoreTeamB", String.valueOf(ScoreTeamB));
                        client.playGoalSong(NameTeamB, goalSongTeamBFader, goalSongMainFader, vuMeter);
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                }
            });
            decreaseScoreBbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (ScoreTeamB > 0){
                        ScoreTeamB -= 1;
                    }
                    
                    ScoreBLabel.setText(String.valueOf(ScoreTeamB));

                    if (client.isAuthenticated) {
                        client.setTextInputContent("ScoreTeamB", String.valueOf(ScoreTeamB));
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                }
            });

            triggerReplayButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e){
                        if (client.isAuthenticated) {
                        client.SaveReplayBuffer();
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                }
            });

            startButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (!client.isTimerRunning) {
                        try {
                            int minutes = Integer.parseInt(minutesInput.getText());
                            int seconds = Integer.parseInt(secondsInput.getText());
                            client.startTimer(timerLabel, minutes, seconds);
                            isPaused = false; // Reset paused state
                            pauseButton.setText("Pause"); // Set button text to "Pause"
                        } catch (NumberFormatException ex) {
                            System.out.println("Invalid input for minutes or seconds");
                        }
                    }
                }
            });
            
            pauseButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if (client.isTimerRunning) {
                        client.pauseTimer();
                        isPaused = true; // Set paused state
                        pauseButton.setText("Resume"); // Change button text to "Resume"
                    } else if (isPaused) {
                        client.resumeTimer(timerLabel); // Add this method to resume the timer
                        isPaused = false; // Reset paused state
                        pauseButton.setText("Pause"); // Change button text back to "Pause"
                    }
                }
            });
            resetButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    try {
                        int minutes = Integer.parseInt(minutesInput.getText());
                        int seconds = Integer.parseInt(secondsInput.getText());
                        client.resetTimer(timerLabel, minutes, seconds);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid input for minutes or seconds");
                    }
                }
            });
            sub5.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    client.adjustTimer(timerLabel, -5);
                }
            });
            add5.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    client.adjustTimer(timerLabel, 5);
                }
            });
            matchDropdown.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    int selectedIndex = matchDropdown.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        JSONObject selectedMatch = matchesArray.getJSONObject(selectedIndex);
                        homeField.setText(selectedMatch.getString("home"));
                        awayField.setText(selectedMatch.getString("away"));
                    }
                }
            });
            saveButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    int selectedIndex = matchDropdown.getSelectedIndex();
                    if (selectedIndex >= 0) {
                        // Get the selected match
                        JSONObject selectedMatch = matchesArray.getJSONObject(selectedIndex);

                        // Update the match data with new input values
                        selectedMatch.put("away", awayField.getText());
                        selectedMatch.put("home", homeField.getText());
                        // Save updated JSON data back to the file
                        saveJSONData();
                        //JOptionPane.showMessageDialog(frame, "Match updated successfully!");
                        try {
                            loadMatchesToList(matchIndexLabel,matchTitelLabel,teamALabelArray,scoreTeamALabelArray,scoreTeamBLabelArray,teamBLabelArray,selectedIndex);
                        } catch (IOException e1) {
                            e1.printStackTrace();
                        }
                        if(selectedIndex+1 == currentMatchIndex){
                            currentMatchIndex--;
                            nextGameButton.doClick();
                        }
                    }
                }
            });
            nextGameButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    if(currentMatchIndex>0){
                        if(!wasMakeActiveButton){
                            saveGameResult();
                            activeButton[currentMatchIndex-1].setBackground(Color.white);
                            activeButton[currentMatchIndex-1].setText("activate");
                            try {
                                loadMatchesToList(matchIndexLabel,matchTitelLabel,teamALabelArray,scoreTeamALabelArray,scoreTeamBLabelArray,teamBLabelArray,currentMatchIndex-1);
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                        wasMakeActiveButton = false;
                    }
                    activeButton[currentMatchIndex].setBackground(Color.cyan);
                    activeButton[currentMatchIndex].setText("current");
                    client.updateMatchLabels(NameTeamALabel, NameTeamBLabel, ScoreALabel, ScoreBLabel, NameTeamALabelMusic, NameTeamBLabelMusic, songTeamAExistsPanel, songTeamBExistsPanel, goalSongTeamAFader, goalSongTeamBFader);
                    //client.adjustScoreBoardWidth(NameTeamA, NameTeamB);
                    ScoreTeamA = 0;
                    ScoreTeamB = 0;
                    try {
                        int minutes = Integer.parseInt(minutesInput.getText());
                        int seconds = Integer.parseInt(secondsInput.getText());
                        client.resetTimer(timerLabel, minutes, seconds);
                    } catch (NumberFormatException ex) {
                        System.out.println("Invalid input for minutes or seconds");
                    }
                    if (client.isAuthenticated) {
                        client.setTextInputContent("NameTeamA", NameTeamA);
                        client.setTextInputContent("NameTeamB", NameTeamB);
                        client.setTextInputContent("NameTeamAnextup", NameTeamA);
                        client.setTextInputContent("NameTeamBnextup", NameTeamB);
                        client.setTextInputContent("ScoreTeamA", String.valueOf(ScoreTeamA));
                        client.setTextInputContent("ScoreTeamB", String.valueOf(ScoreTeamB));
                        client.setTextInputContent("MatchTitle", MatchTitle );
                        client.setCurrentScene("Cam1Nextup");
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                }
            });
            homeField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    if(isUpdatingText == false){
                        List<String> teamStringArray =  teamArrayToString();
                        showSuggestions(teamSuggestions, homeField, teamStringArray); 
                    }
                }
                public void removeUpdate(DocumentEvent e) { 
                    if(isUpdatingText == false){
                        List<String> teamStringArray =  teamArrayToString();
                        showSuggestions(teamSuggestions, homeField, teamStringArray); 
                    }
                }
                public void changedUpdate(DocumentEvent e) {

                }
            });
            awayField.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { 
                    if(isUpdatingText == false){
                        List<String> teamStringArray =  teamArrayToString();
                        showSuggestions(teamSuggestions, homeField, teamStringArray); 
                    }
                }
                public void removeUpdate(DocumentEvent e) { 
                    if(isUpdatingText == false){
                        List<String> teamStringArray =  teamArrayToString();
                        showSuggestions(teamSuggestions, homeField, teamStringArray); 
                    }
                }
                public void changedUpdate(DocumentEvent e) {

                }
            });
            /*homeField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    teamSuggestions.setVisible(false);
                }
            });
            awayField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    teamSuggestions.setVisible(false);
                }
            });*/
            goalSongTeamAFader.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e){
                    for (int i = 0; i < teamArray.length(); i++) {
                        if(teamArray.getString("team" + i).equals(NameTeamA)){
                            teamVolumeArray.put("teamVolume" + i, goalSongTeamAFader.getValue());
                        }
                    }
                }
            });
            goalSongTeamBFader.addChangeListener(new ChangeListener() {
                public void stateChanged(ChangeEvent e){
                    for (int i = 0; i < teamArray.length(); i++) {
                        if(teamArray.getString("team" + i).equals(NameTeamB)){
                            teamVolumeArray.put("teamVolume" + i, goalSongTeamBFader.getValue());
                        }
                    }
                }
            });
            
            


            JPanel TimerPanel = new JPanel();
            TimerPanel.setLayout(null);
            TimerPanel.add(new JLabel(" Set Minutes:") {{setBounds(10, 10, 100, 30);}});
            TimerPanel.add(minutesInput );
            minutesInput.setBounds(120, 10, 100, 30);
            TimerPanel.add(new JLabel(" Set Seconds:") {{setBounds(10, 50, 100, 30);}});
            TimerPanel.add(secondsInput);
            secondsInput.setBounds(120, 50, 100, 30);  
            TimerPanel.add(timerLabel);
            TimerPanel.add(new JLabel("Timer") {{setBounds(10, 90, 80, 30);}});
            timerLabel.setBounds(125, 90, 80, 30); 
            /*TimerPanel.add(sub15);
            sub15.setBounds(10,130,30,30);*/
            TimerPanel.add(sub5);
            sub5.setBounds(95,90,50,30);
            TimerPanel.add(startButton);
            startButton.setBounds(50, 130, 70, 30);
            TimerPanel.add(pauseButton);
            pauseButton.setBounds(130, 130, 70, 30);
            TimerPanel.add(resetButton);
            resetButton.setBounds(210, 130, 70, 30);
            TimerPanel.add(add5);
            add5.setBounds(185,90,50,30);
            /*TimerPanel.add(add15);
            add15.setBounds(380,130,30,30);*/

            JPanel ScorePanel = new JPanel();
            ScorePanel.setLayout(null);
            ScorePanel.add(increaseScoreAbutton);
            increaseScoreAbutton.setBounds(110, 10, 50,30);
            ScorePanel.add(decreaseScoreAbutton);
            decreaseScoreAbutton.setBounds(110, 90, 50,30);
            ScorePanel.add(increaseScoreBbutton);
            increaseScoreBbutton.setBounds(170, 10, 50, 30);
            ScorePanel.add(decreaseScoreBbutton);
            decreaseScoreBbutton.setBounds(170, 90, 50, 30);
            ScorePanel.add(ScoreALabel);
            ScoreALabel.setBounds(110, 50, 50,30);
            ScorePanel.add(ScoreBLabel);
            ScoreBLabel.setBounds(170, 50, 50, 30);
            ScorePanel.add(NameTeamALabel);
            NameTeamALabel.setBounds(0, 50, 100, 30);
            ScorePanel.add(NameTeamBLabel);
            NameTeamBLabel.setBounds(230, 50, 100, 30);
            ScorePanel.add(nextGameButton);
            nextGameButton.setBounds(170, 130, 100, 30);
            ScorePanel.add(triggerReplayButton);
            triggerReplayButton.setBounds(60,130,100,30);

            JPanel MatchManager = new JPanel();
            MatchManager.setLayout(null);
            MatchManager.add(new JLabel("Select Match:") {{setBounds(10, 10, 100, 30);}});
            MatchManager.add(matchDropdown);
            matchDropdown.setBounds(120, 10, 150, 30);
            MatchManager.add(new JLabel("Home Team:") {{setBounds(10, 50, 100, 30);}});
            MatchManager.add(homeField);
            homeField.setBounds(120, 50, 150, 30);
            MatchManager.add(new JLabel("Away Team:") {{setBounds(10, 90, 100, 30);}});
            MatchManager.add(awayField);
            awayField.setBounds(120, 90, 150, 30);
            MatchManager.add(saveButton);
            saveButton.setBounds(10, 130, 80, 30);
                            
            JPanel MatchList = new JPanel();
            MatchList.setLayout(null);
            MatchList.setPreferredSize(new Dimension(730, 40*matchesArray.length() + 10));
            for(int i = 0; i < matchesArray.length(); i++){
                int y = 30*i + (10*(i+1));
                MatchList.add(matchIndexLabel[i]);
                matchIndexLabel[i].setBounds(10, y,50,30);
                MatchList.add(matchTitelLabel[i]);
                matchTitelLabel[i].setBounds(50, y,100,30);
                MatchList.add(teamALabelArray[i]);
                teamALabelArray[i].setBounds(160, y,150,30);
                MatchList.add(scoreTeamALabelArray[i]);
                scoreTeamALabelArray[i].setBounds(320, y,20,30);
                MatchList.add(scoreTeamBLabelArray[i]);
                scoreTeamBLabelArray[i].setBounds(350, y,20,30);
                MatchList.add(teamBLabelArray[i]);
                teamBLabelArray[i].setBounds(390, y,150,30);
                MatchList.add(activeButton[i]);
                activeButton[i].setBounds(550,y,150,30);
                activeButton[i].setBackground(Color.white);
                loadMatchesToList(matchIndexLabel,matchTitelLabel,teamALabelArray,scoreTeamALabelArray,scoreTeamBLabelArray,teamBLabelArray,i);
            }

            JScrollPane MatchListScroll = new JScrollPane(MatchList, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            MatchListScroll.getVerticalScrollBar().setUnitIncrement(12);

            JPanel MusicControl = new JPanel();
            MusicControl.setLayout(null);

            JPanel FaderBank = new JPanel();
            FaderBank.setLayout(null);
            FaderBank.setPreferredSize(new Dimension(60*teamArray.length() + 10, 170));
            FaderBank.add(NameTeamALabelMusic);
            NameTeamALabelMusic.setBounds(10,10,100,30);
            FaderBank.add(NameTeamBLabelMusic);
            NameTeamBLabelMusic.setBounds(10,50,100,30);
            FaderBank.add(new JLabel("Main", SwingConstants.CENTER){{setBounds(10,90,100,30);}});
            FaderBank.add(goalSongTeamAFader);
            goalSongTeamAFader.setBounds(120,10,550,30);
            goalSongTeamAFader.setBackground(Color.gray);
            goalSongTeamAFader.setMinorTickSpacing(20);
            goalSongTeamAFader.setPaintTicks(true);
            FaderBank.add(goalSongTeamBFader);
            goalSongTeamBFader.setBounds(120,50,550,30);
            goalSongTeamBFader.setBackground(Color.gray);
            goalSongTeamBFader.setMinorTickSpacing(20);
            goalSongTeamBFader.setPaintTicks(true);
            FaderBank.add(goalSongMainFader);
            goalSongMainFader.setBounds(120,90,550,30);
            goalSongMainFader.setBackground(Color.gray);
            goalSongMainFader.setMinorTickSpacing(20);
            goalSongMainFader.setPaintTicks(true);
            FaderBank.add(songTeamAExistsPanel);
            songTeamAExistsPanel.setBounds(680,10,30,30);
            FaderBank.add(songTeamBExistsPanel);
            songTeamBExistsPanel.setBounds(680,50,30,30);
            FaderBank.add(vuMeter);
            vuMeter.setBounds(10,130,710, 30);
            vuMeter.setStringPainted(false);
            //vuMeter.setForeground(java.awt.Color.GREEN);
            vuMeter.setBackground(java.awt.Color.BLACK);
            vuMeter.setBorder(BorderFactory.createLineBorder(java.awt.Color.DARK_GRAY));

            // Add all to the frame
            frame.add(TimerPanel);
            TimerPanel.setBounds(10, 10, 350, 170);
            TimerPanel.setBackground(Color.gray);
            frame.add(ScorePanel);
            ScorePanel.setBounds(10,190,350, 170);
            ScorePanel.setBackground(Color.gray);
            frame.add(MatchManager);
            MatchManager.setBounds(10, 370, 350, 170);
            MatchManager.setBackground(Color.gray);
            frame.add(MatchListScroll);
            MatchListScroll.setBounds(370,10,730,530);
            MatchList.setBackground(Color.gray);
            frame.add(MusicControl);
            MusicControl.setBounds(10,550,350,170);
            MusicControl.setBackground(Color.gray);
            frame.add(FaderBank);
            FaderBank.setBounds(370,550, 730,170);
            FaderBank.setBackground(Color.gray);
            frame.setVisible(true);
            matchDropdown.setSelectedIndex(0);
            isUpdatingText = false;
        }catch (URISyntaxException | InterruptedException | IOException ex) {
            ex.printStackTrace();
        }
        
    }
    public static void readConfigData() throws IOException{
        try (InputStream is = new FileInputStream("src/config.json")) {
            JSONTokener tokener = new JSONTokener(is);
            configVariables = new JSONObject(tokener);
            ip = configVariables.getString("ipaddress");
            port = configVariables.getString("port");
            password = configVariables.getString("password");
            serverUri = "ws://" + ip + ":" + port;
            jsonFilePath = "src/" + configVariables.getString("matchespath");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static void writeConfigData(String ipaddress, String port, String password, String matchesFilePath){
        try (OutputStream os = new FileOutputStream("src/config.json")) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("ipaddress", ipaddress);
            jsonObject.put("port", port);
            jsonObject.put("password", password);
            jsonObject.put("matchespath", matchesFilePath);
            os.write(jsonObject.toString(4).getBytes()); // Write with indentation
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void startTimer(JLabel timerLabel, int minutes, int seconds) {
        if (timer != null) {
            timer.cancel();
        }

        timer = new Timer();
        isTimerRunning = true;

        initialTime = (minutes * 60) + seconds;
        remainingTime = initialTime;

        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (remainingTime > 0) {
                    remainingTime--;
                    int minutes = remainingTime / 60;
                    int seconds = remainingTime % 60;
                    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

                    if (isAuthenticated) {
                        setTextInputContent("Timer", String.format("%02d:%02d", minutes, seconds));
                    }
                } else {
                    timer.cancel();
                    isTimerRunning = false;
                    System.out.println("Timer ended.");
                }
            }
        }, 0, 1000);
    }

    public void pauseTimer() {
        if (timer != null) {
            timer.cancel();
            isTimerRunning = false;
        }
    }

    public void resetTimer(JLabel timerLabel, int minutes, int seconds) {
        pauseTimer();
        initialTime = (minutes * 60) + seconds;
        remainingTime = initialTime;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

        if (isAuthenticated) {
            setTextInputContent("Timer", String.format("%02d:%02d", minutes, seconds));
        }
    }

    public void resumeTimer(JLabel timerLabel) {
        if (isTimerRunning) return; // Prevent resuming if the timer is running

        timer = new Timer();
        isTimerRunning = true;

        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                if (remainingTime > 0) {
                    remainingTime--;
                    int minutes = remainingTime / 60;
                    int seconds = remainingTime % 60;
                    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

                    if (isAuthenticated) {
                        setTextInputContent("Timer", String.format("%02d:%02d", minutes, seconds));
                    }
                } else {
                    timer.cancel();
                    isTimerRunning = false;
                    System.out.println("Timer ended.");
                }
            }
        }, 0, 1000);
    }
    public void adjustTimer(JLabel timerLabel, int secondsToAdjust) {
    remainingTime += secondsToAdjust;

    if (remainingTime < 0) {
        remainingTime = 0;
    }

    int minutes = remainingTime / 60;
    int seconds = remainingTime % 60;

    timerLabel.setText(String.format("%02d:%02d", minutes, seconds));

    if (isAuthenticated) {
        setTextInputContent("Timer", String.format("%02d:%02d", minutes, seconds));
    }
}
    private void loadJSONFile() throws IOException {
        try (InputStream is = new FileInputStream(jsonFilePath)) {
            JSONTokener tokener = new JSONTokener(is);
            JSONObject jsonObject = new JSONObject(tokener);
            matchesArray = jsonObject.getJSONArray("matches");
            if (jsonObject.has("teams")) {
                teamArray = jsonObject.getJSONObject("teams");
            }
            else{
                teamArray = new JSONObject();
            }
            if (jsonObject.has("teamVolume")) {
                teamVolumeArray = jsonObject.getJSONObject("teamVolume");
            }
            else{
                teamVolumeArray = new JSONObject();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        for (int i = 0; i < matchesArray.length(); i++) {
            matches.add(matchesArray.getJSONObject(i));
        }
    }
    private static void loadMatchesToList(JLabel[] matchesIndexLabel, JLabel[] matchesTitelLabel, JLabel[] teamAListLabel, JLabel[] teamAListScoreLabel, JLabel[] teamBListScoreLabel, JLabel[] teamBListLabel, int matchToWriteIndex) throws IOException {
        try {
            JSONObject matchToLoadToList = matches.get(matchToWriteIndex);
            matchesIndexLabel[matchToWriteIndex].setText(String.valueOf(matchToWriteIndex + 1));
            matchesTitelLabel[matchToWriteIndex].setText(matchToLoadToList.getString("title"));
            teamAListLabel[matchToWriteIndex].setText(matchToLoadToList.getString("home"));
            teamAListScoreLabel[matchToWriteIndex].setText(String.valueOf(matchToLoadToList.getInt("scoreHome"))+ "   :");
            teamBListScoreLabel[matchToWriteIndex].setText(String.valueOf(matchToLoadToList.getInt("scoreAway")));
            teamBListLabel[matchToWriteIndex].setText(matchToLoadToList.getString("away"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void loadTeamArray(){
        System.out.println(teamArray.length());
        Boolean gotTeamA = false;
        Boolean gotTeamB = false;
        for(int i = 0; i < matchesArray.length(); i++){
            JSONObject readTeamFrom = matches.get(i);
            if (readTeamFrom.getString("title").contains("Gruppe")) {
                for(int j = 0; j < (teamArray.length()); j++){
                    if (teamArray.getString("team"+j).equals(readTeamFrom.getString("home")) || teamArray.getString("team"+j).equals(readTeamFrom.getString("away"))) {
                        if (teamArray.getString("team"+j).equals(readTeamFrom.getString("home"))) {
                            gotTeamA = true;
                        }
                        if (teamArray.getString("team"+j).equals(readTeamFrom.getString("away"))) {
                            gotTeamB = true;
                        }
                    }
                } 
                if (gotTeamA == false) {
                    teamArray.put("team"+ teamArray.length(),readTeamFrom.getString("home"));
                    teamVolumeArray.put("teamVolume" + teamVolumeArray.length(), 100);
                }
                if (gotTeamB == false) {
                    teamArray.put("team"+teamArray.length(),readTeamFrom.getString("away"));
                    teamVolumeArray.put("teamVolume" + teamVolumeArray.length(), 100);
                }
                gotTeamA = false;
                gotTeamB = false;
            }
        }
    }
    public static List<String> teamArrayToString(){
        List<String> teamStringArray = new ArrayList<>();
        for(int i = 0; i < teamArray.length(); i++){
            teamStringArray.add(teamArray.getString("team" + i));
        }
        return teamStringArray;
    }
    public static void showSuggestions(JPopupMenu teamSuggestions, JTextField parent, List<String> teamStringArray){
        teamSuggestions.removeAll();

        String input = parent.getText();
        if (input.isEmpty()) {
            teamSuggestions.setVisible(false);
            return;
        }
        List<String> matches = teamStringArray.stream().filter(s -> s.contains(input)).collect(Collectors.toList());

        if (matches.isEmpty()) {
            teamSuggestions.setVisible(false);
            return;
        }

    for (String match : matches) {
        JMenuItem item = new JMenuItem(match);
        item.addActionListener(e -> {
            isUpdatingText = true;
            parent.setText(match);
            isUpdatingText = false;
            teamSuggestions.setVisible(false);
        });
        teamSuggestions.add(item);
    }

        teamSuggestions.show(parent, 0, parent.getHeight());
        parent.requestFocus();

    }
    public static void getAverageVolume(){

    }
    public static void saveGameResult(){
        JSONObject currentMatch = matches.get(currentMatchIndex-1);
        currentMatch.put("scoreHome", ScoreTeamA);
        currentMatch.put("scoreAway", ScoreTeamB);
        saveJSONData();
    }
    private static void saveJSONData() {
        try (OutputStream os = new FileOutputStream(jsonFilePath)) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("matches", matchesArray);
            jsonObject.put("teams", teamArray);
            jsonObject.put("teamVolume", teamVolumeArray);
            os.write(jsonObject.toString(4).getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
