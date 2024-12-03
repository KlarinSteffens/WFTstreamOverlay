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

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import org.json.JSONTokener;
import java.awt.event.*;
import java.io.*;

public class App extends WebSocketClient {

//////////////////////////////////////////////Variablen\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
    //websocketConnection
    private String password;
    private String challenge;
    private String salt; 
    public int requestID = 0;
    private boolean isAuthenticated = false;
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
    public String wasCurrentScene = "";
    public static JSONArray matchesArray;
    public static String jsonFilePath;
    
    private List<JSONObject> matches = new ArrayList<>();
    private int currentMatchIndex = 0;
    public static String MatchTitle = "";

//////////////////////////////////////////////Websocket intizialisieren\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\

    public App(URI serverUri, String password) {
        super(serverUri);
        this.password = password;
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

        if (json.has("op") && json.getInt("op") == 0) {
            JSONObject authData = json.getJSONObject("d").getJSONObject("authentication");
            this.challenge = authData.getString("challenge");
            this.salt = authData.getString("salt");

            sendAuthenticationRequest();
        }

        if (json.has("op") && json.getInt("op") == 2) {
            handleAuthenticationResponse(json);
        }
//////////////////////////////////////////////handle custom websocket recieving\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
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
                        wasCurrentScene = jsonD.getJSONObject("responseData").getString("currentProgramSceneName");
                    }
                }
            }
            if (jsonD.has("eventType")) {
                if(jsonD.getString("eventType").equals("MediaInputPlaybackEnded")){
                    System.out.println(wasCurrentScene);
                    setCurrentScene(wasCurrentScene);
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

//////////////////////////////////////////////send Websocketrequests\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\
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

    /*public void adjustScoreBoardWidth(String NameTeamA, String NameTeamB){
       
        double longestTeamName = 0;

        if (NameTeamA.length() > NameTeamB.length()) {
            longestTeamName = NameTeamA.length();
        }
        else{
            longestTeamName = NameTeamB.length();
        }

        double newXscale = 1 + ((longestTeamName - 10) * 0.008);

       
        requestID++;
        JSONObject adjustScoreBoardWidth = new JSONObject();
        JSONObject adjustScoreBoardWidthContent = new JSONObject();
        adjustScoreBoardWidthContent.put("requestType", "SetSceneItemTransform");
        adjustScoreBoardWidthContent.put("requestId", requestID);

        JSONObject adjustScoreBoardWidthData = new JSONObject();
        adjustScoreBoardWidthData.put("sceneName", "Overlay");
        adjustScoreBoardWidthData.put("sceneItemId", 16);

        JSONObject adjustScoreBoardWidthTransform = new JSONObject();
        adjustScoreBoardWidthTransform.put("scaleX", newXscale);
        adjustScoreBoardWidthData.put("sceneItemTransform", adjustScoreBoardWidthTransform);

        adjustScoreBoardWidthContent.put("requestData", adjustScoreBoardWidthData);
        adjustScoreBoardWidth.put("d", adjustScoreBoardWidthContent);
        adjustScoreBoardWidth.put("op", 6);

        System.out.println(adjustScoreBoardWidth);

        send(adjustScoreBoardWidth.toString());

        client.adjustScoreBoardWidth(20, newXscale);
        client.adjustScoreBoardWidth(20, newXscale);
    }

    public void adjustTeamNamePosition(int item, double newXscale){
        requestID++;
        JSONObject adjustTeamNamePosition = new JSONObject();
        JSONObject adjustTeamNamePositionContent = new JSONObject();
        adjustTeamNamePositionContent.put("requestType", "SetSceneItemTransform");
        adjustTeamNamePositionContent.put("requestId", requestID);

        JSONObject adjustTeamNamePositionData = new JSONObject();
        adjustTeamNamePositionData.put("sceneName", "Overlay");
        adjustTeamNamePositionData.put("sceneItemId", item);

        double newPositionX = (((850 * newXscale) - 180));

        JSONObject adjustTeamNamePositionTransform = new JSONObject();
        adjustTeamNamePositionTransform.put("positionX", newPositionX);
        adjustTeamNamePositionData.put("sceneItemTransform", adjustTeamNamePositionTransform);

        adjustTeamNamePositionContent.put("requestData", adjustTeamNamePositionData);
        adjustTeamNamePosition.put("d", adjustTeamNamePositionContent);
        adjustTeamNamePosition.put("op", 6);

        System.out.println(adjustTeamNamePosition);

        send(adjustTeamNamePosition.toString());
    }*/
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

    public void activateGoalSong(String scoreGainedTeam){
        requestID++;
        JSONObject activateGoalSong = new JSONObject();
        activateGoalSong.put("op", 6);
        JSONObject activateGoalSongContent = new JSONObject();
        activateGoalSongContent.put("requestType", "TriggerMediaInputAction");
        activateGoalSongContent.put("requestId", requestID);
        JSONObject activateGoalSongData = new JSONObject();
        activateGoalSongData.put("inputName", scoreGainedTeam);
        activateGoalSongData.put("mediaAction", "OBS_WEBSOCKET_MEDIA_INPUT_ACTION_RESTART");
        activateGoalSongContent.put("requestData", activateGoalSongData);
        activateGoalSong.put("d", activateGoalSongContent);

        send(activateGoalSong.toString());
        System.out.println(activateGoalSong.toString());
    }
    

    // Method to load matches from JSON file
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

    public static void main(String[] args) {
        //String serverUri = "ws://192.168.25.167:4444";
        //String password = "JrXIqKD6qIeV5PNL";
        //String serverUri = "ws://192.168.2.4:4455";
        //String password = "WLJR7M2dFyfTK6DV";
        String serverUri = "ws://localhost:4444";
        String password = "JrXIqKD6qIeV5PNL";
        
        try {
            // Use JFileChooser to prompt the user for the JSON file
            JFileChooser fileChooser = new JFileChooser();
            int result = fileChooser.showOpenDialog(null);
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                jsonFilePath = selectedFile.getAbsolutePath();  // Get the full path of the selected file

                App client = new App(new URI(serverUri), password);
                client.connectBlocking();

                // Load matches from JSON
                client.loadMatches();

                JFrame frame = new JFrame("WFT_OBS_Manager");
                frame.setSize(1920, 1080);
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setLayout(new BorderLayout()); // Changed layout for better control

                JLabel NameTeamAlabel = new JLabel(NameTeamA);
                JLabel NameTeamBLabel = new JLabel(NameTeamB);
                JLabel ScoreAlabel = new JLabel(String.valueOf(ScoreTeamA));
                JLabel ScoreBlabel = new JLabel(String.valueOf(ScoreTeamB));
                JLabel timerLabel = new JLabel("00:00");  // Timer starts at 10:00
                JButton increaseScoreAbutton = new JButton("+");
                JButton decreaseScoreAbutton = new JButton("-");
                JButton increaseScoreBbutton = new JButton("+");
                JButton decreaseScoreBbutton = new JButton("-");
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
                JButton startButton = new JButton("Start");
                JButton pauseButton = new JButton("Pause");
                JButton resetButton = new JButton("Reset");
                for (int i = 0; i < matchesArray.length(); i++) {
                    JSONObject match = matchesArray.getJSONObject(i);
                    matchDropdown.addItem(match.getInt("id") + ": " + match.getString("title"));
                }

                increaseScoreAbutton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        ScoreTeamA += 1;
                        ScoreAlabel.setText(String.valueOf(ScoreTeamA));
    
                        if (client.isAuthenticated) {
                            client.setTextInputContent("ScoreTeamA", String.valueOf(ScoreTeamA));
                            client.SaveReplayBuffer();
                            client.activateGoalSong(NameTeamA);
                        } else {
                            System.out.println("WebSocket is not authenticated yet.");
                        }
                    }
                });
                decreaseScoreAbutton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        if (ScoreTeamA > 0){
                            ScoreTeamA -= 1;
                        }
                        ScoreAlabel.setText(String.valueOf(ScoreTeamA));
    
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
                        ScoreBlabel.setText(String.valueOf(ScoreTeamB));
    
                        if (client.isAuthenticated) {
                            client.setTextInputContent("ScoreTeamB", String.valueOf(ScoreTeamB));
                            client.SaveReplayBuffer();
                            client.activateGoalSong(NameTeamB);
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
                        
                        ScoreBlabel.setText(String.valueOf(ScoreTeamB));
    
                        if (client.isAuthenticated) {
                            client.setTextInputContent("ScoreTeamB", String.valueOf(ScoreTeamB));
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
                saveButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        int selectedIndex = matchDropdown.getSelectedIndex();
                        if (selectedIndex >= 0) {
                            // Get the selected match
                            JSONObject selectedMatch = matchesArray.getJSONObject(selectedIndex);
        
                            // Update the match data with new input values
                            selectedMatch.put("home", homeField.getText());
                            selectedMatch.put("away", awayField.getText());
        
                            // Save updated JSON data back to the file
                            saveJSONData();
                            JOptionPane.showMessageDialog(frame, "Match updated successfully!");
                        }
                    }
                });

                // Next Game button to load next match
                JButton nextGameButton = new JButton("Next Game");
                nextGameButton.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        // Update team labels with the next match
                        client.updateMatchLabels(NameTeamAlabel, NameTeamBLabel, ScoreAlabel, ScoreBlabel);
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

                JPanel PanelScoreA = new JPanel();
                PanelScoreA.setLayout(new GridBagLayout());
                GridBagConstraints gbcA = new GridBagConstraints();
                gbcA.gridx = 0;
                gbcA.gridy = 0;
                gbcA.anchor = GridBagConstraints.CENTER;
                PanelScoreA.add(increaseScoreAbutton, gbcA); 
                gbcA.gridy = 1; 
                PanelScoreA.add(ScoreAlabel, gbcA);
                gbcA.gridy = 2; 
                PanelScoreA.add(decreaseScoreAbutton, gbcA);
                
    
                JPanel PanelScoreB = new JPanel();
                PanelScoreB.setLayout(new GridBagLayout());
                GridBagConstraints gbcB = new GridBagConstraints();
                gbcB.gridx = 0;
                gbcB.gridy = 0;
                gbcB.anchor = GridBagConstraints.CENTER;
                PanelScoreB.add(increaseScoreBbutton, gbcB); 
                gbcB.gridy = 1; 
                PanelScoreB.add(ScoreBlabel, gbcB);
                gbcB.gridy = 2;
                PanelScoreB.add(decreaseScoreBbutton, gbcB);
    
                JPanel ScorePanel = new JPanel();
                ScorePanel.setLayout(new GridBagLayout());
                ScorePanel.add(NameTeamAlabel);
                ScorePanel.add(PanelScoreA);
                ScorePanel.add(PanelScoreB);
                ScorePanel.add(NameTeamBLabel);
                ScorePanel.add(nextGameButton);
    
                JPanel TimerPanel = new JPanel();
                TimerPanel.setLayout(new GridBagLayout());
                TimerPanel.add(new JLabel("  Set Minutes:"));
                TimerPanel.add(minutesInput);
                TimerPanel.add(new JLabel("  Set Seconds:"));
                TimerPanel.add(secondsInput);
                TimerPanel.add(new JLabel("  Timer:")); 
                TimerPanel.add(timerLabel);
                TimerPanel.add(startButton);
                TimerPanel.add(pauseButton);
                TimerPanel.add(resetButton);

                JPanel MatchManager = new JPanel();
                MatchManager.setLayout(new GridBagLayout());
                MatchManager.add(new JLabel("Select Match:"));
                MatchManager.add(matchDropdown);
                MatchManager.add(new JLabel("Home Team:"));
                MatchManager.add(homeField);
                MatchManager.add(new JLabel("Away Team:"));
                MatchManager.add(awayField);
                MatchManager.add(saveButton);

                // Add all to the frame
                frame.add(ScorePanel, BorderLayout.NORTH);
                frame.add(TimerPanel, BorderLayout.CENTER);
                frame.add(MatchManager, BorderLayout.SOUTH);
                frame.setVisible(true);

            } else {
                System.out.println("No file selected");
            }
        } catch (URISyntaxException | InterruptedException | IOException ex) {
            ex.printStackTrace();
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
