import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
//import java.nio.file.Path;
//import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONTokener;
import java.awt.event.*;
import java.io.*;

import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;
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
    public static JSONArray matchesArray;
    public static JSONObject configVariables;
    public static String jsonFilePath;
    
    public List<JSONObject> matches = new ArrayList<>();
    public static int currentMatchIndex = 0;
    public static String MatchTitle = "";

//////////////////////////////////////////////Websocket initializing\\\\\\\\\\\\\\\\\\\\\\\

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
            System.out.println("has eventype and scenename");
            if (json.getJSONObject("d").getString("eventType").equals("CurrentProgramSceneChanged") && !json.getJSONObject("d").getJSONObject("eventData").getString("sceneName").equals("ReplayScene")) {
                changeSceneBack = false;
                System.out.println(changeSceneBack);
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
//////////////////////////////////////////////handle custom websocket recieving\\\\\\\\\\\\\\\\\\\\\\\
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

//////////////////////////////////////////////send Websocketrequests\\\\\\\\\\\\\\\\\\\\\\\
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
        //System.out.println(request.toString());
        //System.out.println("Sent request to update text input: " + inputName + " to new content: " + newTextContent);
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
            System.out.println(changeSceneBack);
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
    public void playGoalSong(String scoreGainedTeam) throws JavaLayerException, FileNotFoundException{
        if (songPlaying) return;

        new Thread(() -> {
            songPlaying = true;
            try (FileInputStream fis = new FileInputStream("Torsongs/" + scoreGainedTeam + ".mp3")) {
                Player player = new Player(fis);
                player.play();
            } catch (JavaLayerException| IOException e ) {
                e.printStackTrace();
            }finally {
                songPlaying = false;
            }
        }).start();
    }
    private void loadMatches() throws IOException {
        try (InputStream is = new FileInputStream(jsonFilePath)) {
            JSONTokener tokener = new JSONTokener(is);
            JSONObject jsonObject = new JSONObject(tokener);
            matchesArray = jsonObject.getJSONArray("matches");
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        for (int i = 0; i < matchesArray.length(); i++) {
            matches.add(matchesArray.getJSONObject(i));
        }
    }

    public void updateMatchLabels(JLabel NameTeamAlabel, JLabel NameTeamBLabel, JLabel ScoreAlabel, JLabel ScoreBlabel) {
        if (currentMatchIndex < matches.size()) {
            JSONObject currentMatch = matches.get(currentMatchIndex);
            NameTeamA = currentMatch.getString("home");
            NameTeamB = currentMatch.getString("away");
            MatchTitle = currentMatch.getString("title");


            NameTeamAlabel.setText(NameTeamA);
            NameTeamBLabel.setText(NameTeamB);
            ScoreAlabel.setText(String.valueOf(ScoreTeamA));
            ScoreBlabel.setText(String.valueOf(ScoreTeamB));

            currentMatchIndex++;
        } else {
            NameTeamAlabel.setText("No more matches");
            NameTeamBLabel.setText("");
        }
    }

    public static void main(String[] args) throws IOException{
//////////////////////////////////////////////Config Paramters\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\        
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

//////////////////////////////////////////////Actual App\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

        try {
            App client = new App(new URI(serverUri), password);
            client.connectBlocking();

            // Load matches from JSON
            client.loadMatches();

            JFrame frame = new JFrame("WFT_OBS_Manager");
            frame.setSize(370, 590);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setLayout(null); 

            JLabel NameTeamALabel = new JLabel(NameTeamA, SwingConstants.CENTER);
            JLabel NameTeamBLabel = new JLabel(NameTeamB, SwingConstants.CENTER);
            JLabel ScoreALabel = new JLabel(String.valueOf(ScoreTeamA), SwingConstants.CENTER);
            JLabel ScoreBLabel = new JLabel(String.valueOf(ScoreTeamB), SwingConstants.CENTER);
            JLabel timerLabel = new JLabel("00:00");  // Timer starts at 10:00
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
            JButton saveButton = new JButton("Save");
            JComboBox<String> matchDropdown = new JComboBox<>();
            //JButton sub15 = new JButton("-15");
            JButton sub5 = new JButton("-5");
            JButton startButton = new JButton("Start");
            JButton pauseButton = new JButton("Pause");
            JButton resetButton = new JButton("Reset");
            JButton add5 = new JButton("+5");
            //JButton add15 = new JButton("+15");
            for (int i = 0; i < matchesArray.length(); i++) {
                JSONObject match = matchesArray.getJSONObject(i);
                matchDropdown.addItem(match.getInt("id") + ": " + match.getString("title"));
            }

            increaseScoreAbutton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    ScoreTeamA += 1;
                    ScoreALabel.setText(String.valueOf(ScoreTeamA));
                    if (client.isAuthenticated) {
                        client.setTextInputContent("ScoreTeamA", String.valueOf(ScoreTeamA));
                    } else {
                        System.out.println("WebSocket is not authenticated yet.");
                    }
                    try {
                        client.playGoalSong(NameTeamA);
                    } catch (FileNotFoundException | JavaLayerException e1) {
                        e1.printStackTrace();
                    }
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
                        try {
                            client.playGoalSong(NameTeamB);
                        } catch (FileNotFoundException | JavaLayerException e1) {
                            e1.printStackTrace();
                        }
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
                        JOptionPane.showMessageDialog(frame, "Match updated successfully!");
                        if(selectedIndex+1 == currentMatchIndex){
                            currentMatchIndex--;
                            nextGameButton.doClick();
                        }
                    }
                }
            });
            nextGameButton.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    client.updateMatchLabels(NameTeamALabel, NameTeamBLabel, ScoreALabel, ScoreBLabel);
                    System.out.println(currentMatchIndex);
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
            timerLabel.setBounds(120, 90, 80, 30); 
            /*TimerPanel.add(sub15);
            sub15.setBounds(10,130,30,30);*/
            TimerPanel.add(sub5);
            sub5.setBounds(10,130,30,30);
            TimerPanel.add(startButton);
            startButton.setBounds(50, 130, 70, 30);
            TimerPanel.add(pauseButton);
            pauseButton.setBounds(130, 130, 70, 30);
            TimerPanel.add(resetButton);
            resetButton.setBounds(210, 130, 70, 30);
            TimerPanel.add(add5);
            add5.setBounds(290,130,30,30);
            /*TimerPanel.add(add15);
            add15.setBounds(380,130,30,30);*/

            JPanel ScorePanel = new JPanel();
            ScorePanel.setLayout(null);
            ScorePanel.add(increaseScoreAbutton);
            increaseScoreAbutton.setBounds(120, 10, 50,30);
            ScorePanel.add(ScoreALabel);
            ScoreALabel.setBounds(120, 50, 50,30);
            ScorePanel.add(decreaseScoreAbutton);
            decreaseScoreAbutton.setBounds(120, 90, 50,30);
            ScorePanel.add(increaseScoreBbutton);
            increaseScoreBbutton.setBounds(180, 10, 50, 30);
            ScorePanel.add(ScoreBLabel);
            ScoreBLabel.setBounds(180, 50, 50, 30);
            ScorePanel.add(decreaseScoreBbutton);
            decreaseScoreBbutton.setBounds(180, 90, 50, 30);
            ScorePanel.add(NameTeamALabel);
            NameTeamALabel.setBounds(10, 50, 100, 30);
            ScorePanel.add(NameTeamBLabel);
            NameTeamBLabel.setBounds(220, 50, 100, 30);
            ScorePanel.add(nextGameButton);
            nextGameButton.setBounds(180, 130, 100, 30);
            ScorePanel.add(triggerReplayButton);
            triggerReplayButton.setBounds(70,130,100,30);

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

            // Add all to the frame
            frame.add(TimerPanel);
            TimerPanel.setBounds(10, 10, 330, 170);
            TimerPanel.setBackground(Color.gray);
            frame.add(ScorePanel);
            ScorePanel.setBounds(10,190,330, 170);
            ScorePanel.setBackground(Color.gray);
            frame.add(MatchManager);
            MatchManager.setBounds(10, 370, 330, 170);
            MatchManager.setBackground(Color.gray);
            frame.setVisible(true);
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
    private static void saveJSONData() {
        try (OutputStream os = new FileOutputStream(jsonFilePath)) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("matches", matchesArray);
            os.write(jsonObject.toString(4).getBytes()); // Write with indentation
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
